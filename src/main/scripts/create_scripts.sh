#!/bin/bash
#
# Called by gradle install to create scripts used in Android build.
# Scripts are kept in ~/.gradle/scripts and referred to in gradle source:
# gradle/src/main/groovy/net/zhuoweizhang/swiftandroid/SwiftAndroidPlugin.groovy
#

export ANDROID_NDK="${ANDROID_NDK?-Please export ANDROID_NDK}"
export JAVA_HOME="${JAVA_HOME?-Please export JAVA_HOME}"

SWIFT_INSTALL="$(dirname "$PWD")"
UNAME="$(uname)"
UNAME_LOWERCASED="$(uname | tr '[:upper:]' '[:lower:]')"

SCRIPTS=~/.gradle/scripts

cat <<DOC &&

Running: $SWIFT_INSTALL/swift-android-gradle/$0

This version of the gradle plugin inserts four scripts into the Android
build process. These auto-generate Swift code for any Java classes in a
directory "swiftbindings", build the swift, compile Swift for Android
and copy Swift standard libraries into the jniLibs directory.

Add the following to your project build.gradle's "buildscript/dependencies"

        classpath 'net.zhuoweizhang:swiftandroid:1.0.0'

And add this to the module's build.gradle:

apply plugin: 'net.zhuoweizhang.swiftandroid'

This version of the plugin requires https://github.com/SwiftJava/java_swift
with a tag > 2.1.0 *** Check for "pinning" files created by Swift PM. **

Consult https://github.com/SwiftJava/swift-android-kotlin for an example.

DOC

GLIBC_MODULEMAP="$SWIFT_INSTALL/usr/lib/swift/android/armv7/glibc.modulemap"
if [[ ! -f "$GLIBC_MODULEMAP.orig" ]]; then
    cp "$GLIBC_MODULEMAP" "$GLIBC_MODULEMAP.orig"
fi &&

sed -e "s@/usr/local/android/ndk/platforms/android-21/arch-arm/@$SWIFT_INSTALL/ndk-android-21@" <"$GLIBC_MODULEMAP.orig" >"$GLIBC_MODULEMAP" &&

install_patched_swift_build() {
    pushd /tmp
        git clone https://github.com/zayass/swift-package-manager.git

        pushd swift-package-manager
            git checkout swift-4.0-branch
            swift build
            cp .build/debug/swift-build $SWIFT_INSTALL/usr/$UNAME/
            ln -s $(xcrun --find swift-build-tool) $SWIFT_INSTALL/usr/$UNAME/
        popd

        rm -rf swift-package-manager
    popd
}

copy_swift_pm_runtime() {
    local xcode_toolchain=$(dirname $(dirname $(dirname $(xcrun --find swiftc))))
    cp -r $xcode_toolchain/usr/lib/swift/pm $SWIFT_INSTALL/usr/lib/swift/
}

rm -f "$SWIFT_INSTALL/usr/bin/swift" &&
if [[ "$UNAME" == "Darwin" ]]; then
    install_patched_swift_build
    copy_swift_pm_runtime

    SWIFT="$(xcrun --find swift)"
else
    SWIFT="$(which swift)"
    if [[ "$SWIFT" == "" ]]; then
        echo
        echo "*** A swift binary needs to be in your \$PATH to proceed ***"
        echo
        exit 1
    fi
fi &&
ln -sf "$SWIFT" "$SWIFT_INSTALL/usr/bin/swift" &&

echo "Swift path selected:" && ls -l "$SWIFT_INSTALL/usr/bin/swift" && echo &&

mkdir -p "$SCRIPTS" && cd "$SCRIPTS" && rm -rf /tmp/{genswift,bindings} &&

cat <<SCRIPT >generate-swift.sh &&
#!/bin/bash
#
# Pre-build stage to regenerate swift source from bindings sources
# Also generates Swift proxy classes so it has to be pre-build
#

SWIFT_INSTALL="$SWIFT_INSTALL"
export JAVA_HOME="$JAVA_HOME"

# compile genswift if required
if [[ ! -f /tmp/genswift/genswift.class ]]; then
    mkdir -p /tmp/genswift &&
    cd "\$SWIFT_INSTALL/swift-android-gradle/src/main/scripts" &&
    "\$JAVA_HOME/bin/javac" -d /tmp/genswift genswift.java &&
    cd - >/dev/null
fi &&

# regenerate swift for any bindings if a source file has changed or exit
# yes, this is perl...
cd ../java && (find . -type f | grep /swiftbindings/ | perl <(cat <<'PERL'
use strict;
while ( my \$source = <STDIN> ) {
    chomp \$source;
    (my \$class = "/tmp/bindings/\$source") =~ s/\.java\$/.class/;
    # Binding source more recent than when it was last generated?
    exit 1 if (stat \$source)[9] > (stat \$class)[9];
}
PERL
)) && exit 0 # Bindings up to date

