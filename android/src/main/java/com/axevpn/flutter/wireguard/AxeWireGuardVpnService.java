package com.axevpn.flutter.wireguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.wireguard.config.Config;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;

import java.net.InetAddress;

/**
 * Actual VPN Service that creates the VPN tunnel using Android VpnService API.
 * This is what GoBackend uses to create the VPN interface.
 *
 * IMPORTANT: This service is started via startForegroundService() and MUST call
 * startForeground() within 5 seconds of onStartCommand() to avoid
 * ForegroundServiceDidNotStartInTimeException on Android 8+.
 */
public class AxeWireGuardVpnService extends VpnService {
    private static final String TAG = "AxeWireGuardVpnService";

    // Share the same channel ID as AxeWireGuardService so only one channel is created.
    private static final String NOTIFICATION_CHANNEL_ID = "wireguard_vpn_channel";
    // Use a distinct notification ID from AxeWireGuardService (which uses 1001).
    private static final int NOTIFICATION_ID = 1002;

    public static final String ACTION_CONNECT = "com.axevpn.wireguard.vpn.CONNECT";
    public static final String ACTION_DISCONNECT = "com.axevpn.wireguard.vpn.DISCONNECT";
    public static final String EXTRA_CONFIG = "config";
    public static final String EXTRA_TUNNEL_NAME = "tunnelName";

    private static AxeWireGuardVpnService instance;
    private ParcelFileDescriptor vpnInterface;
    private String currentTunnelName;

    public static AxeWireGuardVpnService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ensureNotificationChannel();
        android.util.Log.d(TAG, "VPN Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // CRITICAL FIX: Call startForeground() IMMEDIATELY, before any other work.
        // Android 8+ requires this within ~5 s of startForegroundService(); failure
        // raises ForegroundServiceDidNotStartInTimeException and crashes the app.
        ensureNotificationChannel();
        startForeground(NOTIFICATION_ID, buildForegroundNotification(
                currentTunnelName != null ? currentTunnelName : "AxeVPN"));
        android.util.Log.i(TAG, "startForeground() called immediately in onStartCommand");

        if (intent != null) {
            final String action = intent.getAction();
            android.util.Log.d(TAG, "onStartCommand action: " + action);

            if (ACTION_CONNECT.equals(action)) {
                final String configStr = intent.getStringExtra(EXTRA_CONFIG);
                currentTunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME);
                if (currentTunnelName == null) {
                    currentTunnelName = "AxeVPN";
                }

                // Re-issue startForeground with the correct tunnel name now that we have it.
                startForeground(NOTIFICATION_ID, buildForegroundNotification(currentTunnelName));

                if (configStr != null) {
                    try {
                        android.util.Log.i(TAG, "Starting VPN tunnel: " + currentTunnelName);
                        Config config = Config.parse(new java.io.ByteArrayInputStream(
                                configStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        establishVpnInterface(config);
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "Failed to parse WireGuard config", e);
                        stopSelf();
                    }
                } else {
                    android.util.Log.e(TAG, "ACTION_CONNECT received but no config provided");
                    stopSelf();
                }

            } else if (ACTION_DISCONNECT.equals(action)) {
                android.util.Log.i(TAG, "Disconnect action received");
                disconnect();
            } else {
                android.util.Log.w(TAG, "Unknown action: " + action + " — stopping service");
                stopSelf();
            }
        } else {
            // Null intent means the service was restarted by the OS after being killed.
            // There is no tunnel to restore here; stop cleanly.
            android.util.Log.w(TAG, "onStartCommand received null intent (OS restart) — stopping");
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void establishVpnInterface(Config config) {
        try {
            final Interface iface = config.getInterface();
            final Builder builder = new Builder();

            for (InetNetwork addr : iface.getAddresses()) {
                builder.addAddress(addr.getAddress(), addr.getMask());
                android.util.Log.d(TAG, "Added address: " + addr.getAddress() + "/" + addr.getMask());
            }

            for (InetAddress dns : iface.getDnsServers()) {
                builder.addDnsServer(dns);
                android.util.Log.d(TAG, "Added DNS: " + dns);
            }

            for (Peer peer : config.getPeers()) {
                for (InetNetwork allowedIp : peer.getAllowedIps()) {
                    builder.addRoute(allowedIp.getAddress(), allowedIp.getMask());
                    android.util.Log.d(TAG, "Added route: " + allowedIp.getAddress() + "/" + allowedIp.getMask());
                }
            }

            if (iface.getMtu().isPresent()) {
                builder.setMtu(iface.getMtu().get());
                android.util.Log.d(TAG, "Set MTU: " + iface.getMtu().get());
            } else {
                builder.setMtu(1280);
            }

            builder.setSession(currentTunnelName);
            builder.setBlocking(false);
            // Exclude the VPN app itself so its own traffic (ads, API calls)
            // bypasses the tunnel and reaches the internet directly.
            try { builder.addDisallowedApplication(getPackageName()); }
            catch (Exception ignored) {}

            if (vpnInterface != null) {
                try {
                    vpnInterface.close();
                } catch (Exception e) {
                    android.util.Log.w(TAG, "Error closing old VPN interface", e);
                }
            }

            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                android.util.Log.e(TAG, "Failed to establish VPN interface — permission denied?");
                stopSelf();
                return;
            }

            android.util.Log.i(TAG, "VPN interface established successfully (fd=" + vpnInterface.getFd() + ")");

        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to establish VPN", e);
            stopSelf();
        }
    }

    private void disconnect() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                android.util.Log.i(TAG, "VPN interface closed");
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error closing VPN interface", e);
            }
            vpnInterface = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception ignored) {}
            vpnInterface = null;
        }
        instance = null;
        android.util.Log.d(TAG, "VPN Service destroyed");
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        android.util.Log.w(TAG, "VPN permission revoked by system");
        disconnect();
        super.onRevoke();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Notification helpers ────────────────────────────────────────────────

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                final NotificationChannel channel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "WireGuard VPN",
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("WireGuard VPN connection status");
                channel.setShowBadge(false);
                channel.enableVibration(false);
                channel.setSound(null, null);
                nm.createNotificationChannel(channel);
                android.util.Log.d(TAG, "Notification channel created");
            }
        }
    }

    private Notification buildForegroundNotification(String tunnelName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent == null) launchIntent = new Intent();
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        final int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;

        final PendingIntent contentIntent = PendingIntent.getActivity(this, 0, launchIntent, piFlags);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("WireGuard VPN")
                .setContentText(tunnelName != null ? tunnelName : "Connecting…")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .build();
    }
}
