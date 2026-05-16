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
 * Foreground Service for WireGuard VPN notification
 * Provides persistent notification while VPN is connected
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
    private String currentTunnelName = "AxeVPN";
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
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_START.equals(action)) {
                currentTunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME);
                if (currentTunnelName == null) {
                    currentTunnelName = "AxeVPN";
                }
                startNotification();
                android.util.Log.i(TAG, "Starting notification for: " + currentTunnelName);

            } else if (ACTION_DISCONNECT.equals(action)) {
                android.util.Log.i(TAG, "Disconnect action received from notification");
                if (disconnectCallback != null) {
                    disconnectCallback.onDisconnectRequested();
                } else {
                    // Fallback path: callback may be null after process/service recreation.
                    // Always request tunnel teardown directly from the VPN service.
                    try {
                        Intent vpnDisconnectIntent = new Intent(this, AxeWireGuardVpnService.class);
                        vpnDisconnectIntent.setAction(AxeWireGuardVpnService.ACTION_DISCONNECT);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(vpnDisconnectIntent);
                        } else {
                            startService(vpnDisconnectIntent);
                        }
                        android.util.Log.i(TAG, "Fallback disconnect sent to AxeWireGuardVpnService");
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "Fallback disconnect failed", e);
                    }
                }
                stopNotification();
            } else if (ACTION_STOP.equals(action)) {
                stopNotification();
            }
        }

        return START_NOT_STICKY;
    }

    private void startNotification() {
        try {
            Notification notification = createNotification(currentTunnelName);
            startForeground(NOTIFICATION_ID, notification);
            android.util.Log.i(TAG, "Foreground notification started");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to start notification", e);
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
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "WireGuard VPN",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("WireGuard VPN connection status");
            channel.setShowBadge(true);
            channel.enableVibration(false);
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                android.util.Log.d(TAG, "Notification channel created");
            }
        }
    }

    private Notification createNotification(String tunnelName) {
        // Launch app intent
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) {
            intent = new Intent();
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Disconnect action
        Intent disconnectIntent = new Intent(this, AxeWireGuardService.class);
        disconnectIntent.setAction(ACTION_DISCONNECT);
        PendingIntent disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload) // VPN icon
            .setContentTitle("WireGuard Connected")
            .setContentText(tunnelName)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectPendingIntent
            );

        return builder.build();
    }
}
