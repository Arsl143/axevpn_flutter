package com.axevpn.flutter.wireguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import androidx.annotation.NonNull;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.BadConfigException;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * AxeVPN Flutter Plugin - WireGuard Integration
 * 
 * This plugin provides comprehensive WireGuard connectivity for Flutter applications
 * with full support for modern Android versions.
 * 
 * Version: 2.0.0
 * Package: com.axevpn.flutter.wireguard
 * 
 * Features:
 * - WireGuard connection management using GoBackend
 * - Real-time connection status monitoring
 * - Modern high-performance VPN protocol
 * - Android native WireGuard integration
 * 
 * @author AxeVPN Team
 * @since 2.0.0
 */
public class WireGuardFlutterPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {

    private static final String TAG = "WireGuardPlugin";
    
    private MethodChannel vpnControlMethod;
    private EventChannel vpnStageEvent;
    private EventChannel.EventSink vpnStageSink;

    // Channel names
    private static final String EVENT_CHANNEL_VPN_STAGE = "com.axevpn.flutter.wireguard/vpnstage";
    private static final String METHOD_CHANNEL_VPN_CONTROL = "com.axevpn.flutter.wireguard/vpncontrol";

    private static final int VPN_REQUEST_CODE = 0x47;
    
    private Activity activity;
    private Context context;
    
    // WireGuard backend and tunnel
    private Backend backend;
    private WireGuardTunnel currentTunnel;
    private Config currentWgConfig;
    private WireGuardStage currentStage = WireGuardStage.DISCONNECTED;
    
    // Statistics
    private long bytesIn = 0;
    private long bytesOut = 0;
    private long lastHandshake = 0;
    
    /**
     * WireGuard connection stages
     */
    private enum WireGuardStage {
        PREPARING("preparing"),
        CONNECTING("connecting"),
        CONNECTED("connected"),
        DISCONNECTING("disconnecting"),
        DISCONNECTED("disconnected"),
        ERROR("error"),
        DENIED("denied");
        
        private final String value;
        
        WireGuardStage(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        context = binding.getApplicationContext();
        
        // Initialize WireGuard backend
        backend = new GoBackend(context);
        
        // Setup event channel for stage updates
        vpnStageEvent = new EventChannel(binding.getBinaryMessenger(), EVENT_CHANNEL_VPN_STAGE);
        vpnStageEvent.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object arguments, EventChannel.EventSink events) {
                vpnStageSink = events;
                Log.d(TAG, "Stage event stream - onListen");
            }

