
# Swift Android Gradle plugin ![Maven Central](https://img.shields.io/maven-central/v/com.readdle.android.swift/gradle)

This plugin integrates [Swift Android Toolchain](https://github.com/readdle/swift-android-toolchain) to Gradle

## Pre-requirements

This plugin require [Android NDK r21e](https://dl.google.com/android/repository/android-ndk-r21e-darwin-x86_64.zip) and [Swift Android Toolchain](https://github.com/readdle/swift-android-toolchain/releases/latest)

Plugin lookup NDK and toolchain by environment variables `ANDROID_NDK_HOME` and `SWIFT_ANDROID_HOME` or `local.properties` in project root

    ndk.dir=/path/to/ndk
    swift-android.dir=/path/to/toolchain

## Setup

Plugin build swift code using [SwiftPM](https://github.com/apple/swift-package-manager) so you should define you code inside SwiftPM modules. Root module should be located inside `app/src/main/swift`. See [sample](https://github.com/readdle/swift-android-architecture).

#### Adding plugin to gradle scripts
1. Add the following to your root build.gradle

```gradle
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'com.readdle.android.swift:gradle:1.4.0'
    }
}
```

2. Add the following to your project build.gradle

```gradle
apply plugin: 'com.readdle.android.swift'
```

3. Configure build types and arhitecture

```gradle
buildTypes {
    release {
        debuggable false
        // Build Swift in release mode
        jniDebuggable false
        // all 4 arhitectures
    }
    debug {
        debuggable true
        // Build Swift in debug mode
        jniDebuggable true
        // only 2 arhitectures
        ndk {
            abiFilters = ["arm64-v8a", "x86_64"]
        }
    }
}
```

4. Optionally you can add some extra configuration to your project build.gradle. For example:

```gradle
swift {
    // helpers to forward flags from Swift Package Manager to Swift Compiler Frontend
    def passToFrontend = ["-Xswiftc", "-Xfrontend", "-Xswiftc"]
    def disableObjcAttr = passToFrontend + "-experimental-disable-objc-attr"
    
    // Enables swift clean when ./gradlew clean invoked. Default true
    cleanEnabled false 
    
    // Custom swift flags for debug build
    debug {
        // Set custom preprocessor flags
        extraBuildFlags "-Xswiftc", "-DDEBUG"
        // Disable @objc and dynamic
        extraBuildFlags disableObjcAttr
    }
    
    // Custom swift flags for release build
    release {
        // enable symbols in relase mode
        extraBuildFlags "-Xswiftc", "-g"
        // Disable @objc and dynamic
        extraBuildFlags disableObjcAttr
    }
}
```

## Usage

In simple cases you can just run `./gradlew assembleDebug` and everything will work.
If your use swift package manager dependencies with external build process you should firstly invoke `./gradlew swiftInstallDebug`

#### Other SwiftPM to gradle mapping:

| Gradle                         | SwiftPM                               |
|--------------------------------|---------------------------------------|
| `./gradlew swiftClean`         | `swift package clean`                 |
| `./gradlew swiftBuildDebug`    | `swift build`                         |
| `./gradlew swiftBuildRelease`  | `swift build --configuration release` |
| `./gradlew swiftPackageUpdate` | `swift package update`                |

## Sample

See [sample android app](https://github.com/readdle/swift-android-architecture).
