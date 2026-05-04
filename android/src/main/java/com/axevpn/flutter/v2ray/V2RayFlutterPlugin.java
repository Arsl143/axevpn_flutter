package com.axevpn.flutter.v2ray;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import org.json.JSONObject;

/**
 * AxeVPN Flutter Plugin – V2Ray/Xray Integration
 *
 * Provides VLESS, VMess, Trojan and Shadowsocks connectivity via the Xray-core
 * (or V2Ray-core) running as a local SOCKS5 proxy tunnelled through a TUN
 * interface managed by Android's VpnService API.
 *
 * Method-channel: com.axevpn.flutter.v2ray/vpncontrol
 * Event-channel:  com.axevpn.flutter.v2ray/vpnstage
 *
 * Supported methods:
 *  - initialize({groupIdentifier, providerBundleIdentifier, localizedDescription})
 *  - connect({config_json, name, sub_protocol, bypass_packages})
 *  - disconnect()
 *  - status()  → JSON string
 *  - stage()   → String (disconnected | preparing | connecting | connected | disconnecting | error | denied)
 *
 * Version: 3.0.0
 */
public class V2RayFlutterPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler,
        io.flutter.plugin.common.PluginRegistry.ActivityResultListener {

    private static final String TAG = "V2RayPlugin";
    private static final int VPN_REQUEST_CODE = 26;

    static final String METHOD_CHANNEL = "com.axevpn.flutter.v2ray/vpncontrol";
    static final String EVENT_CHANNEL = "com.axevpn.flutter.v2ray/vpnstage";

    private MethodChannel methodChannel;
    private EventChannel eventChannel;
    private EventChannel.EventSink stageSink;

    private Activity activity;
    private Context context;

    private String currentStage = "disconnected";
    private String pendingConfigJson;
    private String pendingTunnelName;
    private Result pendingResult;

    /** Receives stage-change broadcasts from AxeV2RayVpnService. */
    private final BroadcastReceiver stageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String stage = intent.getStringExtra(AxeV2RayVpnService.EXTRA_STAGE);
            if (stage != null) updateStage(stage);
        }
    };

    // ── FlutterPlugin ─────────────────────────────────────────────────────

    @Override
    public void onAttachedToEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        context = binding.getApplicationContext();

        methodChannel = new MethodChannel(binding.getBinaryMessenger(), METHOD_CHANNEL);
        methodChannel.setMethodCallHandler(this);

        eventChannel = new EventChannel(binding.getBinaryMessenger(), EVENT_CHANNEL);
        eventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object arguments, EventChannel.EventSink events) {
                stageSink = events;
            }

            @Override
            public void onCancel(Object arguments) {
                stageSink = null;
            }
        });

        // Register receiver for stage broadcasts from AxeV2RayVpnService.
        IntentFilter filter = new IntentFilter(AxeV2RayVpnService.BROADCAST_STAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(stageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(stageReceiver, filter);
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        methodChannel.setMethodCallHandler(null);
        try { context.unregisterReceiver(stageReceiver); } catch (Exception ignored) {}
    }

    // ── ActivityAware ─────────────────────────────────────────────────────

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        binding.addActivityResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        binding.addActivityResultListener(this);
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }

    // ── ActivityResultListener ────────────────────────────────────────────

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                startV2RayService();
            } else {
                updateStage("denied");
                if (pendingResult != null) {
                    pendingResult.success(null);
                    pendingResult = null;
                }
            }
            return true;
        }
        return false;
    }

    // ── MethodCallHandler ─────────────────────────────────────────────────

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "initialize":
                // Initialization: prepare internal state, no VPN permission needed yet.
                updateStage("disconnected");
                result.success(null);
                break;

            case "connect":
                handleConnect(call, result);
                break;

            case "disconnect":
                handleDisconnect(result);
                break;

            case "status":
                result.success(buildStatusJson());
                break;

            case "stage":
                result.success(currentStage);
                break;

            default:
                result.notImplemented();
        }
    }

    // ── Connection logic ──────────────────────────────────────────────────

    private void handleConnect(@NonNull MethodCall call, @NonNull Result result) {
        pendingConfigJson = call.argument("config_json");
        pendingTunnelName = call.argument("name");
        pendingResult = result;

        if (pendingConfigJson == null || pendingConfigJson.isEmpty()) {
            result.error("INVALID_CONFIG", "config_json must not be empty", null);
            pendingResult = null;
            return;
        }

        updateStage("preparing");

        // Request VPN permission if not already granted.
        Intent vpnIntent = VpnService.prepare(context);
        if (vpnIntent != null) {
            if (activity != null) {
                activity.startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
            } else {
                updateStage("error");
                result.error("NO_ACTIVITY", "Activity is not available to request VPN permission", null);
                pendingResult = null;
            }
        } else {
            startV2RayService();
        }
    }

    private void startV2RayService() {
        updateStage("connecting");
        try {
            Intent serviceIntent = new Intent(context, AxeV2RayVpnService.class);
            serviceIntent.putExtra(AxeV2RayVpnService.EXTRA_CONFIG_JSON, pendingConfigJson);
            serviceIntent.putExtra(AxeV2RayVpnService.EXTRA_TUNNEL_NAME, pendingTunnelName);
            serviceIntent.setAction(AxeV2RayVpnService.ACTION_CONNECT);
            context.startService(serviceIntent);
            if (pendingResult != null) {
                pendingResult.success(null);
                pendingResult = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start V2Ray service", e);
            updateStage("error");
            if (pendingResult != null) {
                pendingResult.error("SERVICE_ERROR", e.getMessage(), null);
                pendingResult = null;
            }
        }
    }

    private void handleDisconnect(@NonNull Result result) {
        try {
            updateStage("disconnecting");
            Intent serviceIntent = new Intent(context, AxeV2RayVpnService.class);
            serviceIntent.setAction(AxeV2RayVpnService.ACTION_DISCONNECT);
            context.startService(serviceIntent);
            updateStage("disconnected");
            result.success(null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop V2Ray service", e);
            result.error("DISCONNECT_ERROR", e.getMessage(), null);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    void updateStage(String stage) {
        currentStage = stage;
        if (stageSink != null) {
            activity.runOnUiThread(() -> stageSink.success(stage));
        }
    }

    private String buildStatusJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("state", currentStage);
            // Traffic stats are filled by the VPN service via broadcast in full impl.
            json.put("bytes_in", 0);
            json.put("bytes_out", 0);
            return json.toString();
        } catch (Exception e) {
            return "{\"state\":\"unknown\"}";
        }
    }
}
