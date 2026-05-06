package com.axevpn.flutter.openconnect;

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
 * AxeVPN Flutter Plugin – OpenConnect Integration
 *
 * Provides Cisco AnyConnect-compatible SSL VPN connectivity using the
 * open-source OpenConnect client (ocrunner / libopenconnect).
 *
 * Method-channel: com.axevpn.flutter.openconnect/vpncontrol
 * Event-channel:  com.axevpn.flutter.openconnect/vpnstage
 *
 * Supported methods:
 *  - initialize({groupIdentifier, providerBundleIdentifier, localizedDescription})
 *  - connect({server_url, name, username, password, cert_path, cert_password,
 *             ca_path, auth_group, servercert, auth_method, bypass_packages})
 *  - disconnect()
 *  - status()  → JSON string
 *  - stage()   → String (disconnected | preparing | connecting | authenticating |
 *                         connected | disconnecting | error | denied)
 *
 * Version: 3.0.0
 */
public class OpenConnectFlutterPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler,
        io.flutter.plugin.common.PluginRegistry.ActivityResultListener {

    private static final String TAG = "OpenConnectPlugin";
    private static final int VPN_REQUEST_CODE = 27;

    static final String METHOD_CHANNEL = "com.axevpn.flutter.openconnect/vpncontrol";
    static final String EVENT_CHANNEL  = "com.axevpn.flutter.openconnect/vpnstage";

    private MethodChannel methodChannel;
    private EventChannel eventChannel;
    private EventChannel.EventSink stageSink;

    private Activity activity;
    private Context context;

    private String currentStage = "disconnected";

    /** Receives stage-change broadcasts from AxeOpenConnectVpnService. */
    private final BroadcastReceiver stageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String stage = intent.getStringExtra(AxeOpenConnectVpnService.EXTRA_STAGE);
            if (stage != null) updateStage(stage);
        }
    };

    // Pending connection parameters (awaiting VPN permission result)
    private String pendingServerUrl;
    private String pendingName;
    private String pendingUsername;
    private String pendingPassword;
    private String pendingCertPath;
    private String pendingCertPassword;
    private String pendingCaPath;
    private String pendingAuthGroup;
    private String pendingServercert;
    private Result pendingResult;

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

        // Register receiver for stage broadcasts from AxeOpenConnectVpnService.
        IntentFilter filter = new IntentFilter(AxeOpenConnectVpnService.BROADCAST_STAGE);
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
    public void onDetachedFromActivityForConfigChanges() { activity = null; }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        binding.addActivityResultListener(this);
    }

    @Override
    public void onDetachedFromActivity() { activity = null; }

    // ── ActivityResultListener ────────────────────────────────────────────

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                startOpenConnectService();
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
        pendingServerUrl   = call.argument("server_url");
        pendingName        = call.argument("name");
        pendingUsername    = call.argument("username");
        pendingPassword    = call.argument("password");
        pendingCertPath    = call.argument("cert_path");
        pendingCertPassword = call.argument("cert_password");
        pendingCaPath      = call.argument("ca_path");
        pendingAuthGroup   = call.argument("auth_group");
        pendingServercert  = call.argument("servercert");
        pendingResult      = result;

        if (pendingServerUrl == null || pendingServerUrl.isEmpty()) {
            result.error("INVALID_URL", "server_url must not be empty", null);
            pendingResult = null;
            return;
        }

        updateStage("preparing");

        Intent vpnIntent = VpnService.prepare(context);
        if (vpnIntent != null) {
            if (activity != null) {
                activity.startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
            } else {
                updateStage("error");
                result.error("NO_ACTIVITY", "Activity not available to request VPN permission", null);
                pendingResult = null;
            }
        } else {
            startOpenConnectService();
        }
    }

    private void startOpenConnectService() {
        updateStage("connecting");
        try {
            Intent serviceIntent = new Intent(context, AxeOpenConnectVpnService.class);
            serviceIntent.setAction(AxeOpenConnectVpnService.ACTION_CONNECT);
            serviceIntent.putExtra(AxeOpenConnectVpnService.EXTRA_SERVER_URL,    pendingServerUrl);
            serviceIntent.putExtra(AxeOpenConnectVpnService.EXTRA_TUNNEL_NAME,   pendingName);
            serviceIntent.putExtra(AxeOpenConnectVpnService.EXTRA_USERNAME,      pendingUsername);
            serviceIntent.putExtra(AxeOpenConnectVpnService.EXTRA_PASSWORD,      pendingPassword);
            serviceIntent.putExtra(AxeOpenConnectVpnService.EXTRA_CERT_PATH,     pendingCertPath);
            serviceIntent.putExtra(AxeOpenConnectVpnService.EXTRA_CERT_PASSWORD, pendingCertPassword);
            serviceIntent.putExtra(AxeOpenConnectVpnService.EXTRA_CA_PATH,       pendingCaPath);
            serviceIntent.putExtra(AxeOpenConnectVpnService.EXTRA_AUTH_GROUP,    pendingAuthGroup);
            serviceIntent.putExtra(AxeOpenConnectVpnService.EXTRA_SERVERCERT,    pendingServercert);
            context.startService(serviceIntent);

            if (pendingResult != null) {
                pendingResult.success(null);
                pendingResult = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start OpenConnect service", e);
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
            Intent serviceIntent = new Intent(context, AxeOpenConnectVpnService.class);
            serviceIntent.setAction(AxeOpenConnectVpnService.ACTION_DISCONNECT);
            context.startService(serviceIntent);
            updateStage("disconnected");
            result.success(null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop OpenConnect service", e);
            result.error("DISCONNECT_ERROR", e.getMessage(), null);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    void updateStage(String stage) {
        currentStage = stage;
        if (stageSink != null) {
            // Use the main looper directly so stages reach Flutter even when
            // activity is momentarily null (e.g. VPN overlay / config change).
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> { if (stageSink != null) stageSink.success(stage); });
        }
    }

    private String buildStatusJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("state", currentStage);
            json.put("bytes_in", 0);
            json.put("bytes_out", 0);
            return json.toString();
        } catch (Exception e) {
            return "{\"state\":\"unknown\"}";
        }
    }
}
