
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

        Task generateSwiftTask = createGenerateSwiftTask(project)
        Task compileSwiftTask = createCompileSwiftTask(project)
        Task copySwiftStdlibTask = createCopyStdlibTask(project)
        Task copySwiftTask = createCopyTask(project)
        Task cleanSwift = createCleanTask(project)
        createSwiftUpdateTask(project)

        copySwiftTask.dependsOn(compileSwiftTask, extension, copySwiftStdlibTask)

        project.afterEvaluate {
            // according to Protobuf gradle plugin, the Android variants are only available here
            // TODO: read those variants; we only support debug right now

            Task compileNdkTask = project.tasks.getByName("compileDebugNdk")
            compileNdkTask.dependsOn(copySwiftTask)

            Task preBuildTask = project.tasks.getByName("preBuild")
            preBuildTask.dependsOn(generateSwiftTask)

            if (extension.cleanEnabled) {
                Task cleanTask = project.tasks.getByName("clean")
                cleanTask.dependsOn(cleanSwift)
            }
        }
    }

    private static Task createCleanTask(Project project) {
        Task forceCleanSwift = project.task(type: Delete, "forceCleanSwift") {
            // I don't trust Swift Package Manager's --clean
            delete "src/main/swift/.build"
        }

        Task swiftPackageClean = project.task(type: Exec, "swiftPackageClean") {
            workingDir "src/main/swift"
            commandLine "swift", "package", "clean"
        }

        Task metaTask = project.task("cleanSwift")

        project.afterEvaluate {
            def extension = project.extensions.getByType(SwiftAndroidPluginExtension)

            Task swiftCleanTask = extension.usePackageClean ?
                    swiftPackageClean : forceCleanSwift

            metaTask.dependsOn(swiftCleanTask)
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

    private static Task createCompileSwiftTask(Project project) {
        return project.task(type: Exec, "compileSwift") {
            commandLine(getScriptRoot() + "swift-build.sh")
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
