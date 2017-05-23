#!/bin/bash

SCRIPTS=~/.gradle/scripts

cat <<'DOC'

This version of the gradle plugin uses scripts which require the following environment variable to be set:

ANDROID_NDK_HOME: path to an Android NDK downloadable from:
http://developer.android.com/ndk/downloads/index.html

The build/Ninja-ReleaseAssert/swift-linux-x86_64/bin of a swift compiler toolchain built for Android must be in your path:
https://github.com/apple/swift/blob/master/docs/Android.md

DOC

mkdir -p $SCRIPTS &&

cat <<'SCRIPT' >$SCRIPTS/copy-libraries.sh &&
#!/bin/bash

DESTINATION="$1"
ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-/usr/android/ndk}
ANDROID_SWIFT_HOME=$(dirname $(dirname $(which swiftc)))

mkdir -p "$DESTINATION" && cd "$DESTINATION" &&
rsync -u "$ANDROID_SWIFT_HOME"/lib/swift/android/*.so .
rsync -u "$ANDROID_NDK_HOME/sources/cxx-stl/llvm-libc++/libs/armeabi-v7a/libc++_shared.so" . &&
rpl -R -e libicu libscu lib*.so && rm -f *Unittest*
SCRIPT

cat <<'SCRIPT' >$SCRIPTS/swiftc-android.sh &&
#!/bin/bash

ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-/usr/android/ndk}

if [[ "$*" =~ " -fileno " ]]; then
    swift "$@" ||
	(echo "*** Error executing: $0 $@" && exit 1)
    exit $?
fi

ARGS=$(echo "$*" | sed -e 's@-target x86_64-unknown-linux -sdk / @@') 

if [[ "$*" =~ " -emit-executable " ]]; then
   ARGS="-Xlinker -shared -Xlinker -export-dynamic -Xlinker -fuse-ld=androideabi $ARGS"
fi

swiftc -target armv7-none-linux-androideabi \
    -sdk $ANDROID_NDK_HOME/platforms/android-21/arch-arm \
    -L $ANDROID_NDK_HOME/sources/cxx-stl/llvm-libc++/libs/armeabi-v7a \
    -L $ANDROID_NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/lib/gcc/arm-linux-androideabi/4.9* \
    $ARGS || (echo "*** Error executing: $0 $@" && exit 1)

SCRIPT

chmod +x $SCRIPTS/{copy-libraries,swiftc-android}.sh
