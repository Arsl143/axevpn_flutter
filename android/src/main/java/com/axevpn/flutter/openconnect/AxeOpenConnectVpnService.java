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

    // CSTP packet types (byte [6] of the 8-byte STF header: 'S','T','F',0x01,lenH,lenL,type,0x00)
    private static final int CSTP_DATA       = 0x00;
    private static final int CSTP_DPD_REQ    = 0x03;
    private static final int CSTP_DPD_RESP   = 0x04;
    private static final int CSTP_KEEPALIVE  = 0x07; // server keepalive — no response needed
    private static final int CSTP_DISCONNECT = 0x09; // server-initiated disconnect
    private static final int CSTP_HDR        = 8;    // header size in bytes
    // STF sync: 'S'(0x53) 'T'(0x54) 'F'(0x46) 0x01
    private static final byte[] CSTP_SYNC = {0x53, 0x54, 0x46, 0x01};

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
        Log.d(TAG, "onStartCommand: action=" + action);
        if (ACTION_CONNECT.equals(action)) {
            String serverUrl  = intent.getStringExtra(EXTRA_SERVER_URL);
            String name       = intent.getStringExtra(EXTRA_TUNNEL_NAME);
            String username   = intent.getStringExtra(EXTRA_USERNAME);
            String password   = intent.getStringExtra(EXTRA_PASSWORD);
            String authGroup  = intent.getStringExtra(EXTRA_AUTH_GROUP);
            String servercert = intent.getStringExtra(EXTRA_SERVERCERT);
            if (serverUrl != null && username != null && password != null) {
                // Must call startForeground() within 5 s of startService() (Android 8+).
                // On Android 14+ (API 34), the 3-arg overload with a type is mandatory
                // — using the 2-arg version throws MissingForegroundServiceTypeException.
                Notification fgNotif = buildNotification(name != null ? name : "OpenConnect VPN");
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIFICATION_ID, fgNotif,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    startForeground(NOTIFICATION_ID, fgNotif);
                }
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

            // ── Phase 1: Authenticate (2-step: username → context cookie → password → session) ──
            broadcastStage("authenticating");

            // Step 1a: POST username to /auth to get context cookie
            authSock = (SSLSocket) ssf.createSocket(host, port);
            protect(authSock);
            authSock.startHandshake();

            String body1 = "username=" + URLEncoder.encode(username, "UTF-8");
            if (authGroup != null && !authGroup.isEmpty())
                body1 += "&group_list=" + URLEncoder.encode(authGroup, "UTF-8");

            String authReq1 = "POST /auth HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "User-Agent: AnyConnect Android 4.10.04065\r\n"
                    + "X-AnyConnect-Platform: android\r\n"
                    + "Content-Type: application/x-www-form-urlencoded\r\n"
                    + "Content-Length: " + body1.getBytes("UTF-8").length + "\r\n"
                    + "Connection: close\r\n\r\n"
                    + body1;

            OutputStream authOs1 = authSock.getOutputStream();
            authOs1.write(authReq1.getBytes("UTF-8"));
            authOs1.flush();

            String contextCookie = parseAuthCookie(authSock.getInputStream());
            safeClose(authSock);
            authSock = null;

            if (contextCookie == null || contextCookie.isEmpty()) {
                Log.e(TAG, "Authentication step 1 failed — no context cookie received");
                broadcastStage("denied");
                stopSelf();
                return;
            }

            // Step 1b: POST password with context cookie to /auth to get VPN session cookies
            authSock = (SSLSocket) ssf.createSocket(host, port);
            protect(authSock);
            authSock.startHandshake();

            String body2 = "password=" + URLEncoder.encode(password, "UTF-8");
            String authReq2 = "POST /auth HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "User-Agent: AnyConnect Android 4.10.04065\r\n"
                    + "X-AnyConnect-Platform: android\r\n"
                    + "Cookie: " + contextCookie + "\r\n"
                    + "Content-Type: application/x-www-form-urlencoded\r\n"
                    + "Content-Length: " + body2.getBytes("UTF-8").length + "\r\n"
                    + "Connection: close\r\n\r\n"
                    + body2;

            OutputStream authOs2 = authSock.getOutputStream();
            authOs2.write(authReq2.getBytes("UTF-8"));
            authOs2.flush();

            String sessionCookie = parseAuthCookie(authSock.getInputStream());
            safeClose(authSock);
            authSock = null;

            if (sessionCookie == null || sessionCookie.isEmpty()) {
                Log.e(TAG, "Authentication step 2 failed — no session cookie received");
                broadcastStage("denied");
                stopSelf();
                return;
            }

            // ocserv CONNECT needs ONLY the webvpn= cookie — strip webvpncontext and webvpnc
            for (String part : sessionCookie.split("; ")) {
                if (part.startsWith("webvpn=")) { // matches webvpn= but NOT webvpnc= or webvpncontext=
                    sessionCookie = part;
                    break;
                }
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
                    + "X-CSTP-Accept-Encoding: identity\r\n"
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
            // startForeground() was already called in onStartCommand().
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
                // Skip empty-value cookies (e.g. clearing cookies like webvpnc=)
                int eq = cookie.indexOf('=');
                if (eq < 0 || cookie.substring(eq + 1).isEmpty()) continue;
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
            Log.d(TAG, "tunnel hdr: [" + line + "]");
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
            // establish() opens the TUN fd with O_NONBLOCK. Use Os.poll() to block
            // until a packet is available so we don't busy-spin or exit prematurely.
            android.system.StructPollfd[] pollFds = {new android.system.StructPollfd()};
            pollFds[0].fd = tunInterface.getFileDescriptor();
            pollFds[0].events = (short) android.system.OsConstants.POLLIN;
            try (FileInputStream fis = new FileInputStream(tunInterface.getFileDescriptor())) {
                while (running) {
                    try {
                        android.system.Os.poll(pollFds, 500);
                    } catch (android.system.ErrnoException e) {
                        if (e.errno == android.system.OsConstants.EINTR) continue;
                        Log.e(TAG, "TUN poll error, errno=" + e.errno); break;
                    }
                    if ((pollFds[0].revents & android.system.OsConstants.POLLIN) == 0) continue;
                    int n = fis.read(pkt, 0, mtu);
                    if (n < 0) {
                        Log.e(TAG, "TUN→socket: TUN fd read returned " + n + ", exiting");
                        break;
                    }
                    if (n == 0) continue;
                    // Build outgoing STF frame: sync[0-3], lenH[4], lenL[5], type[6], reserved[7]
                    frm[0] = CSTP_SYNC[0]; frm[1] = CSTP_SYNC[1];
                    frm[2] = CSTP_SYNC[2]; frm[3] = CSTP_SYNC[3];
                    frm[4] = (byte) (n >> 8); frm[5] = (byte) (n & 0xFF);
                    frm[6] = CSTP_DATA;    frm[7] = 0;
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
            // Pre-built DPD response frame with correct STF sync
            byte[] dpdResp = {CSTP_SYNC[0], CSTP_SYNC[1], CSTP_SYNC[2], CSTP_SYNC[3],
                              0x00, 0x00, (byte) CSTP_DPD_RESP, 0x00};
            try (FileOutputStream fos = new FileOutputStream(tunInterface.getFileDescriptor())) {
                while (running) {
                    if (!readFully(sockIn, hdr, CSTP_HDR)) {
                        Log.e(TAG, "socket\u2192TUN: EOF reading CSTP header (server closed connection)");
                        break;
                    }
                    // Validate CSTP sync bytes and dump raw header for diagnostics
                    Log.d(TAG, String.format("CSTP hdr: %02x %02x %02x %02x  %02x %02x %02x %02x",
                            hdr[0]&0xFF, hdr[1]&0xFF, hdr[2]&0xFF, hdr[3]&0xFF,
                            hdr[4]&0xFF, hdr[5]&0xFF, hdr[6]&0xFF, hdr[7]&0xFF));
                    if (hdr[0] != CSTP_SYNC[0] || hdr[1] != CSTP_SYNC[1]
                            || hdr[2] != CSTP_SYNC[2] || hdr[3] != CSTP_SYNC[3]) {
                        Log.e(TAG, String.format(
                                "Invalid STF sync: %02x %02x %02x %02x — stream misaligned",
                                hdr[0]&0xFF, hdr[1]&0xFF, hdr[2]&0xFF, hdr[3]&0xFF));
                        break;
                    }
                    // Real STF header: sync[0-3], lenH[4], lenL[5], type[6], reserved[7]
                    int dataLen = ((hdr[4] & 0xFF) << 8) | (hdr[5] & 0xFF);
                    int type    = hdr[6] & 0xFF;
                    Log.d(TAG, "CSTP pkt type=" + type + " len=" + dataLen);
                    if (dataLen > 65535) {
                        Log.e(TAG, "Bogus CSTP length " + dataLen); break;
                    }
                    byte[] data = new byte[dataLen];
                    if (dataLen > 0 && !readFully(sockIn, data, dataLen)) {
                        Log.e(TAG, "socket\u2192TUN: EOF reading CSTP data (expected " + dataLen + " bytes)");
                        break;
                    }
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
                            Log.w(TAG, "Unknown CSTP type " + type + ", skipping");
                            break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "socket\u2192TUN error", e);
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
        Log.d(TAG, "broadcastStage: " + stage);
        Intent i = new Intent(BROADCAST_STAGE);
        i.putExtra(EXTRA_STAGE, stage);
        // setPackage() is required on Android 14+ so the broadcast reaches
        // a RECEIVER_NOT_EXPORTED receiver registered in the same app.
        i.setPackage(getPackageName());
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
