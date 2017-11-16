
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
        def extension = project.extensions.create('swift', SwiftAndroidPluginExtension)

        Task generateSwift = createGenerateSwiftTask(project)

        Task copySwiftStdlib = createCopyStdlibTask(project)
        Task swiftBuild = createSwiftBuildTask(project)
        Task copySwift = createCopyTask(project)
        copySwift.dependsOn(swiftBuild, copySwiftStdlib)

        Task swiftClean = createCleanTask(project)
        createSwiftInstallTask(project)
        createSwiftUpdateTask(project)

        project.afterEvaluate {
            // according to Protobuf gradle plugin, the Android variants are only available here
            // TODO: read those variants; we only support debug right now

            Task compileNdkTask = project.tasks.getByName("compileDebugNdk")
            compileNdkTask.dependsOn(copySwift)

            Task preBuildTask = project.tasks.getByName("preBuild")
            preBuildTask.dependsOn(generateSwift)

            if (extension.cleanEnabled) {
                Task cleanTask = project.tasks.getByName("clean")
                cleanTask.dependsOn(swiftClean)
            }
        }
    }

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

    private static String getScriptRoot() {
        return System.getenv("HOME") + "/.gradle/scripts/"
    }

    private static Task createGenerateSwiftTask(Project project) {
        return project.task(type: Exec, "generateSwift") {
            commandLine(getScriptRoot() + "generate-swift.sh")
            workingDir("src/main/swift")
        }
    }

    private static Task createSwiftBuildTask(Project project) {
        Task swiftBuild = project.task(type: Exec, "swiftBuild") {
            commandLine(getScriptRoot() + "swift-build.sh")
            workingDir("src/main/swift")
        }

        addCompatibilityAlias(project, swiftBuild, "compileSwift")

        return swiftBuild
    }

    private static Task addCompatibilityAlias(Project project, Task task, String alias) {
        String originalName = task.name

        return project.task(alias).dependsOn(task).doFirst {
            print "${alias} is deprecated use ${ originalName}"
        }
    }

    private static Task createSwiftInstallTask(Project project) {
        return project.task(type: Exec, "swiftInstall") {
            commandLine(getScriptRoot() + "invoke-external-build.sh")
            workingDir("src/main/swift")
        }
    }

    private static Task createCopyStdlibTask(Project project) {
        return project.task(type: Exec, "copySwiftStdlib") {
            commandLine(
                    getScriptRoot() + "copy-libraries.sh",
                    "src/main/jniLibs/armeabi-v7a"
            )
        }
    }

    private static Task createCopyTask(Project project) {
        return project.task(type: Copy, "copySwift") {
            from("src/main/swift/.build/debug")
            from("src/main/swift/.build/jniLibs/armeabi-v7a")

            include("*.so")

            into("src/main/jniLibs/armeabi-v7a")
            
            fileMode 0644
        }
    }
}
