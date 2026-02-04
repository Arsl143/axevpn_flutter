# AxeVPN Flutter Plugin - ProGuard Configuration
# Add project specific ProGuard rules here.

# Flutter wrapper
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }
-keep class com.google.firebase.** { *; }

# VPN Service - Critical for functionality
-keep public class com.axevpn.flutter.openvpn.AxeVPNFlutterPlugin {
    public *;
    private *;
}

-keep class com.axevpn.flutter.openvpn.** { *; }

# OpenVPN Core Library
-keep class de.blinkt.openvpn.** { *; }
-keep interface de.blinkt.openvpn.** { *; }
-keep enum de.blinkt.openvpn.** { *; }

# VPN Service and Receivers
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application

# Method channels - Critical for Flutter communication
-keepclassmembers class * {
    @io.flutter.plugin.common.MethodChannel$* *;
}

# Event channels - Critical for real-time updates
-keepclassmembers class * {
    @io.flutter.plugin.common.EventChannel$* *;
}

# Keep native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# VPN Configuration parsing
-keepclassmembers class * {
    public <init>(...);
}

# Enum support
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# Serialization support
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Parcelable support
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep generic signatures for reflection
-keepattributes Signature

# Keep exceptions for better crash reports
-keepattributes Exceptions

# Network security
-keep class javax.net.ssl.** { *; }
-keep class org.apache.http.** { *; }
-keep interface org.apache.http.** { *; }

# Security providers
-keep class org.spongycastle.** { *; }
-keep interface org.spongycastle.** { *; }

# Conscrypt (if used)
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# OpenJSSE
-dontwarn org.openjsse.**

# Kotlin Coroutines (if used in future)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Suppress warnings
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe
-dontwarn com.google.common.**

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# 16 KB Page Size Support
# Keep memory-aligned classes
-keep,allowobfuscation class * {
    <init>(...);
}

# Ensure proper native library loading
-keep class java.lang.System {
    public static void loadLibrary(java.lang.String);
    public static void load(java.lang.String);
}

# AndroidX support
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Google Play Core
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**