            @Override
            public void onCancel(Object arguments) {
                vpnStageSink = null;
                Log.d(TAG, "Stage event stream - onCancel");
            }
        });

        // Setup method channel for control
        vpnControlMethod = new MethodChannel(binding.getBinaryMessenger(), METHOD_CHANNEL_VPN_CONTROL);
        vpnControlMethod.setMethodCallHandler(this);
        
        Log.i(TAG, "WireGuard plugin attached to engine with GoBackend");
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "initialize":
                handleInitialize(call, result);
                break;
            case "connect":
                handleConnect(call, result);
                break;
            case "disconnect":
                handleDisconnect(result);
                break;
            case "status":
                handleStatus(result);
                break;
            case "stage":
                handleStage(result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    /**
     * Initialize WireGuard plugin
     */
    private void handleInitialize(MethodCall call, Result result) {
        Log.i(TAG, "Initializing WireGuard plugin");
        // Android WireGuard doesn't need special initialization
        // iOS parameters are ignored on Android
        updateStage(WireGuardStage.DISCONNECTED);
        result.success(null);
    }

    /**
     * Connect to WireGuard VPN
     */
    private void handleConnect(MethodCall call, Result result) {
        String config = call.argument("config");
        String tunnelName = call.argument("tunnelName");
        
        if (config == null || config.isEmpty()) {
            result.error("INVALID_CONFIG", "WireGuard config is required", null);
            return;
        }
        
        if (tunnelName == null || tunnelName.isEmpty()) {
           tunnelName = "AxeVPN";
        }
        
        try {
            // Parse WireGuard config
            ByteArrayInputStream configStream = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8));
            currentWgConfig = Config.parse(configStream);
            currentTunnel = new WireGuardTunnel(tunnelName, currentWgConfig);
            
            Log.i(TAG, "Connecting to WireGuard tunnel: " + tunnelName);
            updateStage(WireGuardStage.PREPARING);
            
            // Request VPN permission
            Intent intent = VpnService.prepare(context);
            if (intent != null && activity != null) {
                activity.startActivityForResult(intent, VPN_REQUEST_CODE);
                result.success(null);
            } else {
                // Permission already granted, start VPN
                startWireGuardVPN();
                result.success(null);
            }
        } catch (BadConfigException e) {
            Log.e(TAG, "Invalid WireGuard config", e);
            result.error("INVALID_CONFIG", "Failed to parse WireGuard config: " + e.getMessage(), null);
        } catch (Exception e) {
            Log.e(TAG, "Connection failed", e);
            result.error("CONNECTION_FAILED", e.getMessage(), null);
        }
    }

    /**
     * Disconnect from WireGuard VPN
     */
    private void handleDisconnect(Result result) {
        Log.i(TAG, "Disconnecting WireGuard VPN");
        updateStage(WireGuardStage.DISCONNECTING);
        
// Stop WireGuard service
        stopWireGuardVPN();
        
        updateStage(WireGuardStage.DISCONNECTED);
        result.success(null);
    }

    /**
     * Get current VPN status
     */
    private void handleStatus(Result result) {
        try {
            JSONObject status = new JSONObject();
            status.put("duration", "0");
            status.put("last_packet_receive", String.valueOf(lastHandshake));
            status.put("byte_in", String.valueOf(bytesIn));
            status.put("byte_out", String.valueOf(bytesOut));
            result.success(status.toString());
        } catch (JSONException e) {
            result.error("STATUS_ERROR", e.getMessage(), null);
        }
    }

    /**
     * Get current VPN stage
     */
    private void handleStage(Result result) {
        result.success(currentStage.getValue());
    }

    /**
     * Start WireGuard VPN connection with GoBackend
     */
    private void startWireGuardVPN() {
        if (currentTunnel == null || currentWgConfig == null) {
            Log.e(TAG, "Cannot start VPN: no tunnel/config configured");
            updateStage(WireGuardStage.ERROR);
            return;
        }
        
        updateStage(WireGuardStage.CONNECTING);
        
        new Thread(() -> {
            try {
                // Start tunnel using GoBackend
                backend.setState(currentTunnel, Tunnel.State.UP, currentWgConfig);
                
                updateStage(WireGuardStage.CONNECTED);
                Log.i(TAG, "WireGuard tunnel established: " + currentTunnel.getName());
                
                // Start statistics monitoring
                startStatsMonitoring();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to start WireGuard tunnel", e);
                updateStage(WireGuardStage.ERROR);
            }
        }).start();
    }

    /**
     * Stop WireGuard VPN connection
     */
    private void stopWireGuardVPN() {
        if (currentTunnel == null) {
            Log.w(TAG, "No active tunnel to stop");
            return;
        }
        
        new Thread(() -> {
            try {
                backend.setState(currentTunnel, Tunnel.State.DOWN, null);
                currentTunnel = null;
                currentWgConfig = null;
                bytesIn = 0;
                bytesOut = 0;
                lastHandshake = 0;
                Log.i(TAG, "WireGuard tunnel stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping tunnel", e);
            }
        }).start();
    }
    
    /**
     * Monitor WireGuard tunnel statistics
     */
    private void startStatsMonitoring() {
        new Thread(() -> {
            while (currentTunnel != null && currentStage == WireGuardStage.CONNECTED) {
                try {
                    Statistics stats = backend.getStatistics(currentTunnel);
                    if (stats != null) {
                        // Get total stats for all peers
                        com.wireguard.crypto.Key[] peers = stats.peers();
                        for (com.wireguard.crypto.Key peer : peers) {
                            Statistics.PeerStats peerStats = stats.peer(peer);
                            if (peerStats != null) {
                                bytesIn += peerStats.rxBytes();
                                bytesOut += peerStats.txBytes();
                                lastHandshake = peerStats.latestHandshakeEpochMillis();
                            }
                        }
                    }
                    Thread.sleep(1000); // Update every second
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error getting stats", e);
                }
            }
        }).start();
    }

    /**
     * Update connection stage and notify Flutter
     */
    private void updateStage(WireGuardStage stage) {
        currentStage = stage;
        if (vpnStageSink != null) {
            vpnStageSink.success(stage.getValue());
        }
        Log.d(TAG, "Stage updated: " + stage.getValue());
    }

    /**
     * Called when VPN permission is granted
     */
    public static void connectWhileGranted(boolean granted) {
        if (granted) {
            Log.i(TAG, "VPN permission granted");
            // Permission granted, connection will be handled by startWireGuardVPN()
        } else {
            Log.w(TAG, "VPN permission denied");
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        vpnControlMethod.setMethodCallHandler(null);
        vpnControlMethod = null;
        vpnStageEvent.setStreamHandler(null);
        vpnStageEvent = null;
        Log.i(TAG, "WireGuard plugin detached from engine");
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        Log.d(TAG, "Attached to activity");
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }
    
    /**
     * WireGuard Tunnel implementation
     */
    private static class WireGuardTunnel implements Tunnel {
        private final String name;
        private final Config config;
        private Tunnel.State state = Tunnel.State.DOWN;
        
        WireGuardTunnel(String name, Config config) {
            this.name = name;
            this.config = config;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public void onStateChange(Tunnel.State newState) {
            state = newState;
        }
        
        public Tunnel.State getState() {
            return state;
        }
    }
}
