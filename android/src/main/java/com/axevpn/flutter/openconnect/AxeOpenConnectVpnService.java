package com.axevpn.flutter.openconnect;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Android VpnService — pure-Java Cisco AnyConnect/OpenConnect SSL VPN client.
 *
 * Protocol (CSTP over TLS):
 *  1. POST /+webvpn+/index.html  – username/password authentication
 *  2. CONNECT /CSCOSSLC/tunnel  – open CSTP data channel
 *  3. Parse X-CSTP-* response headers for VPN IP config
 *  4. Establish TUN interface; forward packets via 8-byte CSTP framing
 *
 * No native binary required — runs entirely in the Java sandbox.
 *
 * Version: 3.0.0
 */
public class AxeOpenConnectVpnService extends VpnService {

    private static final String TAG = "AxeOpenConnect";
    private static final int NOTIFICATION_ID = 9002;
    private static final String CHANNEL_ID = "axevpn_oc_channel";

    // CSTP packet types (byte 4 of the 8-byte CSTP header)
    private static final int CSTP_DATA       = 0x00;
    private static final int CSTP_DPD_REQ    = 0x03;
    private static final int CSTP_DPD_RESP   = 0x04;
    private static final int CSTP_DISCONNECT = 0x07;
    private static final int CSTP_HDR        = 8; // header size in bytes

    public static final String ACTION_CONNECT    = "com.axevpn.flutter.openconnect.CONNECT";
    public static final String ACTION_DISCONNECT = "com.axevpn.flutter.openconnect.DISCONNECT";

    public static final String EXTRA_SERVER_URL    = "server_url";
    public static final String EXTRA_TUNNEL_NAME   = "tunnel_name";
    public static final String EXTRA_USERNAME      = "username";
    public static final String EXTRA_PASSWORD      = "password";
    public static final String EXTRA_AUTH_GROUP    = "auth_group";
    public static final String EXTRA_SERVERCERT    = "servercert";
    public static final String EXTRA_CERT_PATH     = "cert_path";
    public static final String EXTRA_CERT_PASSWORD = "cert_password";
    public static final String EXTRA_CA_PATH       = "ca_path";

    /** Broadcast sent to OpenConnectFlutterPlugin on every stage change. */
    public static final String BROADCAST_STAGE = "com.axevpn.flutter.openconnect.STAGE";
    public static final String EXTRA_STAGE     = "stage";

