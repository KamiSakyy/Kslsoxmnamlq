#!/usr/bin/env bash
set -e

echo "=== Building Native Security Libraries ==="

NDK_DIR=$(find "$ANDROID_HOME/ndk" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1)
if [ -z "$NDK_DIR" ] || [ ! -d "$NDK_DIR" ]; then
    echo "NDK not found in $ANDROID_HOME/ndk, attempting sdkmanager install..."
    yes | sdkmanager "ndk;26.1.10909125" >/dev/null 2>&1 || yes | sdkmanager "ndk;25.2.9519653" >/dev/null 2>&1 || true
    NDK_DIR=$(find "$ANDROID_HOME/ndk" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1)
fi

mkdir -p yoru-android/app/src/main/jniLibs/arm64-v8a yoru-android/app/src/main/jniLibs/armeabi-v7a

if [ -n "$NDK_DIR" ] && [ -d "$NDK_DIR" ]; then
    echo "Found NDK at: $NDK_DIR"
    TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin"
    if [ -x "$TOOLCHAIN/aarch64-linux-android26-clang" ]; then
        echo "Compiling for arm64-v8a..."
        "$TOOLCHAIN/aarch64-linux-android26-clang" -shared -fPIC -O3 -s \
          -Wall -Wextra -Wno-unused-parameter \
          yoru-android/app/src/main/cpp/tsuyu_sec.c \
          -o yoru-android/app/src/main/jniLibs/arm64-v8a/libtsuyu_sec.so
    fi
    if [ -x "$TOOLCHAIN/armv7a-linux-androideabi26-clang" ]; then
        echo "Compiling for armeabi-v7a..."
        "$TOOLCHAIN/armv7a-linux-androideabi26-clang" -shared -fPIC -O3 -s \
          -Wall -Wextra -Wno-unused-parameter \
          yoru-android/app/src/main/cpp/tsuyu_sec.c \
          -o yoru-android/app/src/main/jniLibs/armeabi-v7a/libtsuyu_sec.so
    fi
else
    echo "NDK not found, using prebuilt valid native stubs"
fi

echo "Native libraries status:"
ls -la yoru-android/app/src/main/jniLibs/*/*.so
