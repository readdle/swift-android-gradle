package org.swiftjava.gradle.android

import org.gradle.api.Project

class ToolchainHandle {
    public static final String FN_LOCAL_PROPERTIES = "local.properties"
    public static final String TOOLS_VERSION = "1.0"

    final File toolchainFolder

    ToolchainHandle(Project project) {
        def properties = loadProperties(project)
        toolchainFolder = findToolchain(project, properties)
    }

    private String getTool(String name) {
        return new File(toolchainFolder, "build-tools/${TOOLS_VERSION}/${name}").absolutePath
    }

    private String getFolderInToolchain(String path) {
        return new File(toolchainFolder, "toolchain/${path}").absolutePath
    }

    boolean isToolchainPresent() {
        return toolchainFolder != null && toolchainFolder.isDirectory()
    }

    String getToolsManagerPath() {
        return new File(toolchainFolder, "bin/swift-android").absolutePath
    }

    String getSwiftBuildPath() {
        return getTool("swift-build")
    }

    String getSwiftInstallPath() {
        return getTool("swift-install")
    }

    String getSwiftLibFolder() {
        return getFolderInToolchain("usr/lib/swift/android/")
    }

    private static Properties loadProperties(Project project) {
        File rootDir = project.getRootDir()
        File localProperties = new File(rootDir, FN_LOCAL_PROPERTIES)
        Properties properties = new Properties()

        if (localProperties.isFile()) {
            try {
                localProperties.withInputStream {
                    properties.load(it)
                }
            } catch (IOException e) {
                throw new RuntimeException("Unable to read ${localProperties.absolutePath}", e)
            }
        }

        return properties
    }

    private static File findToolchain(Project project, Properties properties) {
        String property = properties.getProperty("swift-android.dir")
        if (property != null) {
            return new File(property)
        }

        String envVar = System.getenv("SWIFT_ANDROID_HOME")
        if (envVar != null) {
            return new File(envVar)
        }

        return null
    }
}
