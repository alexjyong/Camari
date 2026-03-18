#!/bin/bash
# Build script for Camari Android app
# Builds debug APK for testing on Android device

set -e

echo "=== Camari Android Build ==="
echo ""

# Ensure Android SDK env vars are set (devcontainer sets these via remoteEnv,
# but fall back to the known install path if missing)
if [ -z "$ANDROID_HOME" ] && [ -d "/opt/android-sdk" ]; then
  export ANDROID_HOME="/opt/android-sdk"
  export ANDROID_SDK_ROOT="/opt/android-sdk"
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
fi

cd mobile

echo "=== Installing npm dependencies ==="
npm install

echo "=== Building TypeScript/React app ==="
npm run build

echo "=== Syncing Capacitor to Android ==="
npx cap sync android

echo "=== Building debug APK ==="
cd android
./gradlew assembleDebug

echo ""
echo "=== Build complete ==="
echo "APK location: mobile/android/app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "To install on device:"
echo "  adb install mobile/android/app/build/outputs/apk/debug/app-debug.apk"
