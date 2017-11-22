
//
// Injects Swift relevant tasks into gradle build process
// Mostly these are performed by scripts in ~/.gradle/scripts
// installed when the plugin is installed by "create_scripts.sh"
//

package net.zhuoweizhang.swiftandroid

import org.gradle.api.*
import org.gradle.api.tasks.*

class SwiftAndroidPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def extension = project.extensions.create('swift', SwiftAndroidPluginExtension, project)

        Task generateSwift = createGenerateSwiftTask(project)
        Task swiftClean = createCleanTask(project)
        createSwiftUpdateTask(project)

        project.afterEvaluate {
            // according to Protobuf gradle plugin, the Android variants are only available here
            Task swiftBuildChainDebug = createSwiftBuildChain(project, true)
            Task swiftBuildChainRelease = createSwiftBuildChain(project, false)

            createSwiftInstallTask(project, true)
            createSwiftInstallTask(project, false)

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

    private static Task createSwiftBuildChain(Project project, boolean debug) {
        Task copySwiftStdlib = findOrCreateCopyStdlibTask(project)
        Task swiftBuild = createSwiftBuildTask(project, debug)
        Task copySwift = createCopyTask(project, debug)

        copySwift.dependsOn(swiftBuild, copySwiftStdlib)

        return copySwift
    }

    // Tasks
    private static Task createCleanTask(Project project) {
        Task forceClean = project.task(type: Delete, "swiftForceClean") {
            // I don't trust Swift Package Manager's --clean
            delete "src/main/swift/.build"
        }

        Task packageClean = project.task(type: Exec, "swiftPackageClean") {
            workingDir "src/main/swift"
            commandLine "swift", "package", "clean"
        }

        Task metaTask = project.task("swiftClean")

        project.afterEvaluate {
            def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

            Task impl = extension.usePackageClean ?
                    forceClean : packageClean

            metaTask.dependsOn(impl)
        }

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
        return project.task(type: Exec, "generateSwift") {
            commandLine(getScriptRoot() + "generate-swift.sh")
            workingDir("src/main/swift")
        }
    }

    private static Task findOrCreateCopyStdlibTask(Project project) {
        def name = "copySwiftStdlib"
        def exists = project.tasks.findByName(name)

        if (exists) {
            return exists
        }

        return project.task(type: Exec, name) {
            commandLine(
                    getScriptRoot() + "copy-libraries.sh",
                    "src/main/jniLibs/armeabi-v7a"
            )
        }
    }

    private static Task createSwiftInstallTask(Project project, boolean debug) {
        String name = debug ? "swiftInstallDebug" :  "swiftInstallRelease"

        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        def configurationArgs = ["--configuration", debug ? "debug" : "release"]
        def extraArgs = debug ? extension.debug.extraInstallFlags : extension.release.extraInstallFlags

        Task swiftInstall = project.task(type: Exec, name) {
            workingDir("src/main/swift")
            executable(getScriptRoot() + "invoke-external-build.sh")
            args(configurationArgs + extraArgs)
        }

        if (debug) {
            addCompatibilityAlias(project, swiftInstall, "swiftInstall")
        }

        return swiftInstall
    }

    private static Task createSwiftBuildTask(Project project, boolean debug) {
        String name = debug ? "swiftBuildDebug" :  "swiftBuildRelease"

        def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

        def configurationArgs = ["--configuration", debug ? "debug" : "release"]
        def extraArgs = debug ? extension.debug.extraBuildFlags : extension.release.extraBuildFlags

        Task swiftBuild = project.task(type: Exec, name) {
            workingDir("src/main/swift")
            executable(getScriptRoot() + "swift-build.sh")
            args(configurationArgs + extraArgs)
        }

        if (debug) {
            addCompatibilityAlias(project, swiftBuild, "swiftBuild")
            addCompatibilityAlias(project, swiftBuild, "compileSwift")
        }

        return swiftBuild
    }

    private static Task createCopyTask(Project project, boolean debug) {
        String name = debug ? "copySwiftDebug" :  "copySwiftRelease"

        String swiftPmBuildPath = debug ?
                "src/main/swift/.build/debug" : "src/main/swift/.build/release"

        return project.task(type: Copy, name) {
            from(swiftPmBuildPath)
            from("src/main/swift/.build/jniLibs/armeabi-v7a")

            include("*.so")

            into("src/main/jniLibs/armeabi-v7a")
            
            fileMode 0644
        }
    }

    // Utils
    private static String getScriptRoot() {
        return System.getenv("HOME") + "/.gradle/scripts/"
    }

    private static Task addCompatibilityAlias(Project project, Task task, String alias) {
        String originalName = task.name

        return project.task(alias).dependsOn(task).doFirst {
            print "${alias} is deprecated use ${originalName}"
        }
    }
}
