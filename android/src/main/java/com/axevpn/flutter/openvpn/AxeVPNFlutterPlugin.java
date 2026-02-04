package com.axevpn.flutter.openvpn;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

import androidx.annotation.NonNull;

import java.util.ArrayList;

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
public class AxeVPNFlutterPlugin implements FlutterPlugin, ActivityAware {

    private MethodChannel vpnControlMethod;
    private EventChannel vpnStageEvent;
    private EventChannel.EventSink vpnStageSink;

    // ✅ Updated channel names for AxeVPN
    private static final String EVENT_CHANNEL_VPN_STAGE = "com.axevpn.flutter.openvpn/vpnstage";
    private static final String METHOD_CHANNEL_VPN_CONTROL = "com.axevpn.flutter.openvpn/vpncontrol";

    private static String config = "", username = "", password = "", name = "";
    private static ArrayList<String> bypassPackages;
    
    @SuppressLint("StaticFieldLeak")
    private static VPNHelper vpnHelper;
    private Activity activity;
    Context mContext;

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
        vpnStageEvent = new EventChannel(binding.getBinaryMessenger(), EVENT_CHANNEL_VPN_STAGE);
        vpnControlMethod = new MethodChannel(binding.getBinaryMessenger(), METHOD_CHANNEL_VPN_CONTROL);

        // Setup stage event stream handler
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

        // Setup VPN control method handler
        vpnControlMethod.setMethodCallHandler((call, result) -> {
            try {
                switch (call.method) {
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
                        result.success(updateVPNStages());
                        break;

                    case "disconnect":
                        if (vpnHelper == null) {
                            result.error("-1", "VPNEngine needs to be initialized", "Call initialize() first");
                            return;
                        }
                        vpnHelper.stopVPN();
                        updateStage("disconnected");
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

        mContext = binding.getApplicationContext();
    }

    /**
     * Update VPN stage and notify listeners
     * @param stage Current VPN connection stage
     */
    public void updateStage(String stage) {
        if (stage == null) {
            stage = "idle";
        }
        if (vpnStageSink != null) {
            vpnStageSink.success(stage.toLowerCase());
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
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        // Activity temporarily detached
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
    }

    @Override
    public void onDetachedFromActivity() {
        // Activity detached
    }
}
