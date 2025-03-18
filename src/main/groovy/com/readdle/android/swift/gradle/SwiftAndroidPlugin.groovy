package com.readdle.android.swift.gradle

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.file.DuplicatesStrategy

import java.nio.file.Files
import java.nio.file.Path

class SwiftAndroidPlugin implements Plugin<Project> {
    private ToolchainHandle toolchainHandle

    @Override
    void apply(Project project) {
        toolchainHandle = new ToolchainHandle(project)

        def extension = project.extensions.create('swift', SwiftAndroidPluginExtension, project)

        configurePropertiesTask(project)

        project.afterEvaluate {
            Task swiftClean = createCleanTask(project, extension.usePackageClean)
            if (extension.cleanEnabled) {
                Task cleanTask = project.tasks.getByName("clean")
                cleanTask.dependsOn(swiftClean)
            }

            if(project.android.hasProperty('applicationVariants')) {
                project.android.applicationVariants.all { ApplicationVariant variant ->
                    handleVariant(project, variant)
                }
            }

            if(project.android.hasProperty('libraryVariants')) {
                project.android.libraryVariants.all { LibraryVariant variant ->
                    handleVariant(project, variant)
                }
            }
        }
    }

    private void configurePropertiesTask(Project project) {
        project.task("swiftUpdateLocalProperties") {
            doLast {
                toolchainHandle.updateProperties(project)
            }
        }
    }

    private void handleVariant(Project project, BaseVariant variant) {
        boolean isDebug = variant.buildType.isJniDebuggable()

        Task swiftLinkGenerated = createLinkGeneratedSourcesTask(project, variant)

        SwiftAndroidPluginExtension extension = project.extensions.getByType(SwiftAndroidPluginExtension)
        Set<String> abiFilters = isDebug ? extension.debug.abiFilters : extension.release.abiFilters
        if (abiFilters == null || abiFilters.isEmpty()) {
            abiFilters = variant.buildType.ndk.abiFilters ?: new HashSet<String>()
        }

        Set<Arch> allowedArchitectures = Arch.values()
                .findAll { arch -> return abiFilters.isEmpty() || abiFilters.contains(arch.androidAbi) }
                .toSet()

        for (Arch arch : Arch.values()) {
            Task swiftChain = createSwiftTaskChain(project, variant, arch, swiftLinkGenerated)

            if (allowedArchitectures.contains(arch)) {
                mountSwiftToAndroidPipeline(project, variant, swiftChain)
            }
        }
    }

    private Task createSwiftTaskChain(Project project, BaseVariant variant, Arch arch, Task swiftLinkGenerated) {
        Task swiftBuild = createSwiftBuildTask(project, variant, arch)
        swiftBuild.dependsOn(swiftLinkGenerated)

        return createCopyTask(project, variant, arch, swiftBuild)
    }

    private static void mountSwiftToAndroidPipeline(Project project, BaseVariant variant, Task copySwift) {
        def variantName = variant.name.capitalize()

        Task compileNdk = project.tasks.findByName("compile${variantName}Ndk")
        Task externalNativeBuild = project.tasks.findByName("externalNativeBuild${variantName}")
        Task mergeLibs = project.tasks.findByName("merge${variantName}JniLibFolders")
        Task compileSources = project.tasks.findByName("compile${variantName}Sources")

        if (compileNdk != null) {
            compileNdk.dependsOn(copySwift)
        } else if (externalNativeBuild != null) {
            externalNativeBuild.dependsOn(copySwift)
        } else if (mergeLibs != null) {
            mergeLibs.dependsOn(copySwift)
        } else {
            compileSources.dependsOn(copySwift)
        }
    }

    private void checkToolchain() {
        if (!toolchainHandle.isToolchainPresent()) {
            throw new GradleException(
                    "Swift Toolchain location not found. Define location with swift-android.dir in the " +
                            "local.properties file or with an SWIFT_ANDROID_HOME environment variable."
            )
        }
    }

    private void checkNdk() {
        if (!toolchainHandle.isNdkPresent()) {
            throw new GradleException(
                    "NDK location not found. Define location with ndk.dir in the " +
                            "local.properties file or with an ANDROID_NDK_HOME environment variable."
            )
        }
    }

    // Tasks
    private static Task createCleanTask(Project project, boolean usePackageClean) {
        Task forceClean = project.task(type: Delete, "swiftForceClean") {
            // I don't trust Swift Package Manager's --clean
            delete "src/main/swift/.build"
        }

        Task packageClean = project.task(type: Exec, "swiftPackageClean") {
            workingDir "src/main/swift"
            commandLine "swift", "package", "clean"
        }

        return project.task("swiftClean") {
            dependsOn(usePackageClean ? packageClean : forceClean)
        }
    }

