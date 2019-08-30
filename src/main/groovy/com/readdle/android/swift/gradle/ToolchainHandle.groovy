package com.readdle.android.swift.gradle

import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.configuration.PropertiesConfigurationLayout
import org.gradle.api.GradleException
import org.gradle.api.Project


class ToolchainHandle {
    public static final String FN_LOCAL_PROPERTIES = "local.properties"
    public static final String TOOLS_VERSION = "1.9.4-swift5"
    public static final String SWIFT_ANDROID_HOME_KEY = "swift-android.dir"
    public static final String ANDROID_NDK_HOME_KEY = "ndk.dir"

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

    String getSwiftLibFolder(Arch arch) {
        return getFolderInToolchain("usr/lib/swift/android/${arch.swiftArch}")
    }

    Map<String, String> getSwiftEnv() {
        return [
                SWIFT_ANDROID_HOME: toolchainFolder?.absolutePath,
        ]
    }

    Map<String, String> getFullEnv(Arch arch) {
        return [
                SWIFT_ANDROID_ARCH: arch.swiftArch,
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
                throw new GradleException("Unable to read ${localProperties.absolutePath}", e)
            }
        }

        return properties
    }

    void updateProperties(Project project) {
        def toolchainPath = System.getenv("SWIFT_ANDROID_HOME")
        def ndkPath = System.getenv("ANDROID_NDK_HOME")

        if (toolchainPath == null && toolchainFolder == null) {
            throw new GradleException("SWIFT_ANDROID_HOME environment variable not defined")
        }

        if (ndkPath == null && ndkFolder == null) {
            throw new GradleException("ANDROID_NDK_HOME environment variable not defined")
        }

        File rootDir = project.getRootDir()
        File localProperties = new File(rootDir, FN_LOCAL_PROPERTIES)

        PropertiesConfiguration config = new PropertiesConfiguration()
        PropertiesConfigurationLayout layout = new PropertiesConfigurationLayout(config)

        try {
            localProperties.withInputStream {
                layout.load(new InputStreamReader(it))
            }
        } catch (IOException e) {
            throw new GradleException("Unable to read ${localProperties.absolutePath}", e)
        }

        config.setProperty(ANDROID_NDK_HOME_KEY, ndkPath)
        config.setProperty(SWIFT_ANDROID_HOME_KEY, toolchainPath)

        try {
            localProperties.withOutputStream {
                layout.save(new OutputStreamWriter(it))
            }
        } catch (IOException e) {
            throw new GradleException("Unable to write to ${localProperties.absolutePath}", e)
        }
    }

    private static File findToolchain(Properties properties) {
        String property = properties.getProperty(SWIFT_ANDROID_HOME_KEY)
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
        String ndkDirProp = properties.getProperty(ANDROID_NDK_HOME_KEY)
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