    private ParcelFileDescriptor tunInterface;
    private volatile boolean     running      = false;
    private SSLSocket            tunnelSocket;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            String serverUrl  = intent.getStringExtra(EXTRA_SERVER_URL);
            String name       = intent.getStringExtra(EXTRA_TUNNEL_NAME);
            String username   = intent.getStringExtra(EXTRA_USERNAME);
            String password   = intent.getStringExtra(EXTRA_PASSWORD);
            String authGroup  = intent.getStringExtra(EXTRA_AUTH_GROUP);
            String servercert = intent.getStringExtra(EXTRA_SERVERCERT);
            if (serverUrl != null && username != null && password != null) {
                new Thread(() -> connectToServer(serverUrl, name, username,
                        password, authGroup, servercert), "oc-connect").start();
            } else {
                stopSelf();
            }
        } else if (ACTION_DISCONNECT.equals(action)) {
            shutdown();
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onRevoke() { shutdown(); stopSelf(); super.onRevoke(); }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Connection flow ────────────────────────────────────────────────────────

    private void connectToServer(String serverUrl, String name, String username,
                                  String password, String authGroup, String servercert) {
        broadcastStage("preparing");
        SSLSocket authSock = null;
        SSLSocket tunSock  = null;
        try {
            // ── Parse host:port from URL ──
            String host;
            int    port;
            String work = serverUrl;
            if (work.startsWith("https://")) work = work.substring(8);
            else if (work.startsWith("http://")) work = work.substring(7);
            int slash = work.indexOf('/');
            String hostPort = slash >= 0 ? work.substring(0, slash) : work;
            int colon = hostPort.lastIndexOf(':');
            if (colon >= 0) {
                host = hostPort.substring(0, colon);
                port = Integer.parseInt(hostPort.substring(colon + 1));
            } else {
                host = hostPort;
                port = 443;
            }

            SSLSocketFactory ssf = buildSslSocketFactory(servercert);

            // ── Phase 1: Authenticate ──
            broadcastStage("authenticating");
            authSock = (SSLSocket) ssf.createSocket(host, port);
            protect(authSock);
            authSock.startHandshake();

            String body = "username=" + URLEncoder.encode(username, "UTF-8")
                        + "&password=" + URLEncoder.encode(password, "UTF-8");
            if (authGroup != null && !authGroup.isEmpty())
                body += "&group_list=" + URLEncoder.encode(authGroup, "UTF-8");

            String authReq = "POST /+webvpn+/index.html HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "User-Agent: AnyConnect Android 4.10.04065\r\n"
                    + "X-AnyConnect-Platform: android\r\n"
                    + "X-Aggregate-Auth: 1\r\n"
                    + "X-AnyConnect-Identifier-ClientVersion: 4.10.04065\r\n"
                    + "Content-Type: application/x-www-form-urlencoded\r\n"
                    + "Content-Length: " + body.getBytes("UTF-8").length + "\r\n"
                    + "Connection: close\r\n\r\n"
                    + body;

            OutputStream authOs = authSock.getOutputStream();
            authOs.write(authReq.getBytes("UTF-8"));
            authOs.flush();

            String sessionCookie = parseAuthCookie(authSock.getInputStream());
            safeClose(authSock);
            authSock = null;

            if (sessionCookie == null || sessionCookie.isEmpty()) {
                Log.e(TAG, "Authentication failed — no session cookie received");
                broadcastStage("denied");
                stopSelf();
                return;
            }

            // ── Phase 2: Open CSTP tunnel ──
            broadcastStage("connecting");
            tunSock = (SSLSocket) ssf.createSocket(host, port);
            protect(tunSock);
            tunSock.startHandshake();

            String connectReq = "CONNECT /CSCOSSLC/tunnel HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Cookie: " + sessionCookie + "\r\n"
                    + "X-CSTP-Version: 1\r\n"
                    + "X-CSTP-Hostname: android-axevpn\r\n"
                    + "X-CSTP-Accept-Encoding: lzs,deflate\r\n"
                    + "X-CSTP-MTU: 1500\r\n"
                    + "X-CSTP-Address-Type: IPv4\r\n"
                    + "X-CSTP-Default-Domain: \r\n"
                    + "\r\n";

            OutputStream tos = tunSock.getOutputStream();
            tos.write(connectReq.getBytes("UTF-8"));
            tos.flush();

            VpnTunnelConfig cfg = parseTunnelHeaders(tunSock.getInputStream());
            if (cfg == null) {
                Log.e(TAG, "Tunnel setup failed — no VPN config in CONNECT response");
                broadcastStage("error");
                safeClose(tunSock);
                stopSelf();
                return;
            }

            // ── Phase 3: TUN interface ──
            tunInterface = buildVpnInterface(cfg, name);
            if (tunInterface == null) {
                Log.e(TAG, "TUN interface build failed");
                broadcastStage("error");
                safeClose(tunSock);
                stopSelf();
                return;
            }

            // ── Phase 4: Forward packets ──
            this.tunnelSocket = tunSock;
            running = true;
            startForeground(NOTIFICATION_ID, buildNotification(name != null ? name : "OpenConnect VPN"));
            broadcastStage("connected");
            forwardPackets(tunSock.getInputStream(), tos, cfg.mtu > 0 ? cfg.mtu : 1406);

        } catch (Exception e) {
            Log.e(TAG, "Connection error", e);
            broadcastStage("error");
        } finally {
            safeClose(authSock);
            safeClose(tunSock);
            shutdown();
            stopSelf();
        }
    }

    // ── SSL socket factory (with optional SHA-256 certificate pinning) ──────────

    private SSLSocketFactory buildSslSocketFactory(final String servercert) {
        final byte[] pin = parseCertPin(servercert);
        TrustManager[] tms = new TrustManager[]{new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] c, String a) {}
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws java.security.cert.CertificateException {
                if (pin == null) return; // accept any cert when no pin provided
                for (X509Certificate cert : chain) {
                    try {
                        byte[] fp = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
                        if (Arrays.equals(fp, pin)) return;
                    } catch (Exception e) {
                        throw new java.security.cert.CertificateException(e);
                    }
                }
                throw new java.security.cert.CertificateException("Certificate pin mismatch");
            }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tms, new SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            Log.e(TAG, "SSL factory failed, falling back to default", e);
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
    }

    /** Parse pin-sha256:BASE64 or 64-hex-char fingerprint → raw bytes, or null. */
    private byte[] parseCertPin(String servercert) {
        if (servercert == null || servercert.isEmpty()) return null;
        try {
            if (servercert.startsWith("pin-sha256:"))
                return android.util.Base64.decode(servercert.substring(11), android.util.Base64.DEFAULT);
            String hex = servercert.replace(":", "").replace(" ", "");
            if (hex.length() == 64) {
                byte[] b = new byte[32];
                for (int i = 0; i < 32; i++)
                    b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                return b;
            }
        } catch (Exception e) {
            Log.w(TAG, "Ignoring unparseable servercert: " + servercert);
        }
        return null;
    }

    // ── Auth response ──────────────────────────────────────────────────────────

    /** Read HTTP response from auth POST; return concatenated session cookies or null. */
    private String parseAuthCookie(InputStream is) throws IOException {
        String statusLine = readLine(is);
        if (statusLine == null) return null;
        int code = -1;
        String[] parts = statusLine.split(" ", 3);
        if (parts.length >= 2) { try { code = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {} }
        if (code != 200) { drainAll(is); Log.e(TAG, "Auth HTTP " + code); return null; }

        StringBuilder cookies = new StringBuilder();
        String line;
        while ((line = readLine(is)) != null && !line.isEmpty()) {
            if (line.toLowerCase(Locale.US).startsWith("set-cookie:")) {
                String cookie = line.substring(11).trim();
                int sc = cookie.indexOf(';');
                if (sc >= 0) cookie = cookie.substring(0, sc).trim();
                if (cookies.length() > 0) cookies.append("; ");
                cookies.append(cookie);
            }
        }
        drainAll(is);
        return cookies.length() > 0 ? cookies.toString() : null;
    }

    // ── Tunnel CONNECT response ────────────────────────────────────────────────

    private static class VpnTunnelConfig {
        String address;
        String netmask = "255.255.0.0";
        int    prefix  = 16;
        String dns;
        String dns2;
        int    mtu     = 1406;
    }

    private VpnTunnelConfig parseTunnelHeaders(InputStream is) throws IOException {
        String status = readLine(is);
        if (status == null || !status.contains("200")) {
            Log.e(TAG, "CONNECT failed: " + status);
            drainAll(is);
            return null;
        }
        VpnTunnelConfig cfg = new VpnTunnelConfig();
        String line;
        while ((line = readLine(is)) != null && !line.isEmpty()) {
            String lower = line.toLowerCase(Locale.US);
            if (lower.startsWith("x-cstp-address:"))
                cfg.address = line.substring(15).trim();
            else if (lower.startsWith("x-cstp-netmask:")) {
                cfg.netmask = line.substring(15).trim();
                cfg.prefix  = maskToPrefix(cfg.netmask);
            } else if (lower.startsWith("x-cstp-dns:")) {
                if (cfg.dns == null) cfg.dns = line.substring(11).trim();
                else                 cfg.dns2 = line.substring(11).trim();
            } else if (lower.startsWith("x-cstp-mtu:")) {
                try { cfg.mtu = Integer.parseInt(line.substring(11).trim()); } catch (Exception ignored) {}
            }
        }
        if (cfg.address == null || cfg.address.isEmpty()) {
            Log.e(TAG, "No X-CSTP-Address in response"); return null;
        }
        return cfg;
    }

    private int maskToPrefix(String mask) {
        try {
            byte[] b = InetAddress.getByName(mask).getAddress();
            int p = 0; for (byte x : b) p += Integer.bitCount(x & 0xFF);
            return p;
        } catch (Exception e) { return 24; }
    }

    // ── TUN interface ──────────────────────────────────────────────────────────

    private ParcelFileDescriptor buildVpnInterface(VpnTunnelConfig cfg, String name) {
        try {
            Builder b = new Builder();
            b.setSession(name != null ? name : "OpenConnect VPN")
             .setMtu(cfg.mtu)
             .addAddress(cfg.address, cfg.prefix)
             .addRoute("0.0.0.0", 0);
            if (cfg.dns  != null) try { b.addDnsServer(cfg.dns);  } catch (Exception ignored) {}
            if (cfg.dns2 != null) try { b.addDnsServer(cfg.dns2); } catch (Exception ignored) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) b.setMetered(false);
            return b.establish();
        } catch (Exception e) {
            Log.e(TAG, "TUN build failed", e); return null;
        }
    }

    // ── CSTP packet forwarding ─────────────────────────────────────────────────
    //
    // CSTP data packet layout (8-byte header):
    //   [0] 0x00  [1] 0x00  [2] len_hi  [3] len_lo  [4] type  [5][6][7] 0x00
    //
    private void forwardPackets(InputStream sockIn, OutputStream sockOut, int mtu) {
        // TUN → socket thread
        Thread tunToSock = new Thread(() -> {
            byte[] pkt = new byte[mtu];
            byte[] frm = new byte[mtu + CSTP_HDR];
            try (FileInputStream fis = new FileInputStream(tunInterface.getFileDescriptor())) {
                while (running) {
                    int n = fis.read(pkt, 0, mtu);
                    if (n <= 0) break;
                    frm[0] = 0; frm[1] = 0;
                    frm[2] = (byte) (n >> 8); frm[3] = (byte) (n & 0xFF);
                    frm[4] = CSTP_DATA; frm[5] = 0; frm[6] = 0; frm[7] = 0;
                    System.arraycopy(pkt, 0, frm, CSTP_HDR, n);
                    synchronized (sockOut) {
                        sockOut.write(frm, 0, CSTP_HDR + n);
                        sockOut.flush();
                    }
                }
            } catch (Exception e) {
                if (running) Log.e(TAG, "TUN→socket error", e);
            }
            running = false;
        }, "oc-tun-to-sock");

        // Socket → TUN thread
        Thread sockToTun = new Thread(() -> {
            byte[] hdr  = new byte[CSTP_HDR];
            byte[] dpdResp = new byte[CSTP_HDR]; // pre-built DPD response frame
            dpdResp[4] = CSTP_DPD_RESP;
            try (FileOutputStream fos = new FileOutputStream(tunInterface.getFileDescriptor())) {
                while (running) {
                    if (!readFully(sockIn, hdr, CSTP_HDR)) break;
                    int dataLen = ((hdr[2] & 0xFF) << 8) | (hdr[3] & 0xFF);
                    int type    = hdr[4] & 0xFF;
                    if (dataLen < 0 || dataLen > 65536) {
                        Log.e(TAG, "Bogus CSTP length " + dataLen); break;
                    }
                    byte[] data = new byte[dataLen];
                    if (dataLen > 0 && !readFully(sockIn, data, dataLen)) break;
                    switch (type) {
                        case CSTP_DATA:
                            fos.write(data);
                            break;
                        case CSTP_DPD_REQ:
                            synchronized (sockOut) {
                                sockOut.write(dpdResp);
                                sockOut.flush();
                            }
                            break;
                        case CSTP_DISCONNECT:
                            Log.i(TAG, "Server requested disconnect");
                            running = false;
                            break;
                        default:
                            break; // skip unknown types
                    }
                }
            } catch (Exception e) {
                if (running) Log.e(TAG, "socket→TUN error", e);
            }
            running = false;
        }, "oc-sock-to-tun");

        tunToSock.start();
        sockToTun.start();
        try { sockToTun.join(); } catch (InterruptedException ignored) {}
        running = false;
        try { tunToSock.join(2000); } catch (InterruptedException ignored) {}
    }

    // ── Teardown ───────────────────────────────────────────────────────────────

    private void shutdown() {
        running = false;
        safeClose(tunnelSocket);
        tunnelSocket = null;
        if (tunInterface != null) {
            try { tunInterface.close(); } catch (Exception ignored) {}
            tunInterface = null;
        }
        try { stopForeground(true); } catch (Exception ignored) {}
        broadcastStage("disconnected");
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private String readLine(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = is.read()) >= 0) {
            if (c == '\r') { is.read(); return sb.toString(); }
            if (c == '\n') return sb.toString();
            sb.append((char) c);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private boolean readFully(InputStream is, byte[] buf, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int n = is.read(buf, off, len - off);
            if (n < 0) return false;
            off += n;
        }
        return true;
    }

    private void drainAll(InputStream is) {
        try { byte[] b = new byte[512]; is.read(b); } catch (Exception ignored) {}
    }

    private void safeClose(SSLSocket s) {
        if (s != null) try { s.close(); } catch (Exception ignored) {}
    }

    private void broadcastStage(String stage) {
        Intent i = new Intent(BROADCAST_STAGE);
        i.putExtra(EXTRA_STAGE, stage);
        sendBroadcast(i);
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private Notification buildNotification(String title) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "OpenConnect VPN", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("OpenConnect tunnel active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
