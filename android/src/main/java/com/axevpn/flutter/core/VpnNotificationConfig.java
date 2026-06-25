package com.axevpn.flutter.core;

import android.content.Context;

/**
 * Shared, white-label-safe notification branding for every VPN service in this plugin
 * (WireGuard, V2Ray, OpenVPN, OpenConnect).
 *
 * Buyers configure this once from Dart via setNotificationConfig(...) on any engine
 * (OpenVPN, WireGuard, V2Ray, OpenConnect — they all write into this same static holder).
 * If a buyer never calls it, every fallback below resolves to the host app's own label
 * (AndroidManifest android:label / strings.xml app_name) — never a literal "AxeVPN".
 */
public final class VpnNotificationConfig {

    private static volatile String appName;
    private static volatile String connectedTitle;
    private static volatile String connectedSubtitle;
    private static volatile String channelName;
    private static volatile String channelDescription;

    private VpnNotificationConfig() {}

    public static void configure(
            String appName,
            String connectedTitle,
            String connectedSubtitle,
            String channelName,
            String channelDescription) {
        VpnNotificationConfig.appName = nullIfEmpty(appName);
        VpnNotificationConfig.connectedTitle = nullIfEmpty(connectedTitle);
        VpnNotificationConfig.connectedSubtitle = nullIfEmpty(connectedSubtitle);
        VpnNotificationConfig.channelName = nullIfEmpty(channelName);
        VpnNotificationConfig.channelDescription = nullIfEmpty(channelDescription);
    }

    /** The host app's display name — buyer-configured value, or the Android app label. Never "AxeVPN". */
    public static String appName(Context context) {
        if (appName != null) return appName;
        return hostAppLabel(context);
    }

    /** Notification title for an active tunnel, e.g. "WireGuard Connected". */
    public static String connectedTitle(Context context, String protocolDefaultTitle) {
        if (connectedTitle != null) return connectedTitle;
        return protocolDefaultTitle;
    }

    /**
     * Notification subtitle/body for an active tunnel. Prefers, in order: buyer-configured
     * subtitle, the live tunnel/server name passed by the caller, then the host app label.
     * Never "AxeVPN".
     */
    public static String connectedSubtitle(Context context, String tunnelNameOrNull) {
        if (connectedSubtitle != null) return connectedSubtitle;
        if (tunnelNameOrNull != null && !tunnelNameOrNull.isEmpty()) return tunnelNameOrNull;
        return hostAppLabel(context);
    }

    /** Default tunnel/session name when the Flutter caller doesn't supply one. */
    public static String defaultTunnelName(Context context) {
        return appName(context);
    }

    public static String channelName(String protocolDefaultName) {
        return channelName != null ? channelName : protocolDefaultName;
    }

    public static String channelDescription(String protocolDefaultDescription) {
        return channelDescription != null ? channelDescription : protocolDefaultDescription;
    }

    private static String hostAppLabel(Context context) {
        try {
            return context.getApplicationInfo()
                    .loadLabel(context.getPackageManager())
                    .toString();
        } catch (Exception e) {
            return "VPN";
        }
    }

    private static String nullIfEmpty(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value;
    }
}
