package com.axevpn.flutter.v2ray;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import libv2ray.Libv2ray;
import libv2ray.V2RayPoint;
import libv2ray.V2RayVPNServiceSupportsSet;

/**
 * Android VpnService – V2Ray/Xray core integration via libv2ray.aar (Go mobile binding).
 *
 * Architecture:
 *  1. VpnService.Builder establishes a TUN interface (26.26.26.1/30).
 *  2. Xray-core runs in-process via Libv2ray.newV2RayPoint(); it opens a SOCKS5
 *     listener on 127.0.0.1:10808.
 *  3. libtun2socks.so bridges the TUN fd to the SOCKS5 proxy via a Unix socket.
 *
 * Version: 3.0.0
 */
public class AxeV2RayVpnService extends VpnService {

    private static final String TAG = "AxeV2RayVpnService";
    private static final int NOTIFICATION_ID = 9001;
    private static final String NOTIFICATION_CHANNEL_ID = "axevpn_v2ray_channel";
    private static final int SOCKS5_PORT = 10808;

    public static final String ACTION_CONNECT    = "com.axevpn.flutter.v2ray.CONNECT";
    public static final String ACTION_DISCONNECT = "com.axevpn.flutter.v2ray.DISCONNECT";
    public static final String EXTRA_CONFIG_JSON = "config_json";
    public static final String EXTRA_TUNNEL_NAME = "tunnel_name";

    /** Broadcast sent to V2RayFlutterPlugin whenever the tunnel stage changes. */
    public static final String BROADCAST_STAGE = "com.axevpn.flutter.v2ray.STAGE";
    public static final String EXTRA_STAGE = "stage";

    private ParcelFileDescriptor tunInterface;
    private Process tun2socksProcess;
    private volatile boolean running = false;

    // The single V2RayPoint for this service instance.
    private V2RayPoint v2RayPoint;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialise the Go-mobile V2RayPoint once per service instance.
        v2RayPoint = Libv2ray.newV2RayPoint(new V2RayVPNServiceSupportsSet() {
            @Override
            public long shutdown() {
                stopTunnel();
                return 0;
            }
            @Override
            public long prepare() { return 0; }
            @Override
            public boolean protect(long socket) {
                return AxeV2RayVpnService.this.protect((int) socket);
            }
            @Override
            public long onEmitStatus(long l, String s) { return 0; }
            @Override
            public long setup(String s) { return 0; }
        }, Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1);

        // Copy geosite/geoip assets to external storage so Xray-core can read them.
        copyGeoAssets();

