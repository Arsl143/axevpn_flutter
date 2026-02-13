import Flutter
import UIKit
import NetworkExtension

/**
 * AxeVPN Flutter Plugin - WireGuard Integration for iOS
 * 
 * This plugin provides comprehensive WireGuard connectivity for iOS applications
 * using NetworkExtension framework with WireGuardKit.
 * 
 * Version: 2.0.0
 * Package: com.axevpn.flutter.wireguard
 * 
 * Features:
 * - WireGuard connection management via NEPacketTunnelProvider
 * - Real-time connection status monitoring
 * - Modern high-performance VPN protocol
 * - iOS Network Extension with WireGuardKit support
 * 
 * @author AxeVPN Team
 * @since 2.0.0
 */
public class WireGuardFlutterPlugin: NSObject, FlutterPlugin {
    private static var manager: WireGuardManager! = WireGuardManager()
    
    private static var EVENT_CHANNEL_VPN_STAGE = "com.axevpn.flutter.wireguard/vpnstage"
    private static var METHOD_CHANNEL_VPN_CONTROL = "com.axevpn.flutter.wireguard/vpncontrol"
     
    public static var stage: FlutterEventSink?
    private var initialized: Bool = false
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = WireGuardFlutterPlugin()
        instance.onRegister(registrar)
    }
    
    public func onRegister(_ registrar: FlutterPluginRegistrar) {
        let vpnControlM = FlutterMethodChannel(
            name: WireGuardFlutterPlugin.METHOD_CHANNEL_VPN_CONTROL,
            binaryMessenger: registrar.messenger()
        )
        let vpnStageE = FlutterEventChannel(
            name: WireGuardFlutterPlugin.EVENT_CHANNEL_VPN_STAGE,
            binaryMessenger: registrar.messenger()
        )
        
        vpnStageE.setStreamHandler(WireGuardStageHandler())
        vpnControlM.setMethodCallHandler { (call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
            switch call.method {
            case "status":
                WireGuardFlutterPlugin.manager.getStatistics()
                let statsJson = UserDefaults.init(suiteName: WireGuardFlutterPlugin.manager.groupIdentifier)?
                    .string(forKey: "wg_stats") ?? "{}"
                result(statsJson)
                break
                
            case "stage":
                result(WireGuardFlutterPlugin.manager.currentStatus())
                break
                
            case "initialize":
                let providerBundleIdentifier = (call.arguments as? [String: Any])?["providerBundleIdentifier"] as? String
                let localizedDescription = (call.arguments as? [String: Any])?["localizedDescription"] as? String
                let groupIdentifier = (call.arguments as? [String: Any])?["groupIdentifier"] as? String
                
                if providerBundleIdentifier == nil {
                    result(FlutterError(
                        code: "-2",
                        message: "providerBundleIdentifier content empty or null",
                        details: nil
                    ))
                    return
                }
                
                if localizedDescription == nil {
                    result(FlutterError(
                        code: "-3",
                        message: "localizedDescription content empty or null",
                        details: nil
                    ))
                    return
                }
                
                if groupIdentifier == nil {
                    result(FlutterError(
                        code: "-4",
                        message: "groupIdentifier content empty or null",
                        details: nil
                    ))
                    return
                }
                
                WireGuardFlutterPlugin.manager.groupIdentifier = groupIdentifier
                WireGuardFlutterPlugin.manager.localizedDescription = localizedDescription
                WireGuardFlutterPlugin.manager.providerBundleIdentifier = providerBundleIdentifier
                
                WireGuardFlutterPlugin.manager.loadProviderManager { (err: Error?) in
                    if err == nil {
                        result(WireGuardFlutterPlugin.manager.currentStatus())
                    } else {
                        result(FlutterError(
                            code: "-4",
                            message: err?.localizedDescription ?? "Failed to load provider",
                            details: err?.localizedDescription
                        ))
                    }
                }
                
                self.initialized = true
                break
                
            case "disconnect":
                WireGuardFlutterPlugin.manager.stopVPN()
                result(nil)
                break
                
            case "connect":
                if !self.initialized {
                    result(FlutterError(
                        code: "-1",
                        message: "WireGuard engine needs to be initialized",
                        details: nil
                    ))
                    return
                }
                
                let config = (call.arguments as? [String: Any])?["config"] as? String
                let tunnelName = (call.arguments as? [String: Any])?["tunnelName"] as? String ?? "AxeVPN"
                
                if config == nil {
                    result(FlutterError(
                        code: "-2",
                        message: "Config is empty or null",
                        details: "WireGuard config cannot be null"
                    ))
                    return
                }
                
                WireGuardFlutterPlugin.manager.configureVPN(
                    config: config!,
                    tunnelName: tunnelName,
                    completion: { (success: Error?) -> Void in
                        if success == nil {
                            result(nil)
                        } else {
                            result(FlutterError(
                                code: "99",
                                message: "Connection failed or permission denied",
                                details: success?.localizedDescription
                            ))
                        }
                    }
                )
                break
                
            case "dispose":
                self.initialized = false
                result(nil)
                break
                
            default:
                result(FlutterMethodNotImplemented)
                break
            }
        }
    }
}

/**
 * WireGuard Stage Event Handler
 */
class WireGuardStageHandler: NSObject, FlutterStreamHandler {
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        WireGuardFlutterPlugin.stage = events
        return nil
    }
    
    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        WireGuardFlutterPlugin.stage = nil
        return nil
    }
}
