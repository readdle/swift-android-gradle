
//
// Injects Swift relevant tasks into gradle build process
// Mostly these are performed by scripts in ~/.gradle/scripts
// installed when the plugin is installed by "create_scripts.sh"
//

package org.swiftjava.gradle.android

import org.gradle.api.*
import org.gradle.api.tasks.*

class SwiftAndroidPlugin implements Plugin<Project> {
    ToolchainHandle toolchainHandle

    @Override
    void apply(Project project) {
        toolchainHandle = new ToolchainHandle(project)

        checkToolchanin()

        def extension = project.extensions.create('swift', SwiftAndroidPluginExtension, project)

        project.afterEvaluate {
            Task installTools = createInstallSwiftToolsTask(project)

            Task generateSwift = createGenerateSwiftTask(project)
            Task swiftClean = createCleanTask(project, extension.usePackageClean)
            createSwiftUpdateTask(project)

            Task swiftBuildChainDebug = createSwiftBuildChain(project, true)
            Task swiftBuildChainRelease = createSwiftBuildChain(project, false)

            Task swiftInstallDebug = createSwiftInstallTask(project, true)
            Task swiftInstallRelease = createSwiftInstallTask(project, false)

            // Tasks using build tools
            swiftBuildChainDebug.dependsOn(installTools)
            swiftBuildChainRelease.dependsOn(installTools)
            swiftInstallDebug.dependsOn(installTools)
            swiftInstallRelease.dependsOn(installTools)


            Task compileDebugNdk = project.tasks.getByName("compileDebugNdk")
            compileDebugNdk.dependsOn(swiftBuildChainDebug)

            Task compileReleaseNdk = project.tasks.getByName("compileReleaseNdk")
            compileReleaseNdk.dependsOn(swiftBuildChainRelease)

            Task preBuildTask = project.tasks.getByName("preBuild")
            preBuildTask.dependsOn(generateSwift)

            if (extension.cleanEnabled) {
                Task cleanTask = project.tasks.getByName("clean")
                cleanTask.dependsOn(swiftClean)
            }
        }
    }

    private void checkToolchanin() {
        if (!toolchainHandle.isToolchainPresent()) {
            throw new GradleException(
                    "Specified Swift Toolchain location does not exists. " +
                            "Please ensure swift-android.dir in local.properties file or " +
                            "SWIFT_ANDROID_HOME is configured correctly."
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
                println "Installing Swift Build Tools v${version}"
            }

            doLast {
                println "Swift Build Tools v${version} installed"
            }
        }
    }

    private Task createSwiftBuildChain(Project project, boolean debug) {
        Task swiftBuild = createSwiftBuildTask(project, debug)
        Task copySwift = createCopyTask(project, debug)

        copySwift.dependsOn(swiftBuild)

        return copySwift
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

        Task metaTask = project.task("swiftClean")

        Task impl = usePackageClean ?
                    forceClean : packageClean

        metaTask.dependsOn(impl)

        return metaTask
    }

    // TODO: integrate with android gradle pipeline
    private static Task createSwiftUpdateTask(Project project) {
        return project.task(type: Exec, "swiftPackageUpdate") {
            workingDir "src/main/swift"
            commandLine "swift", "package", "update"
        }
    }

    private static Task createGenerateSwiftTask(Project project) {
        return project.task("generateSwift")
    }

    private Task createSwiftInstallTask(Project project, boolean debug) {
        String name = debug ? "swiftInstallDebug" :  "swiftInstallRelease"

        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        def configurationArgs = ["--configuration", debug ? "debug" : "release"]
        def extraArgs = debug ? extension.debug.extraInstallFlags : extension.release.extraInstallFlags

        Task swiftInstall = project.task(type: Exec, name) {
            workingDir "src/main/swift"
            executable toolchainHandle.swiftInstallPath
            args configurationArgs + extraArgs
        }

        if (debug) {
            addCompatibilityAlias(project, swiftInstall, "swiftInstall")
        }

        return swiftInstall
    }

    private Task createSwiftBuildTask(Project project, boolean debug) {
        String name = debug ? "swiftBuildDebug" :  "swiftBuildRelease"

        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        def configurationArgs = ["--configuration", debug ? "debug" : "release"]
        def extraArgs = debug ? extension.debug.extraBuildFlags : extension.release.extraBuildFlags

        Task swiftBuild = project.task(type: Exec, name) {
            workingDir "src/main/swift"
            executable toolchainHandle.swiftBuildPath
            args configurationArgs + extraArgs
        }

        if (debug) {
            addCompatibilityAlias(project, swiftBuild, "swiftBuild")
            addCompatibilityAlias(project, swiftBuild, "compileSwift")
        }

        return swiftBuild
    }

    private Task createCopyTask(Project project, boolean debug) {
        String name = debug ? "copySwiftDebug" :  "copySwiftRelease"

        String swiftPmBuildPath = debug ?
                "src/main/swift/.build/debug" : "src/main/swift/.build/release"

        return project.task(type: Copy, name) {
            from toolchainHandle.swiftLibFolder
            from "src/main/swift/.build/jniLibs/armeabi-v7a"
            from swiftPmBuildPath

            include "*.so"

            into "src/main/jniLibs/armeabi-v7a"
            
            fileMode 0644
        }
    }

    private static Task addCompatibilityAlias(Project project, Task task, String alias) {
        String originalName = task.name

        return project.task(alias).dependsOn(task).doFirst {
            print "${alias} is deprecated use ${originalName}"
        }
    }
}