    private Task createSwiftBuildTask(Project project, BaseVariant variant, Arch arch) {
        boolean isDebug = variant.buildType.isJniDebuggable()
        def taskQualifier = taskQualifier(variant, arch)

        def task = project.tasks.findByName("swiftBuild${taskQualifier}")
        if (task != null) {
            return task
        }

        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        def configurationArgs = ["--configuration", isDebug ? "debug" : "release"]
        def extraArgs = isDebug ? extension.debug.extraBuildFlags : extension.release.extraBuildFlags
        def arguments = configurationArgs + extraArgs
        def apiLevel = extension.apiLevel

        return project.task(type: Exec, "swiftBuild${taskQualifier}") {
            workingDir "src/main/swift"
            executable toolchainHandle.swiftBuildPath
            args arguments
            environment toolchainHandle.getFullEnv(arch, apiLevel)

            doFirst {
                checkNdk()

                def args = arguments.join(" ")
                println("Swift PM flags: ${args}")
            }
        }
    }

    private Task createCopyTask(Project project, BaseVariant variant, Arch arch, Task swiftBuildTask) {
        def taskQualifier = taskQualifier(variant, arch)
        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        def task = project.tasks.findByName("copySwift${taskQualifier}")
        if (task != null) {
            return task
        }

        boolean isDebug = variant.buildType.isJniDebuggable()
        String swiftPmBuildPath = isDebug
                ? "src/main/swift/.build/${arch.swiftTriple}${extension.apiLevel}/debug"
                : "src/main/swift/.build/${arch.swiftTriple}${extension.apiLevel}/release"

        def outputLibraries = project.fileTree(swiftPmBuildPath) {
            include "*.so", "*.so.*"
        }

        return project.task(type: Copy, "copySwift${taskQualifier}") {
            dependsOn(swiftBuildTask)

            from("src/main/swift/.build/jniLibs/${arch.androidAbi}") {
                include "*.so", "*.so.*"
            }
            from(toolchainHandle.getSwiftLibFolder(arch)) {
                include "*.so", "*.so.*"
            }
            from(outputLibraries)

            into "src/main/jniLibs/${arch.androidAbi}"

            fileMode = 0644
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }

    private static String taskQualifier(BaseVariant variant, Arch arch) {
        String archComponent = arch.variantName.capitalize()
        String buildTypeComponent = variant.buildType.name.capitalize()
        return archComponent + buildTypeComponent
    }

    private static Task createLinkGeneratedSourcesTask(Project project, BaseVariant variant) {
        def variantName = variant.name.capitalize()

        def target = generatedSourcesPath(project, variant)

        def swiftBuildDir = new File(project.projectDir, "src/main/swift/.build")
        def link = new File(swiftBuildDir, "generated").toPath()

        def annotationProcessorTask = project.tasks.getByName("compile${variantName}JavaWithJavac")

        return project.task("swiftLinkGeneratedSources${variantName}") {
            dependsOn(annotationProcessorTask)

            doLast {
                swiftBuildDir.mkdirs()
                Files.deleteIfExists(link)
                Files.createSymbolicLink(
                        link,
                        link.getParent().relativize(target)
                )
            }
        }
    }

    private static Task createSwiftLintTask(Project project) {
        return project.task(type: Exec, "swiftLint") {
            workingDir "src/main/swift"
            commandLine "swiftlint", "--strict", "--reporter", "xcode"

            ignoreExitValue = true
            errorOutput = new ByteArrayOutputStream()
            standardOutput = new ByteArrayOutputStream()

            doLast {
                def output = standardOutput.toString()
                if (!output.empty) {
                    throw new GradleException(output)
                }
            }
        }
    }

    private static Path generatedSourcesPath(Project project, BaseVariant variant) {
        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        if (extension.useKapt) {
            return new File(project.buildDir, "generated/source/kapt/${variant.name}/SwiftGenerated").toPath()
        } else {
            return new File(project.buildDir, "generated/ap_generated_sources/${variant.name}/out/SwiftGenerated").toPath()
        }
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private static Task addCompatibilityAlias(Project project, Task task, String alias) {
        String originalName = task.name

        return project.task(alias).dependsOn(task).doFirst {
            print "${alias} is deprecated use ${originalName}"
        }
    }
}