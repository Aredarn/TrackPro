# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# --- Release rules (R8 enabled) ---
# Gson reads these by reflection on field names and generic signatures (tracks.json seeding).
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.example.trackpro.dataClasses.** { *; }
-keep class com.example.trackpro.online.BundledPremadeTracks$** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.errorprone.annotations.**
