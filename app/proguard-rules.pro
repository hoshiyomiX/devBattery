# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Applications/Utilities/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/getproguard/index.html

# Preserve JavaScript interface methods from ProGuard renaming
-keepclassmembers class com.deviant.batterymonitor.MainActivity$BatteryBridge {
    @android.webkit.JavascriptInterface *;
}

# Keep BatteryBridge class itself
-keep class com.deviant.batterymonitor.MainActivity$BatteryBridge { *; }
