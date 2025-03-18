
# Swift Android Gradle plugin ![Maven Central](https://img.shields.io/maven-central/v/com.readdle.android.swift/gradle)

This plugin integrates [Swift Android Toolchain](https://github.com/readdle/swift-android-toolchain) to Gradle

## Pre-requirements

This plugin requires **Android NDK** and the **Swift Android Toolchain**. Follow the [installation guide](https://github.com/readdle/swift-android-toolchain) to set them up.

The plugin looks up the NDK and toolchain using the environment variables `ANDROID_NDK_HOME` and `SWIFT_ANDROID_HOME`, or by reading `local.properties` in the project root:

    ndk.dir=/path/to/ndk
    swift-android.dir=/path/to/toolchain

## Setup

Plugin build swift code using [SwiftPM](https://github.com/apple/swift-package-manager) so you should define you code inside SwiftPM modules. Root module should be located inside `app/src/main/swift`. See [sample](https://github.com/andriydruk/swift-weather-app/tree/master/android/app/src/main/swift).

#### Adding plugin to gradle scripts
1. Add the following to your root build.gradle

```gradle
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'com.readdle.android.swift:gradle:6.0.3'
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
    // Enables swift clean when ./gradlew clean invoked. Default true
    cleanEnabled false 
    
    // Custom swift flags for debug build
    debug {
        // Set custom preprocessor flags
        extraBuildFlags "-Xswiftc", "-DDEBUG"
    }
    
    // Custom swift flags for release build
    release {
        // enable symbols in relase mode
        extraBuildFlags "-Xswiftc", "-g"
    }
}
```

## Usage

In simple cases you can just run `./gradlew assembleDebug` and everything will work.

#### Other SwiftPM to gradle mapping:

| Gradle                         | SwiftPM                               |
|--------------------------------|---------------------------------------|
| `./gradlew swiftClean`         | `swift package clean`                 |
| `./gradlew swiftBuildDebug`    | `swift build`                         |
| `./gradlew swiftBuildRelease`  | `swift build --configuration release` |
| `./gradlew swiftPackageUpdate` | `swift package update`                |

## Sample

See [sample app](https://github.com/andriydruk/swift-weather-app).
