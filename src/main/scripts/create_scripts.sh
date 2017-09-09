#!/bin/bash
#
# Called by gradle install to create scripts used in Android build.
# Scripts are kept in ~/.gradle/scripts and referred to in gradle source:
# gradle/src/main/groovy/net/zhuoweizhang/swiftandroid/SwiftAndroidPlugin.groovy
#

export ANDROID_HOME="${ANDROID_HOME?-Please export ANDROID_HOME}"
export JAVA_HOME="${JAVA_HOME?-Please export JAVA_HOME}"

SWIFT_INSTALL="$(dirname "$PWD")"
UNAME="$(uname)"

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

rm -f "$SWIFT_INSTALL/usr/bin/swift" &&
if [[ "$UNAME" == "Darwin" ]]; then
    SWIFT="$(xcode-select -p)/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift"
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
export SWIFT_EXEC=~/.gradle/scripts/swiftc-android.sh

swift build "\$@"

SCRIPT

cat <<SCRIPT >swiftc-android.sh &&
#!/bin/bash
#
# Substitutes in for swiftc to compile package and build Android sources
#

SWIFT_INSTALL="$SWIFT_INSTALL"
export PATH="\$SWIFT_INSTALL/usr/$UNAME:\$SWIFT_INSTALL/usr/bin:\$PATH"

if [[ "\$*" =~ " -fileno " ]]; then
    swift "\$@" || (echo "*** Error executing: \$0 \$@" && exit 1)
    exit $?
fi

# remove whatever target SwiftPM has supplied
ARGS=\$(echo "\$*" | sed -E "s@-target [^[:space:]]+ -sdk /[^[:space:]]* (-F /[^[:space:]]* )?@@")

if [[ "$UNAME" == "Darwin" ]]; then
    # xctest
    if [[ "\$*" =~ "-Xlinker -bundle" ]]; then
        xctest_bundle=\$(echo \$ARGS | grep -o \$(pwd)'[^[:space:]]*xctest')
        rm -rf \$xctest_bundle

        modulemaps=\$(find .build/checkouts -name '*.modulemap' | sed 's@^@-I @' | sed 's@/module.modulemap\$@@')

        build_dir=\$(echo "\$ARGS" | grep -o '\-L '\$(pwd)'/.build/[^[:space:]]*' | sed -E "s@-L @@")

        ARGS=\$(echo "\$ARGS" | sed -E "s@-Xlinker -bundle@-emit-executable@")
        ARGS=\$(echo "\$ARGS" | sed -E "s@xctest[^[:space:]]*PackageTests@xctest@")

        ARGS="\$ARGS \$modulemaps -I \$build_dir Tests/LinuxMain.swift"
    fi

    # .dylib -> .so
    if [[ "\$ARGS" =~ "-emit-library" ]]; then
        ARGS=\$(echo "\$ARGS" | sed -E "s@\.dylib@\.so@")
    fi
fi

# for compatability with V3 Package.swift for now
if [[ "\$ARGS" =~ " -emit-executable " && "\$ARGS" =~ ".so " ]]; then
    ARGS=\$(echo "\$ARGS" | sed -E "s@ (-module-name [^[:space:]]+ )?-emit-executable @ -emit-library @")
fi

# link in prebuilt libraries
for lib in \`find "\$PWD"/.build/checkouts -name '*.so'\`; do
    DIR="\$(dirname \$lib)"
    LIB="\$(basename \$lib | sed -E 's@^lib|.so\$@@g')"
    ARGS="\$ARGS -L\$DIR -l\$LIB"
done

# compile using toolchain's swiftc with Android target
swiftc -target armv7-none-linux-androideabi -sdk "\$SWIFT_INSTALL/ndk-android-21" \\
    -L "\$SWIFT_INSTALL/usr/$UNAME" -tools-directory "\$SWIFT_INSTALL/usr/$UNAME" \\
    \$ARGS || (echo "*** Error executing: \$0 \$ARGS" && exit 1)

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

chmod +x {generate-swift,swift-build,swiftc-android,copy-libraries,run-tests}.sh &&
echo Created: $SCRIPTS/{generate-swift,swift-build,swiftc-android,copy-libraries,run-tests}.sh &&
echo
