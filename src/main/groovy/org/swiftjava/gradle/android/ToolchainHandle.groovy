package org.swiftjava.gradle.android

import org.gradle.api.Project

class ToolchainHandle {
    public static final String FN_LOCAL_PROPERTIES = "local.properties"
    public static final String TOOLS_VERSION = "1.0"

    final File toolchainFolder
    final File ndkFolder

    ToolchainHandle(Project project) {
        def properties = loadProperties(project)
        toolchainFolder = findToolchain(properties)
        ndkFolder = findNdkLocation(properties)
    }

    private String getPathInSwiftHome(String path) {
        // Dummy cast for groovy late dispatch
        return new File((File) toolchainFolder, path).absolutePath
    }

    private String getTool(String name) {
        return getPathInSwiftHome("build-tools/${TOOLS_VERSION}/${name}")
    }

    private String getFolderInToolchain(String path) {
        return getPathInSwiftHome("toolchain/${path}")
    }

    boolean isToolchainPresent() {
        return toolchainFolder != null && toolchainFolder.isDirectory()
    }

    boolean isNdkPresent() {
        return ndkFolder != null && ndkFolder.isDirectory()
    }

    String getToolsManagerPath() {
        return getPathInSwiftHome("bin/swift-android")
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

    Map<String, String> getSwiftEnv() {
        return [
                SWIFT_ANDROID_HOME: toolchainFolder?.absolutePath,
        ]
    }

    Map<String, String> getFullEnv() {
        return [
                SWIFT_ANDROID_HOME: toolchainFolder?.absolutePath,
                ANDROID_NDK_HOME: ndkFolder?.absolutePath
        ]
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

    private static File findToolchain(Properties properties) {
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

    private static File findNdkLocation(Properties properties) {
        String ndkDirProp = properties.getProperty("ndk.dir");
        if (ndkDirProp != null) {
            return new File(ndkDirProp)
        }

        String envVar = System.getenv("ANDROID_NDK_HOME")
        if (envVar != null) {
            return new File(envVar)
        }

        return null
    }
}