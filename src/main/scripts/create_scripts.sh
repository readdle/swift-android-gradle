#!/bin/bash

SCRIPTS=~/.gradle/scripts

mkdir -p $SCRIPTS &&

cat <<'SCRIPT' >$SCRIPTS/copy-libraries.sh &&
#!/bin/bash

DESTINATION="$1"

ANDROID_ICU=${ANDROID_ICU:-~/libiconv-libicu-android}
ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-~/android-ndk-r12b}
ANDROID_SWIFT_HOME=$(dirname $(dirname $(which swiftc)))

mkdir -p "$DESTINATION" && cd "$DESTINATION" &&

if [[ ! -f "libscui18n.so" ]]; then
    # Use Swift libraries built from: https://github.com/apple/swift/blob/master/docs/Android.md 

    rsync -u "$ANDROID_SWIFT_HOME"/lib/swift/android/*.so . &&

    # apply technique from: https://medium.com/@ephemer/how-we-put-an-app-in-the-android-play-store-using-swift-67bd99573e3c

    rsync -u "$ANDROID_ICU"/armeabi-v7a/libicu*.so . &&
    rpl -R -e libicu libscu lib{icu,swift}*.so &&

    for i in libicu*.so; do \mv -f $i ${i/libicu/libscu}; done
fi &&

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

chmod +x $SCRIPTS/{copy-libraries,swiftc-android}.sh  &&

cat <<'DOC'

This version of the gradle plugin uses scripts which require the following environment variables to be set:

ANDROID_NDK_HOME: path to an Android NDK downloadable from:
http://developer.android.com/ndk/downloads/index.html

ANDROID_ICU: path to android port of icu librares containing the directory "armeabi-v7a" downloadable from:
https://github.com/SwiftAndroid/libiconv-libicu-android/releases/download/android-ndk-r12/libiconv-libicu-armeabi-v7a-ubuntu-15.10-ndk-r12.tar.gz

The build/Ninja-ReleaseAssert/swift-linux-x86_64/bin of a swift compiler toolchain built for Android must be in your path:
https://github.com/apple/swift/blob/master/docs/Android.md

Chances are you'll also need to move /usr/bin/ld.gold aside and replace it with a symbolic link to the following:
$ANDROID_NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/arm-linux-androideabi/bin/ld.gold

Finally, install "rpl" with: sudo apt-get install rpl

DOC