# Compile bindings
rm -rf /tmp/bindings && mkdir /tmp/bindings &&
"\$JAVA_HOME/bin/javac" -parameters -d /tmp/bindings \`find . -type f | grep /swiftbindings/\` &&

# Pack bindings into a jar
cd - >/dev/null && cd /tmp/bindings &&
"\$JAVA_HOME/bin/jar" cf bindings.jar \`find . -type f -name '*.class'\` && cd - >/dev/null &&

# Code gen classes in jar to Swift using genswift.java
"\$JAVA_HOME/bin/jar" tf /tmp/bindings/bindings.jar | grep /swiftbindings/ | sed 's@\\.class\$@@' | \
"\$JAVA_HOME/bin/java" -cp /tmp/genswift:/tmp/bindings/bindings.jar \
genswift 'java/lang|java/util|java/sql' Sources ../java

SCRIPT

cat <<SCRIPT >swift-build.sh &&
#!/bin/bash
#
# Script to call swift build with PATH set correctly
#

SWIFT_INSTALL="$SWIFT_INSTALL"

export PATH="\$SWIFT_INSTALL/usr/bin:\$PATH"
export CC="\$ANDROID_NDK/toolchains/llvm/prebuilt/$UNAME_LOWERCASED-x86_64/bin/clang"
export SWIFT_EXEC=~/.gradle/scripts/swiftc-android.sh

# Uncomment if you would like to work with packages containing prebuilt binaries
"\$SWIFT_INSTALL"/swift-android-gradle/src/main/scripts/collect-dependencies.py

SCRIPT
if [[ "$UNAME" == "Darwin" ]]; then
    echo '"$SWIFT_INSTALL"/usr/Darwin/swift-build --destination=$HOME/.gradle/scripts/android-destination.json "$@"' >> swift-build.sh
else
    echo 'swift build --destination=$HOME/.gradle/scripts/android-destination.json "$@"' >> swift-build.sh
fi


cat <<SCRIPT >swiftc-android.sh &&
#!/bin/bash
#
# Substitutes in for swiftc to compile package and build Android sources
#

SWIFT_INSTALL="$SWIFT_INSTALL"
export PATH="\$SWIFT_INSTALL/usr/bin:\$SWIFT_INSTALL/usr/$UNAME:\$PATH"

if [[ "\$*" =~ " -fileno " ]]; then
    swift "\$@" || { 
        return_code=\$? 
        echo "*** Error executing: \$0 \$@"
        exit \$return_code
    }
    exit 0
fi

# compile using toolchain's swiftc with Android target
swiftc "\$@" || { 
    return_code=\$? 
    echo "*** Error executing: \$0 \$@"
    exit \$return_code
}
SCRIPT

cat <<SCRIPT >copy-libraries.sh &&
#!/bin/bash
#
# Copy swift libraries for inclusion in app APK
#

DESTINATION="\$1"
SWIFT_INSTALL="$SWIFT_INSTALL"

PREBUILTS="\$(find "\$PWD"/src/main/swift/.build/checkouts -name '*.so')"

mkdir -p "\$DESTINATION" && cd "\$DESTINATION" &&
rsync -a \$PREBUILTS "\$SWIFT_INSTALL"/usr/lib/swift/android/*.so .

SCRIPT

cat <<SCRIPT >run-tests.sh &&
#!/bin/bash
#
# Builds test bundles and pushes them to the device and runs them
#

export SWIFT_INSTALL="$SWIFT_INSTALL"
"\$SWIFT_INSTALL"/swift-android-gradle/src/main/scripts/run-tests.py

SCRIPT

cat <<SCRIPT >android-destination.json &&
{
    "version": 1,
    "dynamic-library-extension": "so",

    "target": "armv7-unknown-linux-androideabi",
    "sdk": "$SWIFT_INSTALL/ndk-android-21",
    "toolchain-bin-dir": "$SWIFT_INSTALL/usr/$UNAME",

    "extra-swiftc-flags": [
        "-use-ld=gold", 
        "-tools-directory", 
        "$SWIFT_INSTALL/usr/$UNAME",
        "-L$SWIFT_INSTALL/usr/$UNAME"
    ],
    "extra-cc-flags": [
        "-fPIC",
        "-fblocks",
        "-I$ANDROID_NDK/sources/cxx-stl/llvm-libc++/include",
        "-I$SWIFT_INSTALL/usr/lib/swift"
    ],
    "extra-cpp-flags": [
        "-lc++_shared"
    ]
}
SCRIPT

chmod +x {generate-swift,swift-build,swiftc-android,copy-libraries,run-tests}.sh &&
echo Created: $SCRIPTS/{generate-swift,swift-build,swiftc-android,copy-libraries,run-tests}.sh &&
echo

cd "$SWIFT_INSTALL"
if [[ ! -d Injection4Android ]]; then
    git clone https://github.com/SwiftJava/Injection4Android.git
    echo "Cloned Injection4Android for runtime code injection"
    echo "See https://github.com/SwiftJava/Injection4Android"
fi
