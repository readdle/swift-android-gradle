package com.readdle.android.swift.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec

import java.nio.file.Files
import java.nio.file.Path

class SwiftAndroidPlugin implements Plugin<Project> {
    private ToolchainHandle toolchainHandle
    private Task installToolsTask

    @Override
    void apply(Project project) {
        toolchainHandle = new ToolchainHandle(project)

        def extension = project.extensions.create('swift', SwiftAndroidPluginExtension, project)

        configurePropertiesTask(project)

        project.afterEvaluate {
            installToolsTask = createInstallSwiftToolsTask(project)

            createSwiftUpdateTask(project)

            Task swiftClean = createCleanTask(project, extension.usePackageClean)
            if (extension.cleanEnabled) {
                Task cleanTask = project.tasks.getByName("clean")
                cleanTask.dependsOn(swiftClean)
            }

            project.android.applicationVariants.all { variant ->
                handleVariant(project, variant)
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

    private void handleVariant(Project project, def variant) {
        boolean isDebug = variant.buildType.isDebuggable()

        Task swiftInstall = createSwiftInstallTask(project, variant)
        swiftInstall.dependsOn(installToolsTask)

        Task swiftLinkGenerated = createLinkGeneratedSourcesTask(project, variant)

        SwiftAndroidPluginExtension extension = project.extensions.getByType(SwiftAndroidPluginExtension)
        Set<String> abiFilters = isDebug ? extension.debug.abiFilters : extension.release.abiFilters

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

    private Task createSwiftTaskChain(Project project, def variant, Arch arch, Task swiftLinkGenerated) {
        Task swiftBuild = createSwiftBuildTask(project, variant, arch)
        swiftBuild.dependsOn(installToolsTask, swiftLinkGenerated)

        return createCopyTask(project, variant, arch, swiftBuild)
    }

    private static void mountSwiftToAndroidPipeline(Project project, def variant, Task copySwift) {
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
    private Task createInstallSwiftToolsTask(Project project) {
        def version = toolchainHandle.TOOLS_VERSION

        return project.task(type: Exec, "installSwiftTools") {
            executable toolchainHandle.toolsManagerPath
            args "tools", "--install", version

            doFirst {
                checkToolchain()
                println "Installing Swift Build Tools v${version}"
            }

            doLast {
                println "Swift Build Tools v${version} installed"
            }
        }
    }

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

    // TODO: integrate with android gradle pipeline
    private static Task createSwiftUpdateTask(Project project) {
        return project.task(type: Exec, "swiftPackageUpdate") {
            workingDir "src/main/swift"
            commandLine "swift", "package", "update"
        }
    }

    private Task createSwiftInstallTask(Project project, def variant) {
        boolean isDebug = variant.buildType.isDebuggable()
        def variantName = variant.name.capitalize()

        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        def configurationArgs = ["--configuration", isDebug ? "debug" : "release"]
        def extraArgs = isDebug ? extension.debug.extraInstallFlags : extension.release.extraInstallFlags

        return project.task(type: Exec, "swiftInstall${variantName}") {
            workingDir "src/main/swift"
            executable toolchainHandle.swiftInstallPath
            args configurationArgs + extraArgs
            environment toolchainHandle.swiftEnv
        }
    }

    private Task createSwiftBuildTask(Project project, def variant, Arch arch) {
        boolean isDebug = variant.buildType.isDebuggable()
        def taskQualifier = taskQualifier(variant, arch)

        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        def configurationArgs = ["--configuration", isDebug ? "debug" : "release"]
        def extraArgs = isDebug ? extension.debug.extraBuildFlags : extension.release.extraBuildFlags
        def arguments = configurationArgs + extraArgs

        return project.task(type: Exec, "swiftBuild${taskQualifier}") {
            workingDir "src/main/swift"
            executable toolchainHandle.swiftBuildPath
            args arguments
            environment toolchainHandle.getFullEnv(arch)

            doFirst {
                checkNdk()

                def args = arguments.join(" ")
                println("Swift PM flags: ${args}")
            }
        }
    }

    private Task createCopyTask(Project project, def variant, Arch arch, Task swiftBuildTask) {
        def taskQualifier = taskQualifier(variant, arch)

        boolean isDebug = variant.buildType.isDebuggable()
        String swiftPmBuildPath = isDebug
                ? "src/main/swift/.build/${arch.swiftTriple}/debug"
                : "src/main/swift/.build/${arch.swiftTriple}/release"

        def outputLibraries = project.fileTree(swiftPmBuildPath) {
            include "*.so"
        }

        return project.task(type: Copy, "copySwift${taskQualifier}") {
            dependsOn(swiftBuildTask)

            from("src/main/swift/.build/jniLibs/${arch.androidAbi}") {
                include "*.so"
            }
            from(toolchainHandle.getSwiftLibFolder(arch)) {
                include "*.so"
            }
            from(outputLibraries)

            into "src/main/jniLibs/${arch.androidAbi}"
            
            fileMode 0644
        }
    }

    private static String taskQualifier(def variant, Arch arch) {
        String archComponent = arch.variantName.capitalize()
        String variantComponent = variant.name.capitalize()

        return archComponent + variantComponent
    }

    private static Task createLinkGeneratedSourcesTask(Project project, def variant) {
        def variantName = variant.name.capitalize()
        def variantDir = variant.dirName

        def target = generatedSourcesPath(project, variantDir)

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

    private static Path generatedSourcesPath(Project project, variantDir) {
        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        if (extension.useKapt) {
            return new File(project.buildDir, "generated/source/kapt/${variantDir}/SwiftGenerated").toPath()
        } else {
            return new File(project.buildDir, "generated/ap_generated_sources/${variantDir}/out/SwiftGenerated").toPath()
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