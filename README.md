
# Swift Android Gradle plugin ![Maven Central](https://img.shields.io/maven-central/v/com.readdle.android.swift/gradle)

This plugin integrates Swift for Android builds using [swiftly](https://github.com/swiftlang/swiftly) and Swift Android SDK.

## Pre-requirements

This plugin requires **swiftly** and a **Swift Android SDK** installed.

1. Install swiftly and Swift 6.2+:
```bash
curl -L https://swiftlang.github.io/swiftly/swiftly-install.sh | bash
swiftly install 6.2
```

2. Install the Swift Android SDK:
```bash
swift sdk install <path-to-sdk.artifactbundle>
```

## Setup

Plugin builds Swift code using [SwiftPM](https://github.com/apple/swift-package-manager) so you should define your code inside SwiftPM modules. Root module should be located inside `app/src/main/swift`. See [sample](https://github.com/andriydruk/swift-weather-app/tree/master/android/app/src/main/swift).

#### Adding plugin to gradle scripts
1. Add the following to your root build.gradle

```gradle
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'com.readdle.android.swift:gradle:6.2'
    }
}
```

2. Add the following to your project build.gradle

```gradle
apply plugin: 'com.readdle.android.swift'
```

3. Configure build types and architecture

```gradle
buildTypes {
    release {
        debuggable false
        // Build Swift in release mode
        jniDebuggable false
        // all 3 architectures: arm64-v8a, armeabi-v7a, x86_64
    }
    debug {
        debuggable true
        // Build Swift in debug mode
        jniDebuggable true
        // limit architectures for faster builds
        ndk {
            abiFilters = ["arm64-v8a"]
        }
    }
}
```

4. Optionally you can add some extra configuration to your project build.gradle. For example:

```gradle
swift {
    // Enables swift clean when ./gradlew clean invoked. Default true
    cleanEnabled false
    
    // Android API level. Default 29
    apiLevel 31
    
    // Path to swiftly executable. Default: ~/.swiftly/bin/swiftly
    swiftlyPath "/custom/path/to/swiftly"
    
    // Swift version to use with swiftly. Default: "6.2"
    swiftlyVersion "6.2"
    
    // Path to Swift Android SDK. Default: ~/Library/org.swift.swiftpm/swift-sdks/readdle-swift-6.2.1-RELEASE_android.artifactbundle
    swiftSdkPath "/custom/path/to/sdk.artifactbundle"
    
    // Custom swift flags for debug build
    debug {
        // Set custom preprocessor flags
        extraBuildFlags "-Xswiftc", "-DDEBUG"
    }
    
    // Custom swift flags for release build
    release {
        // enable symbols in release mode
        extraBuildFlags "-Xswiftc", "-g"
    }
}
```

## Supported Architectures

- `arm64-v8a` (aarch64)
- `armeabi-v7a` (armv7)
- `x86_64`

## Usage

In simple cases you can just run `./gradlew assembleDebug` and everything will work.

#### Other SwiftPM to gradle mapping:

| Gradle                         | SwiftPM                               |
|--------------------------------|---------------------------------------|
| `./gradlew swiftClean`         | `swift package clean`                 |
| `./gradlew swiftBuildDebug`    | `swift build`                         |
| `./gradlew swiftBuildRelease`  | `swift build --configuration release` |

## Sample

See [sample app](https://github.com/andriydruk/swift-weather-app).
