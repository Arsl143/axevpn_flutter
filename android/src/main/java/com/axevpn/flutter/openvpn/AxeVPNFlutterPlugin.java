package com.axevpn.flutter.openvpn;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.BadConfigException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import com.wireguard.crypto.Key;

import com.axevpn.flutter.wireguard.AxeWireGuardService;
import com.axevpn.flutter.v2ray.V2RayFlutterPlugin;
import com.axevpn.flutter.openconnect.OpenConnectFlutterPlugin;

import de.blinkt.openvpn.OnVPNStatusChangeListener;
import de.blinkt.openvpn.VPNHelper;
import de.blinkt.openvpn.core.OpenVPNService;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;

/**
 * AxeVPN Flutter Plugin - Enhanced OpenVPN Integration
 *
 * This plugin provides comprehensive OpenVPN connectivity for Flutter applications
 * with full support for Android 15+ (16 KB page size) and modern Android features.
 *
 * Version: 2.0.0
 * Package: com.axevpn.flutter.openvpn
 *
 * Features:
 * - OpenVPN connection management
 * - Real-time connection status monitoring
 * - Support for both TCP and UDP protocols
 * - Android 15+ compatible with 16 KB page sizes
 * - Package bypass for split tunneling
 * - Automatic reconnection handling
 *
 * @author AxeVPN Team
 * @since 2.0.0
 */
public class AxeVPNFlutterPlugin implements FlutterPlugin, ActivityAware, io.flutter.plugin.common.PluginRegistry.ActivityResultListener {

    // OpenVPN channels
    private MethodChannel vpnControlMethod;
    private EventChannel vpnStageEvent;
    private EventChannel.EventSink vpnStageSink;

    // ✅ OpenVPN Updated channel names for AxeVPN
    private static final String EVENT_CHANNEL_VPN_STAGE = "com.axevpn.flutter.openvpn/vpnstage";
    private static final String METHOD_CHANNEL_VPN_CONTROL = "com.axevpn.flutter.openvpn/vpncontrol";

    // ✅ WireGuard channels
    private MethodChannel wgControlMethod;
    private EventChannel wgStageEvent;
    private EventChannel.EventSink wgStageSink;

    private static final String EVENT_CHANNEL_WG_STAGE = "com.axevpn.flutter.wireguard/vpnstage";
    private static final String METHOD_CHANNEL_WG_CONTROL = "com.axevpn.flutter.wireguard/vpncontrol";

    private static String config = "", username = "", password = "", name = "";
    private static ArrayList<String> bypassPackages;

    // Set to true when a connect() call is in flight so that any pending
    // delayed DISCONNECT_VPN intent is cancelled before it kills the new session.
    private volatile boolean _connectingInProgress = false;

    @SuppressLint("StaticFieldLeak")
    private static VPNHelper vpnHelper;

    // ✅ WireGuard backend
    private Backend wgBackend;
    private WireGuardTunnel currentTunnel;
    private String wgStage = "disconnected";

    // Pending WireGuard connection data (for after VPN permission)
    private String pendingWgConfig;
    private String pendingWgTunnelName;

    // WireGuard VPN service reference
    private static final int VPN_REQUEST_CODE_WG = 25;
    private boolean isWgConnecting = false;

    private Activity activity;
    private ActivityPluginBinding activityBinding;
    Context mContext;

    // ── Delegated protocol sub-plugins ────────────────────────────────────
    private final V2RayFlutterPlugin v2rayPlugin = new V2RayFlutterPlugin();
    private final OpenConnectFlutterPlugin openConnectPlugin = new OpenConnectFlutterPlugin();

