# AxeVPN Flutter Plugin - Consumer ProGuard Rules
# These rules will be automatically applied to apps using this plugin

# Keep VPN service classes
-keep class com.axevpn.flutter.openvpn.** { *; }

# Keep OpenVPN library classes
-keep class de.blinkt.openvpn.** { *; }
-keep interface de.blinkt.openvpn.** { *; }

# Keep VPN-specific enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Flutter plugin registrar
-keep class io.flutter.embedding.engine.plugins.FlutterPlugin { *; }
-keep class io.flutter.embedding.engine.plugins.activity.ActivityAware { *; }
-keep class io.flutter.plugin.common.** { *; }

# Keep method channel names for reflection
-keepclassmembers class com.axevpn.flutter.openvpn.AxeVPNFlutterPlugin {
    public static final java.lang.String CHANNEL_VPN_STAGE;
    public static final java.lang.String CHANNEL_VPN_CONTROL;
}

# Prevent obfuscation of native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep VPN callback interfaces
-keep interface com.axevpn.flutter.openvpn.VPNHelper { *; }

# Keep VPN connection result classes
-keepclassmembers class * {
    @com.axevpn.flutter.openvpn.** *;
}

# Suppress warnings for optional dependencies
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Optimize but don't remove VPN logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
