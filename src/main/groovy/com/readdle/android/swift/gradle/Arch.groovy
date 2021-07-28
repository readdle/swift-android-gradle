package com.readdle.android.swift.gradle

enum Arch {
    ARM("armv7-unknown-linux-androideabi", "armeabi-v7a"),
    ARM64("aarch64-unknown-linux-android", "arm64-v8a"),
    x86("i686-unknown-linux-android", "x86"),
    x86_64("x86_64-unknown-linux-android","x86_64"),
    ;

    final String swiftArch
    final String swiftTriple
    final String androidAbi

    private Arch(String swiftTriple, String androidAbi) {
        this.swiftArch = swiftTriple.split("-").first()
        this.swiftTriple = swiftTriple
        this.androidAbi = androidAbi
    }

    String getVariantName() {
        return name().toLowerCase(Locale.US).capitalize()
    }

    static boolean isValidAndroidAbi(String abi) {
        return values().any { it.androidAbi == abi }
    }
}