    // ✅ CRITICAL: Load native libraries required for OpenVPN JNI
    // Updated with ics-openvpn v0.7.64 (16 KB page size compatible)
    static {
        // Load OpenVPN utility library (contains JNI methods)
        try {
            System.loadLibrary("ovpnutil");
            android.util.Log.i("AxeVPN", "✅ Loaded ovpnutil library");
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.e("AxeVPN", "❌ Failed to load ovpnutil: " + e.getMessage());
        }

        // Load additional OpenVPN libraries (all from ics-openvpn v0.7.64 with 16 KB support)
        String[] libraries = {"openvpn", "ovpnexec", "osslutil", "osslspeedtest", "ovpn3"};
        for (String lib : libraries) {
            try {
                System.loadLibrary(lib);
                android.util.Log.i("AxeVPN", "✅ Loaded " + lib + " library");
            } catch (UnsatisfiedLinkError e) {
                android.util.Log.w("AxeVPN", "⚠️ " + lib + " library not loaded: " + e.getMessage());
            }
        }
    }

    /**
     * Called when VPN permission is granted
     * @param granted true if permission was granted
     */
    public static void connectWhileGranted(boolean granted) {
        if (granted && vpnHelper != null) {
            vpnHelper.startVPN(config, username, password, name, bypassPackages);
        }
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        mContext = binding.getApplicationContext();

        // ✅ Initialize WireGuard backend with VPN service
        wgBackend = new GoBackend(mContext);

        // Setup notification service disconnect callback
        setupWireGuardServiceCallback();

        // Setup OpenVPN channels
        vpnStageEvent = new EventChannel(binding.getBinaryMessenger(), EVENT_CHANNEL_VPN_STAGE);
        vpnControlMethod = new MethodChannel(binding.getBinaryMessenger(), METHOD_CHANNEL_VPN_CONTROL);

        // Setup OpenVPN stage event stream handler
        vpnStageEvent.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object arguments, EventChannel.EventSink events) {
                vpnStageSink = events;
            }

