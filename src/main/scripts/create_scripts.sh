#!/bin/bash

SCRIPTS=~/.gradle/scripts

cat <<'DOC' &&

This version of the gradle plugin uses scripts which accepts the following environment variable:

ANDROID_NDK_HOME: path to an Android NDK downloadable from:
http://developer.android.com/ndk/downloads/index.html

The swift-install/usr/bin of a swift compiler toolchain built for Android must be in your path:
https://github.com/apple/swift/blob/master/docs/Android.md

DOC

mkdir -p $SCRIPTS && cd $SCRIPTS &&

cat <<'SCRIPT' >copy-libraries.sh &&
#!/bin/bash

DESTINATION="$1"
ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-/usr/local/android/ndk}
ANDROID_SWIFT_HOME=$(dirname $(dirname $(which swiftc)))

mkdir -p "$DESTINATION" && cd "$DESTINATION" &&
rsync -u "$ANDROID_SWIFT_HOME"/lib/swift/android/*.so . &&
#rsync -u "$ANDROID_NDK_HOME/sources/cxx-stl/llvm-libc++/libs/armeabi-v7a/libc++_shared.so" . &&
rpl -R -e libicu libscu lib*.so && rm -f *Unittest*
SCRIPT

cat <<'SCRIPT' >swiftc-android.sh &&
#!/bin/bash

ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-/usr/local/android/ndk}

if [[ "$*" =~ " -fileno " ]]; then
    swift "$@" || (echo "*** Error executing: $0 $@" && exit 1)
    exit $?
fi

ARGS=$(echo "$*" | sed -E "s@-target [^[:space:]]+ -sdk /[^[:space:]]* @@")

if [[ "$*" =~ " -emit-executable " ]]; then
#    if [[ ! -f "/usr/bin/armv7-none-linux-androideabi-ld.gold" ]]; then
#    cat <<EOF
#$0:
#
#Missing correct linker /usr/bin/armv7-none-linux-androideabi-ld.gold.
#This should be a link to the following binary included in the Android NDK
#\$ANDROID_NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/*-x86_64/bin/arm-linux-androideabi-ld.gold
#
#EOF
#    exit 1
#    fi
    LINKER_ARGS="-Xlinker -shared -Xlinker -export-dynamic"
fi

swiftc -target armv7-none-linux-androideabi \
    -sdk $ANDROID_NDK_HOME/platforms/android-21/arch-arm \
    -L $ANDROID_NDK_HOME/sources/cxx-stl/llvm-libc++/libs/armeabi-v7a \
    -L $ANDROID_NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/*-x86_64/lib/gcc/arm-linux-androideabi/4.9* \
    $LINKER_ARGS $ARGS || (echo "*** Error executing: $0 $@" && exit 1)
SCRIPT

chmod +x {copy-libraries,swiftc-android}.sh
