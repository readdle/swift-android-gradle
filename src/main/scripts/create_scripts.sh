#!/bin/bash

SCRIPTS=~/.gradle/scripts

cat <<'DOC'

This version of the gradle plugin uses scripts which require the following environment variable to be set:

ANDROID_NDK_HOME: path to an Android NDK downloadable from:
http://developer.android.com/ndk/downloads/index.html

The build/Ninja-ReleaseAssert/swift-linux-x86_64/bin of a swift compiler toolchain built for Android must be in your path:
https://github.com/apple/swift/blob/master/docs/Android.md

Chances are you'll also need to move /usr/bin/ld.gold aside and replace it with a symbolic link to the following:
$ANDROID_NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/arm-linux-androideabi/bin/ld.gold

DOC

mkdir -p $SCRIPTS &&

cat <<'SCRIPT' >$SCRIPTS/copy-libraries.sh &&
#!/bin/bash

DESTINATION="$1"

ANDROID_ICU=${ANDROID_ICU:-~/libiconv-libicu-android}
ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-~/android-ndk-r12b}
ANDROID_SWIFT_HOME=$(dirname $(dirname $(which swiftc)))

mkdir -p "$DESTINATION" && cd "$DESTINATION" &&

rsync -u "$ANDROID_SWIFT_HOME"/lib/swift/android/*.so . &&

rpl -R -e libicu libscu lib*.so && rm -f *Unittest* &&

rsync -u "$ANDROID_NDK_HOME"/sources/cxx-stl/llvm-libc++/libs/armeabi-v7a/libc++_shared.so . ||

(echo "*** Error executing: $0 $@" && exit 1)
SCRIPT

cat <<'SCRIPT' >$SCRIPTS/swiftc-android.sh &&
#!/bin/bash

ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-~/android-ndk-r12b}

if [[ $1 == "-Xlinker" ]]; then
   EXPORT_SYMBOLS="-Xlinker -export-dynamic"
fi

swiftc -target armv7-none-linux-androideabi \
    -sdk $ANDROID_NDK_HOME/platforms/android-21/arch-arm \
    -L $ANDROID_NDK_HOME/sources/cxx-stl/llvm-libc++/libs/armeabi-v7a \
    -L $ANDROID_NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/lib/gcc/arm-linux-androideabi/4.9* \
    $EXPORT_SYMBOLS "$@" ||

(echo "*** Error executing: $0 $@" && exit 1)
SCRIPT

chmod +x $SCRIPTS/{copy-libraries,swiftc-android}.sh
