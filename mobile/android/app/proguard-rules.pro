# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep Capacitor plugin classes
-keep class com.capacitorjs.** { *; }
-keep class com.camari.** { *; }

# Keep Camera2 API classes
-keep class android.hardware.camera2.** { *; }
