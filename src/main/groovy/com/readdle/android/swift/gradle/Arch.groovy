package com.readdle.android.swift.gradle

enum Arch {
    ARM("armv7-unknown-linux-android", "armv7-linux-androideabi", "arm-linux-androideabi", "armeabi-v7a", "TRIPPLE_ARM_LINUX_ANDROID"),
    ARM64("aarch64-unknown-linux-android", "aarch64-linux-android", "aarch64-linux-android", "arm64-v8a", "TRIPPLE_AARCH64_LINUX_ANDROID"),
    x86_64("x86_64-unknown-linux-android", "x86_64-linux-android", "x86_64-linux-android", "x86_64", "TRIPPLE_X86_64_LINUX_ANDROID"),
    ;

    final String swiftArch
    final String swiftTarget
    final String swiftTriple
    final String  ndkTriple
    final String androidAbi
    final String tripleFlag

    private Arch(String swiftTarget, String swiftTriple, String ndkTriple, String androidAbi, String tripleFlag) {
        this.swiftArch = swiftTarget.split("-").first()
        this.swiftTriple = swiftTriple
        this.swiftTarget = swiftTarget
        this.ndkTriple = ndkTriple
        this.androidAbi = androidAbi
        this.tripleFlag = tripleFlag
    }

    String getVariantName() {
        return name().toLowerCase(Locale.US).capitalize()
    }

    static boolean isValidAndroidAbi(String abi) {
        return values().any { it.androidAbi == abi }
    }
}
