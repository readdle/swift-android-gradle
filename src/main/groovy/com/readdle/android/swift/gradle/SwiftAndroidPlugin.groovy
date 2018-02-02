package com.readdle.android.swift.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec

import java.nio.file.Files

class SwiftAndroidPlugin implements Plugin<Project> {
    ToolchainHandle toolchainHandle

    @Override
    void apply(Project project) {
        toolchainHandle = new ToolchainHandle(project)

        def extension = project.extensions.create('swift', SwiftAndroidPluginExtension, project)

        project.afterEvaluate {
            Task installTools = createInstallSwiftToolsTask(project)

            createSwiftUpdateTask(project)

            Task swiftClean = createCleanTask(project, extension.usePackageClean)
            if (extension.cleanEnabled) {
                Task cleanTask = project.tasks.getByName("clean")
                cleanTask.dependsOn(swiftClean)
            }

            project.android.applicationVariants.all { variant ->
                handleVariant(project, variant, installTools)
            }
        }
    }

    private void handleVariant(Project project, def variant, Task installTools) {
        boolean isDebug = variant.buildType.isDebuggable()

        Task swiftInstall = createSwiftInstallTask(project, variant, isDebug)

        // Swift build chain
        Task swiftLinkGenerated = createLinkGeneratedSourcesTask(project, variant)
        Task swiftBuild = createSwiftBuildTask(project, variant, isDebug)
        Task copySwift = createCopyTask(project, variant, isDebug)

        swiftBuild.dependsOn(swiftLinkGenerated)
        copySwift.dependsOn(swiftBuild)

        // Tasks using build tools
        swiftInstall.dependsOn(installTools)
        swiftBuild.dependsOn(installTools)
        copySwift.dependsOn(installTools)

        def variantName = variant.name.capitalize()

        Task compileDebugNdk = project.tasks.getByName("compile${variantName}Ndk")
        compileDebugNdk.dependsOn(copySwift)
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
            dependsOn(usePackageClean ? forceClean : packageClean)
        }
    }

    // TODO: integrate with android gradle pipeline
    private static Task createSwiftUpdateTask(Project project) {
        return project.task(type: Exec, "swiftPackageUpdate") {
            workingDir "src/main/swift"
            commandLine "swift", "package", "update"
        }
    }

    private Task createSwiftInstallTask(Project project, def variant, boolean debug) {
        def variantName = variant.name.capitalize()

        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        def configurationArgs = ["--configuration", debug ? "debug" : "release"]
        def extraArgs = debug ? extension.debug.extraInstallFlags : extension.release.extraInstallFlags

        return project.task(type: Exec, "swiftInstall${variantName}") {
            workingDir "src/main/swift"
            executable toolchainHandle.swiftInstallPath
            args configurationArgs + extraArgs
            environment toolchainHandle.swiftEnv
        }
    }

    private Task createSwiftBuildTask(Project project, def variant, boolean debug) {
        def variantName = variant.name.capitalize()

        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        def configurationArgs = ["--configuration", debug ? "debug" : "release"]
        def extraArgs = debug ? extension.debug.extraBuildFlags : extension.release.extraBuildFlags

        return project.task(type: Exec, "swiftBuild${variantName}") {
            workingDir "src/main/swift"
            executable toolchainHandle.swiftBuildPath
            args configurationArgs + extraArgs
            environment toolchainHandle.fullEnv

            doFirst {
                checkNdk()

                def args = (configurationArgs + extraArgs).join(" ")
                println("Swift PM flags: ${args}")
            }
        }
    }

    private Task createCopyTask(Project project, def variant, boolean debug) {
        def variantName = variant.name.capitalize()

        String swiftPmBuildPath = debug ?
                "src/main/swift/.build/debug" : "src/main/swift/.build/release"

        return project.task(type: Copy, "copySwift${variantName}") {
            from("src/main/swift/.build/jniLibs/armeabi-v7a") {
                include "*.so"
            }
            from(toolchainHandle.swiftLibFolder) {
                include "*.so"
            }
            from(swiftPmBuildPath) {
                include "*.so"
            }

            into "src/main/jniLibs/armeabi-v7a"
            
            fileMode 0644
        }
    }

    private static Task createLinkGeneratedSourcesTask(Project project, def variant) {
        def variantName = variant.name.capitalize()
        def variantDir = variant.dirName

        def target = new File(project.buildDir, "generated/source/apt/${variantDir}/SwiftGenerated").toPath()
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

    @SuppressWarnings("GroovyUnusedDeclaration")
    private static Task addCompatibilityAlias(Project project, Task task, String alias) {
        String originalName = task.name

        return project.task(alias).dependsOn(task).doFirst {
            print "${alias} is deprecated use ${originalName}"
        }
    }
}