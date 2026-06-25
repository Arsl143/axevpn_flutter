package com.axevpn.flutter.wireguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Foreground Service for WireGuard VPN notification.
 * Provides a persistent notification while the VPN tunnel is active.
 *
 * CRITICAL: startForeground() is called at the very top of onStartCommand()
 * (before branching on the intent action) to satisfy Android 8+'s 5-second
 * deadline regardless of which action — or null intent — arrives.
 */
public class AxeWireGuardService extends Service {
    private static final String TAG = "AxeWireGuardService";
    private static final String NOTIFICATION_CHANNEL_ID = "wireguard_vpn_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "com.axevpn.wireguard.START";
    public static final String ACTION_STOP = "com.axevpn.wireguard.STOP";
    public static final String ACTION_DISCONNECT = "com.axevpn.wireguard.DISCONNECT";
    public static final String EXTRA_TUNNEL_NAME = "tunnelName";

    private static AxeWireGuardService instance;
    // null, not a literal brand name — buildNotification() falls back to the
    // buyer-configured/app-label name via VpnNotificationConfig when this is unset.
    private String currentTunnelName = null;
    private DisconnectCallback disconnectCallback;

    public interface DisconnectCallback {
        void onDisconnectRequested();
    }

    public static AxeWireGuardService getInstance() {
        return instance;
    }

    public void setDisconnectCallback(DisconnectCallback callback) {
        this.disconnectCallback = callback;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        android.util.Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // CRITICAL FIX: startForeground() must be called unconditionally at the top of
        // onStartCommand() so that the Android 8+ foreground-service timeout is met
        // regardless of intent action or whether the OS restarted the service with a
        // null intent. We update the notification text when ACTION_START arrives below.
        startForeground(NOTIFICATION_ID, buildNotification(currentTunnelName));
        android.util.Log.i(TAG, "startForeground() called unconditionally at onStartCommand start");

        if (intent != null) {
            final String action = intent.getAction();
            android.util.Log.d(TAG, "Action: " + action);

            if (ACTION_START.equals(action)) {
                final String name = intent.getStringExtra(EXTRA_TUNNEL_NAME);
                if (name != null) {
                    currentTunnelName = name;
                }
                // Update the notification with the real tunnel name.
                updateNotification(currentTunnelName);
                android.util.Log.i(TAG, "Notification started for tunnel: " + currentTunnelName);

            } else if (ACTION_DISCONNECT.equals(action)) {
                android.util.Log.i(TAG, "Disconnect action received from notification");
                if (disconnectCallback != null) {
                    disconnectCallback.onDisconnectRequested();
                } else {
                    // Fallback: send disconnect intent directly to VPN service.
                    try {
                        final Intent vpnDisconnect = new Intent(this, AxeWireGuardVpnService.class);
                        vpnDisconnect.setAction(AxeWireGuardVpnService.ACTION_DISCONNECT);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(vpnDisconnect);
                        } else {
                            startService(vpnDisconnect);
                        }
                        android.util.Log.i(TAG, "Fallback disconnect sent to AxeWireGuardVpnService");
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "Fallback disconnect failed", e);
                    }
                }
                stopNotification();

            } else if (ACTION_STOP.equals(action)) {
                stopNotification();

            } else {
                android.util.Log.w(TAG, "Unknown action received: " + action);
            }
        } else {
            // OS restarted the service with a null intent (START_NOT_STICKY shouldn't
            // cause this, but guard defensively). Stop cleanly.
            android.util.Log.w(TAG, "Null intent in onStartCommand — stopping service");
            stopNotification();
        }

        return START_NOT_STICKY;
    }

    private void updateNotification(String tunnelName) {
        try {
            final NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, buildNotification(tunnelName));
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to update notification", e);
        }
    }

    private void stopNotification() {
        try {
            stopForeground(true);
            stopSelf();
            android.util.Log.i(TAG, "Notification stopped");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error stopping notification", e);
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        disconnectCallback = null;
        android.util.Log.d(TAG, "Service destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return;

            final NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    com.axevpn.flutter.core.VpnNotificationConfig.channelName("WireGuard VPN"),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(
                    com.axevpn.flutter.core.VpnNotificationConfig.channelDescription(
                            "WireGuard VPN connection status"));
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            nm.createNotificationChannel(channel);
            android.util.Log.d(TAG, "Notification channel created");
        }
    }

    private Notification buildNotification(String tunnelName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent == null) launchIntent = new Intent();
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        final int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;

        final PendingIntent contentPi = PendingIntent.getActivity(this, 0, launchIntent, piFlags);

        final Intent disconnectIntent = new Intent(this, AxeWireGuardService.class);
        disconnectIntent.setAction(ACTION_DISCONNECT);
        final PendingIntent disconnectPi = PendingIntent.getService(
                this, 1, disconnectIntent, piFlags);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(com.axevpn.flutter.core.VpnNotificationConfig.connectedTitle(
                        this, "WireGuard Connected"))
                .setContentText(com.axevpn.flutter.core.VpnNotificationConfig.connectedSubtitle(
                        this, tunnelName))
                .setContentIntent(contentPi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Disconnect",
                        disconnectPi)
                .build();
    }
}