        // Init the Xray env (assets path, empty env string).
        Libv2ray.initV2Env(getGeoAssetsPath(), "");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            String configJson = intent.getStringExtra(EXTRA_CONFIG_JSON);
            String tunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME);
            if (configJson != null) {
                startTunnel(configJson, tunnelName != null ? tunnelName : "V2Ray VPN");
            } else {
                stopSelf();
            }
        } else if (ACTION_DISCONNECT.equals(action)) {
            stopTunnel();
            stopSelf();
        }

        return START_STICKY;
    }

    // ── Tunnel lifecycle ───────────────────────────────────────────────────────

    private void startTunnel(String configJson, String tunnelName) {
        broadcastStage("preparing");
        startForeground(NOTIFICATION_ID, buildNotification(tunnelName));

        // ── Step 1: parse DNS from the JSON config ──
        String[] dnsServers = new String[]{"1.1.1.1", "8.8.8.8"};
        try {
            JSONObject json = new JSONObject(configJson);
            JSONObject dnsObj = json.optJSONObject("dns");
            if (dnsObj != null) {
                JSONArray arr = dnsObj.optJSONArray("servers");
                if (arr != null && arr.length() > 0) {
                    dnsServers = new String[arr.length()];
                    for (int i = 0; i < arr.length(); i++) {
                        dnsServers[i] = arr.getString(i);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "DNS parse failed, using defaults", e);
        }

        // ── Step 2: establish TUN interface ──
        try {
            Builder builder = new Builder();
            builder.setSession(tunnelName)
                   .setMtu(1500)
                   .addAddress("26.26.26.1", 30)
                   .addRoute("0.0.0.0", 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false);
            }
            for (String dns : dnsServers) {
                try { builder.addDnsServer(dns); } catch (Exception ignored) {}
            }
            tunInterface = builder.establish();
            if (tunInterface == null) {
                Log.e(TAG, "Failed to establish TUN interface");
                broadcastStage("error");
                stopForeground(true);
                stopSelf();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "TUN interface error", e);
            broadcastStage("error");
            stopForeground(true);
            stopSelf();
            return;
        }

        // ── Step 3: start Xray core ──
        broadcastStage("connecting");
        try {
            v2RayPoint.setConfigureFileContent(configJson);
            v2RayPoint.setDomainName("v2ray_axevpn");
            v2RayPoint.runLoop(false); // false = core only (tun2socks handles TUN)
            running = true;
            broadcastStage("connected");
        } catch (Exception e) {
            Log.e(TAG, "Xray core start failed", e);
            broadcastStage("error");
            closeTun();
            stopForeground(true);
            stopSelf();
            return;
        }

        // ── Step 4: start tun2socks ──
        runTun2socks();
    }

    private void runTun2socks() {
        File tun2socksLib = new File(getApplicationInfo().nativeLibraryDir, "libtun2socks.so");
        if (!tun2socksLib.exists()) {
            Log.e(TAG, "libtun2socks.so not found at " + tun2socksLib.getAbsolutePath());
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                tun2socksLib.getAbsolutePath(),
                "--netif-ipaddr",    "26.26.26.2",
                "--netif-netmask",   "255.255.255.252",
                "--socks-server-addr", "127.0.0.1:" + SOCKS5_PORT,
                "--tunmtu",          "1500",
                "--sock-path",       "sock_path",
                "--enable-udprelay",
                "--loglevel",        "error"
            );
            pb.redirectErrorStream(true);
            tun2socksProcess = pb.directory(getFilesDir()).start();

            // Monitor tun2socks and restart if it dies while the tunnel is active.
            new Thread(() -> {
                try {
                    tun2socksProcess.waitFor();
                    if (running) {
                        Log.w(TAG, "tun2socks exited unexpectedly, restarting");
                        runTun2socks();
                    }
                } catch (InterruptedException ignored) {}
            }, "tun2socks-monitor").start();

            sendTunFd();
        } catch (Exception e) {
            Log.e(TAG, "tun2socks start failed", e);
        }
    }

    /** Pass the TUN file descriptor to tun2socks via the Unix socket. */
    private void sendTunFd() {
        String sockPath = new File(getFilesDir(), "sock_path").getAbsolutePath();
        FileDescriptor tunFd = tunInterface.getFileDescriptor();
        new Thread(() -> {
            for (int tries = 0; tries < 10; tries++) {
                try {
                    Thread.sleep(50L * (tries + 1));
                    LocalSocket client = new LocalSocket();
                    client.connect(new LocalSocketAddress(sockPath, LocalSocketAddress.Namespace.FILESYSTEM));
                    OutputStream out = client.getOutputStream();
                    client.setFileDescriptorsForSend(new FileDescriptor[]{tunFd});
                    out.write(32);
                    client.setFileDescriptorsForSend(null);
                    out.flush();
                    client.close();
                    Log.i(TAG, "TUN fd sent to tun2socks");
                    return;
                } catch (Exception e) {
                    Log.w(TAG, "sendTunFd attempt " + (tries + 1) + " failed: " + e.getMessage());
                }
            }
            Log.e(TAG, "Failed to send TUN fd to tun2socks after 10 tries");
        }, "send-tun-fd").start();
    }

    private void stopTunnel() {
        running = false;
        broadcastStage("disconnecting");
        try {
            if (v2RayPoint != null) {
                v2RayPoint.stopLoop();
            }
        } catch (Exception e) {
            Log.e(TAG, "stopLoop failed", e);
        }
        if (tun2socksProcess != null) {
            tun2socksProcess.destroy();
            tun2socksProcess = null;
        }
        closeTun();
        stopForeground(true);
        broadcastStage("disconnected");
        Log.i(TAG, "V2Ray tunnel stopped");
    }

    private void closeTun() {
        if (tunInterface != null) {
            try { tunInterface.close(); } catch (Exception ignored) {}
            tunInterface = null;
        }
    }

    @Override
    public void onRevoke() {
        stopTunnel();
        stopSelf();
        super.onRevoke();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Stage broadcast helper ─────────────────────────────────────────────────
    private void broadcastStage(String stage) {
        Intent i = new Intent(BROADCAST_STAGE);
        i.putExtra(EXTRA_STAGE, stage);
        sendBroadcast(i);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Returns the external-storage path used by Xray for geosite/geoip files. */
    private String getGeoAssetsPath() {
        File extDir = getExternalFilesDir("assets");
        if (extDir != null && (extDir.exists() || extDir.mkdirs())) {
            return extDir.getAbsolutePath();
        }
        return getDir("assets", Context.MODE_PRIVATE).getAbsolutePath();
    }

    /** Copies geosite.dat and geoip.dat from the APK assets to external storage. */
    private void copyGeoAssets() {
        String outDir = getGeoAssetsPath();
        String[] geoFiles = {"geosite.dat", "geoip.dat"};
        for (String name : geoFiles) {
            try {
                File outFile = new File(outDir, name);
                if (outFile.exists()) continue; // already copied
                InputStream in = getAssets().open(name);
                OutputStream out = new java.io.FileOutputStream(outFile);
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                in.close();
                out.close();
            } catch (IOException e) {
                Log.w(TAG, "copyGeoAssets: " + name + " not found in assets (optional)", e);
            }
        }
    }

    private Notification buildNotification(String title) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "V2Ray VPN Service",
                NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("V2Ray tunnel active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
}