            @Override
            public void onCancel(Object arguments) {
                if (vpnStageSink != null) {
                    vpnStageSink.endOfStream();
                }
            }
        });

        // ✅ Setup WireGuard channels
        wgStageEvent = new EventChannel(binding.getBinaryMessenger(), EVENT_CHANNEL_WG_STAGE);
        wgControlMethod = new MethodChannel(binding.getBinaryMessenger(), METHOD_CHANNEL_WG_CONTROL);

        // Setup WireGuard stage event stream handler
        wgStageEvent.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object arguments, EventChannel.EventSink events) {
                wgStageSink = events;
                android.util.Log.d("AxeVPN-WG", "WireGuard stage stream - onListen");
            }

            @Override
            public void onCancel(Object arguments) {
                if (wgStageSink != null) {
                    wgStageSink.endOfStream();
                }
                android.util.Log.d("AxeVPN-WG", "WireGuard stage stream - onCancel");
            }
        });

        // Setup OpenVPN control method handler
        setupOpenVPNMethodHandler();

        // ✅ Setup WireGuard control method handler
        setupWireGuardMethodHandler();

        // ✅ Delegate V2Ray and OpenConnect plugins
        v2rayPlugin.onAttachedToEngine(binding);
        openConnectPlugin.onAttachedToEngine(binding);
    }
    /**
     * Setup WireGuard notification service disconnect callback
     */
    private void setupWireGuardServiceCallback() {
        // This will be called when user taps disconnect in notification
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            AxeWireGuardService service = AxeWireGuardService.getInstance();
            if (service != null) {
                service.setDisconnectCallback(() -> {
                    android.util.Log.i("AxeVPN-WG", "Disconnect requested from notification");
                    handleFullWgDisconnect();
                });
            }
        }, 500);
    }
    /**
     * Setup OpenVPN method channel handler
     */
    private void setupOpenVPNMethodHandler() {
        vpnControlMethod.setMethodCallHandler((call, result) -> {
            try {
                switch (call.method) {
                    case "setNotificationConfig":
                        com.axevpn.flutter.core.VpnNotificationConfig.configure(
                                call.argument("appName"),
                                call.argument("connectedTitle"),
                                call.argument("connectedSubtitle"),
                                call.argument("channelName"),
                                call.argument("channelDescription"));
                        result.success(null);
                        break;

                    case "status":
                        if (vpnHelper == null) {
                            result.error("-1", "VPNEngine needs to be initialized", "Call initialize() first");
                            return;
                        }
                        result.success(vpnHelper.status.toString());
                        break;

                    case "initialize":
                        if (activity == null) {
                            result.error("-3", "Activity not attached", "Plugin initialization failed");
                            return;
                        }
                        vpnHelper = new VPNHelper(activity);
                        registerVPNStatusListener();
                        result.success(updateVPNStages());
                        break;

                    case "disconnect":
                        if (vpnHelper == null) {
                            result.error("-1", "VPNEngine needs to be initialized", "Call initialize() first");
                            return;
                        }
                        // 1. Kill native OpenVPN thread and reset vpnStart=false.
                        //    vpnStart MUST be false before the next startVPN() call —
                        //    VPNHelper.startVPN() (no-arg) silently skips connecting when
                        //    vpnStart==true, so any subsequent connect would do nothing.
                        _connectingInProgress = false;
                        try {
                            vpnHelper.stopVPN();
                        } catch (Exception stopEx) {
                            // OpenVPNService may already be null/dead — safe to ignore;
                            // vpnStart should already be false if the process exited naturally.
                            android.util.Log.w("AxeVPN", "stopVPN() in disconnect: " + stopEx.getMessage());
                        }
                        // 2. Tell Dart the VPN is disconnected immediately so the UI updates.
                        updateStage("disconnected");
                        // 3. Send DISCONNECT_VPN to OpenVPNService after a short delay so the
                        //    service tears down the tun/removes the VPN key icon.
                        //    The delay + _connectingInProgress flag ensures a rapid reconnect
                        //    cancels this intent before it can kill the new session.
                        sendDisconnectVpnIntentDelayed();
                        result.success(true);
                        break;

                    case "connect":
                        if (vpnHelper == null) {
                            result.error("-1", "VPNEngine needs to be initialized", "Call initialize() first");
                            return;
                        }

                        config = call.argument("config");
                        name = call.argument("name");
                        username = call.argument("username");
                        password = call.argument("password");
                        bypassPackages = call.argument("bypass_packages");

                        if (config == null || config.isEmpty()) {
                            result.error("-2", "OpenVPN config is required", "Provide valid .ovpn configuration");
                            return;
                        }

                        // Cancel any pending DISCONNECT_VPN intent before connecting.
                        _connectingInProgress = true;
                        // Always reset vpnStart=false before startVPN() — the flag is static and
                        // persists if the previous OpenVPN process exited without an explicit
                        // disconnect() call (e.g. server timeout).  Re-register the listener
                        // immediately after because stopVPN() unregisters the broadcastReceiver.
                        try {
                            vpnHelper.stopVPN();
                        } catch (Exception stopEx) {
                            // OpenVPNService may already be null/dead — safe to ignore.
                            android.util.Log.w("AxeVPN", "stopVPN() in connect: " + stopEx.getMessage());
                        }
                        registerVPNStatusListener();

                        final Intent permission = VpnService.prepare(activity);
                        if (permission != null) {
                            activity.startActivityForResult(permission, 24);
                            result.success(false); // Permission required
                            return;
                        }
                        vpnHelper.startVPN(config, username, password, name, bypassPackages);
                        result.success(true);
                        break;

                    case "stage":
                        if (vpnHelper == null) {
                            result.error("-1", "VPNEngine needs to be initialized", "Call initialize() first");
                            return;
                        }
                        result.success(updateVPNStages());
                        break;

                    case "request_permission":
                        if (activity == null) {
                            result.error("-3", "Activity not attached", "Cannot request permission");
                            return;
                        }
                        final Intent request = VpnService.prepare(activity);
                        if (request != null) {
                            activity.startActivityForResult(request, 24);
                            result.success(false);
                            return;
                        }
                        result.success(true);
                        break;

                    default:
                        result.notImplemented();
                        break;
                }
            } catch (Exception e) {
                result.error("-99", "Unexpected error", e.getMessage());
            }
        });
    }

    /**
     * Sends de.blinkt.openvpn.DISCONNECT_VPN to OpenVPNService via startService().
     * OpenVPNService.onStartCommand() handles this action by calling its own stopVPN(false),
     * which releases the tun interface, stops the foreground notification (removes the VPN
     * key icon from the status bar), and calls stopSelf().
     * The VpnStatus → LocalBroadcast → VPNHelper.listener chain then fires updateStage("disconnected").
     */
    /**
     * Registers (or re-registers) the VPN status listener with vpnHelper.
     * Must be called after vpnHelper is created (initialize) and after
     * vpnHelper.stopVPN() (which unregisters the broadcastReceiver), so
     * that startVPN() status callbacks flow through correctly.
     */
    private void registerVPNStatusListener() {
        vpnHelper.setOnVPNStatusChangeListener(new OnVPNStatusChangeListener() {
            @Override
            public void onVPNStatusChanged(String status) {
                updateStage(status);
            }

            @Override
            public void onConnectionStatusChanged(String duration, String lastPacketReceive,
                                                 String byteIn, String byteOut) {
                // Connection stats updated
            }
        });
    }

    /**
     * Schedules DISCONNECT_VPN to be sent to OpenVPNService after a 2-second delay.
     * If a new connect() call arrives before the delay fires, _connectingInProgress
     * is set to true and the intent is silently dropped, preventing the new session
     * from being killed by a stale disconnect command.
     */
    private void sendDisconnectVpnIntentDelayed() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (_connectingInProgress) {
                android.util.Log.i("AxeVPN", "⚠️ Skipped DISCONNECT_VPN — new connection in progress");
                return;
            }
            try {
                Intent disconnectIntent = new Intent(OpenVPNService.DISCONNECT_VPN);
                disconnectIntent.setClass(mContext, OpenVPNService.class);
                mContext.startService(disconnectIntent);
                android.util.Log.i("AxeVPN", "✅ Sent DISCONNECT_VPN to OpenVPNService");
            } catch (Exception e) {
                android.util.Log.e("AxeVPN", "❌ DISCONNECT_VPN failed: " + e.getMessage());
            }
        }, 2000);
    }

    /**
     * ✅ Setup WireGuard method channel handler
     */
    private void setupWireGuardMethodHandler() {
        wgControlMethod.setMethodCallHandler((call, result) -> {
            try {
                switch (call.method) {
                    case "initialize":
                        android.util.Log.i("AxeVPN-WG", "Initializing WireGuard plugin");
                        updateWgStage("disconnected");
                        result.success(null);
                        break;

                    case "connect":
                        handleWgConnect(call, result);
                        break;

                    case "disconnect":
                        handleWgDisconnect(result);
                        break;

                    case "status":
                        handleWgStatus(result);
                        break;

                    case "stage":
                        result.success(wgStage);
                        break;

                    case "setNotificationConfig":
                        com.axevpn.flutter.core.VpnNotificationConfig.configure(
                                call.argument("appName"),
                                call.argument("connectedTitle"),
                                call.argument("connectedSubtitle"),
                                call.argument("channelName"),
                                call.argument("channelDescription"));
                        result.success(null);
                        break;

                    default:
                        result.notImplemented();
                        break;
                }
            } catch (Exception e) {
                android.util.Log.e("AxeVPN-WG", "Error", e);
                result.error("-99", "WireGuard error", e.getMessage());
            }
        });
    }

    /**
     * ✅ Handle WireGuard connect
     */
    private void handleWgConnect(io.flutter.plugin.common.MethodCall call, MethodChannel.Result result) {
        String config = call.argument("config");
        String tunnelName = call.argument("tunnelName");

        if (config == null || config.isEmpty()) {
            result.error("INVALID_CONFIG", "WireGuard config required", null);
            return;
        }

        if (tunnelName == null || tunnelName.isEmpty()) {
            // Never hardcode a brand name here: fall back to the buyer's configured
            // appName, or the host app's own label, so white-label apps never show "AxeVPN".
            tunnelName = com.axevpn.flutter.core.VpnNotificationConfig.defaultTunnelName(mContext);
        }

        try {
            // Validate config
            ByteArrayInputStream configStream = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8));
            Config wgConfig = Config.parse(configStream);
            currentTunnel = new WireGuardTunnel(tunnelName, wgConfig);

            android.util.Log.i("AxeVPN-WG", "Connecting: " + tunnelName);
            updateWgStage("preparing");

            // Check VPN permission
            Intent permission = VpnService.prepare(mContext);
            if (permission != null && activity != null) {
                // Store config for after permission granted
                pendingWgConfig = config;
                pendingWgTunnelName = tunnelName;
                activity.startActivityForResult(permission, 25);
                result.success(null);
            } else {
                // Permission already granted - start tunnel
                startWgVPN(wgConfig, tunnelName);
                result.success(null);
            }
        } catch (BadConfigException e) {
            android.util.Log.e("AxeVPN-WG", "Invalid config", e);
            result.error("INVALID_CONFIG", e.getMessage(), null);
        } catch (Exception e) {
            android.util.Log.e("AxeVPN-WG", "Connect failed", e);
            result.error("CONNECT_FAILED", e.getMessage(), null);
        }
    }

    /**
     * ✅ Handle WireGuard disconnect - called from Flutter
     */
    private void handleWgDisconnect(MethodChannel.Result result) {
        android.util.Log.i("AxeVPN-WG", "Disconnect requested from Flutter");
        handleFullWgDisconnect();
        result.success(null);
    }

    /**
     * ✅ Perform full WireGuard disconnect
     */
    private void handleFullWgDisconnect() {
        android.util.Log.i("AxeVPN-WG", "Performing full disconnect");
        isWgConnecting = false;
        updateWgStage("disconnecting");

        // Stop notification service first
        try {
            Intent stopIntent = new Intent(mContext, AxeWireGuardService.class);
            stopIntent.setAction(AxeWireGuardService.ACTION_STOP);
            mContext.startService(stopIntent);
        } catch (Exception e) {
            android.util.Log.e("AxeVPN-WG", "Error stopping notification service", e);
        }

        // Stop tunnel via backend
        if (currentTunnel != null) {
            final WireGuardTunnel tunnelToStop = currentTunnel;

            new Thread(() -> {
                try {
                    android.util.Log.d("AxeVPN-WG", "Stopping tunnel: " + tunnelToStop.getName());
                    wgBackend.setState(tunnelToStop, Tunnel.State.DOWN, null);
                    Thread.sleep(300);

                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            currentTunnel = null;
                            updateWgStage("disconnected");
                            android.util.Log.i("AxeVPN-WG", "✅ Fully disconnected");
                        });
                    } else {
                        currentTunnel = null;
                        wgStage = "disconnected";
                    }
                } catch (Exception e) {
                    android.util.Log.e("AxeVPN-WG", "Disconnect error", e);
                    currentTunnel = null;
                    if (activity != null) {
                        activity.runOnUiThread(() -> updateWgStage("disconnected"));
                    } else {
                        wgStage = "disconnected";
                    }
                }
            }).start();
        } else {
            android.util.Log.w("AxeVPN-WG", "No active tunnel to disconnect");
            updateWgStage("disconnected");
        }
    }

    /**
     * ✅ Handle WireGuard status
     */
    private void handleWgStatus(MethodChannel.Result result) {
        try {
            org.json.JSONObject status = new org.json.JSONObject();
            status.put("duration", "0");

            if (currentTunnel != null) {
                try {
                    Statistics stats = wgBackend.getStatistics(currentTunnel);
                    if (stats != null) {
                        Key[] peers = stats.peers();
                        long totalBytesIn = 0;
                        long totalBytesOut = 0;
                        long lastHandshake = 0;

                        for (Key peer : peers) {
                            Statistics.PeerStats peerStats = stats.peer(peer);
                            if (peerStats != null) {
                                totalBytesIn += peerStats.rxBytes();
                                totalBytesOut += peerStats.txBytes();
                                lastHandshake = Math.max(lastHandshake, peerStats.latestHandshakeEpochMillis());
                            }
                        }

                        status.put("byte_in", String.valueOf(totalBytesIn));
                        status.put("byte_out", String.valueOf(totalBytesOut));
                        status.put("last_packet_receive", String.valueOf(lastHandshake));
                    } else {
                        status.put("byte_in", "0");
                        status.put("byte_out", "0");
                        status.put("last_packet_receive", "0");
                    }
                } catch (Exception e) {
                    status.put("byte_in", "0");
                    status.put("byte_out", "0");
                    status.put("last_packet_receive", "0");
                }
            } else {
                status.put("byte_in", "0");
                status.put("byte_out", "0");
                status.put("last_packet_receive", "0");
            }

            result.success(status.toString());
        } catch (org.json.JSONException e) {
            result.error("STATUS_ERROR", e.getMessage(), null);
        }
    }

    /**
     * ✅ Start WireGuard VPN
     */
    private void startWgVPN(Config config, String tunnelName) {
        if (isWgConnecting) {
            android.util.Log.w("AxeVPN-WG", "Already connecting, ignoring duplicate request");
            return;
        }

        isWgConnecting = true;
        updateWgStage("connecting");

        // Start notification service
        try {
            Intent notificationIntent = new Intent(mContext, AxeWireGuardService.class);
            notificationIntent.setAction(AxeWireGuardService.ACTION_START);
            notificationIntent.putExtra(AxeWireGuardService.EXTRA_TUNNEL_NAME, tunnelName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mContext.startForegroundService(notificationIntent);
            } else {
                mContext.startService(notificationIntent);
            }
            android.util.Log.d("AxeVPN-WG", "Notification service started");

            // Setup callback after service starts
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                setupWireGuardServiceCallback();
            }, 500);
        } catch (Exception e) {
            android.util.Log.e("AxeVPN-WG", "Failed to start notification service", e);
        }

        // NOTE: the actual tunnel is established below via GoBackend.setState(), which
        // internally starts and promotes its own com.wireguard.android.backend.GoBackend$VpnService
        // to the foreground (declared in this plugin's AndroidManifest.xml with the
        // specialUse type). AxeWireGuardVpnService is a separate, non-functional VpnService
        // (it opens a TUN fd but nothing reads/writes wireguard packets on it) — starting it
        // here raced GoBackend's own VpnService.Builder().establish() call for the same
        // underlying VPN interface, which is what produced the unstable foreground-service
        // promotion Envato flagged. Do not start it for the real tunnel.

        // Start tunnel via backend
        new Thread(() -> {
            try {
                android.util.Log.d("AxeVPN-WG", "Starting GoBackend tunnel");
                wgBackend.setState(currentTunnel, Tunnel.State.UP, config);

                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        isWgConnecting = false;
                        updateWgStage("connected");
                        android.util.Log.i("AxeVPN-WG", "✅ Connected: " + tunnelName);
                    });
                } else {
                    isWgConnecting = false;
                    wgStage = "connected";
                }
            } catch (Exception e) {
                android.util.Log.e("AxeVPN-WG", "Start VPN failed", e);
                isWgConnecting = false;
                if (activity != null) {
                    activity.runOnUiThread(() -> updateWgStage("error"));
                } else {
                    wgStage = "error";
                }
            }
        }).start();
    }

    /**
     * ✅ Update WireGuard stage
     */
    private void updateWgStage(String stage) {
        wgStage = stage;
        if (wgStageSink != null && activity != null) {
            activity.runOnUiThread(() -> {
                wgStageSink.success(stage);
            });
        }
        android.util.Log.d("AxeVPN-WG", "Stage: " + stage);
    }

    /**
     * Update VPN stage and notify listeners (OpenVPN).
     * Always posts to the main looper so the EventSink.success() call is safe
     * regardless of which thread (VPN service / notification action) invokes this.
     * @param stage Current VPN connection stage
     */
    public void updateStage(String stage) {
        if (stage == null) {
            stage = "idle";
        }
        final String finalStage = stage.toLowerCase();
        if (vpnStageSink != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (vpnStageSink != null) vpnStageSink.success(finalStage);
            });
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (vpnStageEvent != null) {
            vpnStageEvent.setStreamHandler(null);
        }
        if (vpnControlMethod != null) {
            vpnControlMethod.setMethodCallHandler(null);
        }
        v2rayPlugin.onDetachedFromEngine(binding);
        openConnectPlugin.onDetachedFromEngine(binding);
    }

    /**
     * Update and return current VPN stage
     * @return Current VPN stage string
     */
    private String updateVPNStages() {
        if (OpenVPNService.getStatus() == null) {
            OpenVPNService.setDefaultStatus();
        }
        String status = OpenVPNService.getStatus();
        updateStage(status);
        return status;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        activityBinding = binding;
        binding.addActivityResultListener(this);
        v2rayPlugin.onAttachedToActivity(binding);
        openConnectPlugin.onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        v2rayPlugin.onDetachedFromActivityForConfigChanges();
        openConnectPlugin.onDetachedFromActivityForConfigChanges();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        activityBinding = binding;
        binding.addActivityResultListener(this);
        v2rayPlugin.onReattachedToActivityForConfigChanges(binding);
        openConnectPlugin.onReattachedToActivityForConfigChanges(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this);
            activityBinding = null;
        }
        activity = null;
        v2rayPlugin.onDetachedFromActivity();
        openConnectPlugin.onDetachedFromActivity();
    }

    /**
     * Handle activity result for VPN permission
     */
    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 25) {
            // WireGuard VPN permission result
            if (resultCode == Activity.RESULT_OK) {
                android.util.Log.i("AxeVPN-WG", "VPN permission granted, starting connection");
                if (pendingWgConfig != null && pendingWgTunnelName != null) {
                    try {
                        ByteArrayInputStream configStream = new ByteArrayInputStream(
                            pendingWgConfig.getBytes(StandardCharsets.UTF_8));
                        Config wgConfig = Config.parse(configStream);
                        startWgVPN(wgConfig, pendingWgTunnelName);
                    } catch (Exception e) {
                        android.util.Log.e("AxeVPN-WG", "Failed to start after permission", e);
                        updateWgStage("error");
                    } finally {
                        pendingWgConfig = null;
                        pendingWgTunnelName = null;
                    }
                }
            } else {
                android.util.Log.w("AxeVPN-WG", "VPN permission denied");
                updateWgStage("denied");
                pendingWgConfig = null;
                pendingWgTunnelName = null;
            }
            return true;
        } else if (requestCode == 24) {
            // OpenVPN permission result
            if (resultCode == Activity.RESULT_OK && vpnHelper != null) {
                vpnHelper.startVPN(config, username, password, name, bypassPackages);
            }
            return true;
        }
        return false;
    }

    /**
     * ✅ WireGuard Tunnel implementation
     */
    private static class WireGuardTunnel implements Tunnel {
        private final String name;
        private State state = State.DOWN;

        WireGuardTunnel(String name, Config config) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void onStateChange(State newState) {
            state = newState;
        }

        public State getState() {
            return state;
        }
    }
}
