
//
// Injects Swift relevant tasks into gradle build process
// Mostly these are performed by scripts in ~/.gradle/scripts
// installed when the plugin is installed by "create_scripts.sh"
//

package net.zhuoweizhang.swiftandroid

import org.gradle.api.*
import org.gradle.api.tasks.*

public class SwiftAndroidPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        Task generateSwiftTask = createGenerateSwiftTask(project, "generateSwift")
        Task compileSwiftTask = createCompileSwiftTask(project, "compileSwift")
        Task copySwiftStdlibTask = createCopyStdlibTask(project, "copySwiftStdlib")
        Task copySwiftTask = createCopyTask(project, "copySwift")

        copySwiftTask.dependsOn(compileSwiftTask, copySwiftStdlibTask)

        Task cleanSwiftTask = project.task(type: Delete, "cleanSwift") {
            // I don't trust Swift Package Manager's --clean
            delete "src/main/swift/.build"
        }

        project.afterEvaluate {
            // according to Protobuf gradle plugin, the Android variants are only available here
            // TODO: read those variants; we only support debug right now

            Task compileNdkTask = project.tasks.getByName("compileDebugNdk")
            compileNdkTask.dependsOn(copySwiftTask)

            Task preBuildTask = project.tasks.getByName("preBuild")
            preBuildTask.dependsOn(generateSwiftTask)

            Task cleanTask = project.tasks.getByName("clean")
            cleanTask.dependsOn(cleanSwiftTask)
        }
    }

    private static String getScriptRoot() {
        return System.getenv("HOME") + "/.gradle/scripts/"
    }

    private static Task createGenerateSwiftTask(Project project, String name) {
        return project.task(type: Exec, name) {
            commandLine(getScriptRoot() + "generate-swift.sh")
            workingDir("src/main/swift")
        }
    }

    private static Task createCompileSwiftTask(Project project, String name) {
        return project.task(type: Exec, name) {
            commandLine(getScriptRoot() + "swift-build.sh")
            workingDir("src/main/swift")
        }
    }

    private static Task createCopyStdlibTask(Project project, String name) {
        return project.task(type: Exec, name) {
            commandLine(getScriptRoot() + "copy-libraries.sh",
                    "src/main/jniLibs/armeabi-v7a")
        }
    }

    private static Task createCopyTask(Project project, String name) {
        return project.task(type: Copy, name) {
            from("src/main/swift/.build/debug")
            from("src/main/swift/.build/jniLibs/armeabi-v7a")

            include("*.so")

            into("src/main/jniLibs/armeabi-v7a")
            
            fileMode 0644
        }
    }
}
