import Flutter
import UIKit
import NetworkExtension

public class SwiftAxeVPNFlutterPlugin: NSObject, FlutterPlugin {
    private static var utils : VPNUtils! = VPNUtils()
    private static var wgManager: WireGuardManager?
    
    // ✅ OpenVPN channels
    private static var EVENT_CHANNEL_VPN_STAGE = "com.axevpn.flutter.openvpn/vpnstage"
    private static var METHOD_CHANNEL_VPN_CONTROL = "com.axevpn.flutter.openvpn/vpncontrol"
    
    // ✅ WireGuard channels  
    private static var EVENT_CHANNEL_WG_STAGE = "com.axevpn.flutter.wireguard/vpnstage"
    private static var METHOD_CHANNEL_WG_CONTROL = "com.axevpn.flutter.wireguard/vpncontrol"
     
    public static var stage: FlutterEventSink?
    public static var wgStage: FlutterEventSink?
    private var initialized : Bool = false
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = SwiftAxeVPNFlutterPlugin()
        instance.onRegister(registrar)
    }
    
    public func onRegister(_ registrar: FlutterPluginRegistrar){
        // ✅ OpenVPN channels
        let vpnControlM = FlutterMethodChannel(name: SwiftAxeVPNFlutterPlugin.METHOD_CHANNEL_VPN_CONTROL, binaryMessenger: registrar.messenger())
        let vpnStageE = FlutterEventChannel(name: SwiftAxeVPNFlutterPlugin.EVENT_CHANNEL_VPN_STAGE, binaryMessenger: registrar.messenger())
        
        vpnStageE.setStreamHandler(StageHandler())
        setupOpenVPNMethodHandler(vpnControlM)
        
        // ✅ WireGuard channels
        let wgControlM = FlutterMethodChannel(name: SwiftAxeVPNFlutterPlugin.METHOD_CHANNEL_WG_CONTROL, binaryMessenger: registrar.messenger())
        let wgStageE = FlutterEventChannel(name: SwiftAxeVPNFlutterPlugin.EVENT_CHANNEL_WG_STAGE, binaryMessenger: registrar.messenger())
        
        wgStageE.setStreamHandler(WireGuardStageHandler())
        setupWireGuardMethodHandler(wgControlM)
    }
    
    // ✅ OpenVPN method handler
    private func setupOpenVPNMethodHandler(_ channel: FlutterMethodChannel) {
        channel.setMethodCallHandler({(call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
            switch call.method {
            case "status":
                SwiftAxeVPNFlutterPlugin.utils.getTraffictStats()
                result(UserDefaults.init(suiteName: SwiftAxeVPNFlutterPlugin.utils.groupIdentifier)?.string(forKey: "connectionUpdate"))
                break;
            case "stage":
                result(SwiftAxeVPNFlutterPlugin.utils.currentStatus())
                break;
            case "diagnostics":
                result(SwiftAxeVPNFlutterPlugin.utils.diagnosticsSnapshot())
                break;
            case "clearDiagnostics":
                SwiftAxeVPNFlutterPlugin.utils.clearDiagnostics()
                result(nil)
                break;
            case "initialize":
                let providerBundleIdentifier: String? = (call.arguments as? [String: Any])?["providerBundleIdentifier"] as? String
                let localizedDescription: String? = (call.arguments as? [String: Any])?["localizedDescription"] as? String
                let groupIdentifier: String? = (call.arguments as? [String: Any])?["groupIdentifier"] as? String
                if providerBundleIdentifier == nil  {
                    result(FlutterError(code: "-2",
                                        message: "providerBundleIdentifier content empty or null",
                                        details: nil));
                    return;
                }
                if localizedDescription == nil  {
                    result(FlutterError(code: "-3",
                                        message: "localizedDescription content empty or null",
                                        details: nil));
                    return;
                }
                if groupIdentifier == nil  {
                    result(FlutterError(code: "-4",
                                        message: "groupIdentifier content empty or null",
                                        details: nil));
                    return;
                }
                SwiftAxeVPNFlutterPlugin.utils.groupIdentifier = groupIdentifier
                SwiftAxeVPNFlutterPlugin.utils.localizedDescription = localizedDescription
                SwiftAxeVPNFlutterPlugin.utils.providerBundleIdentifier = providerBundleIdentifier
                SwiftAxeVPNFlutterPlugin.utils.loadProviderManager{(err:Error?) in
                    if err == nil{
                        result(SwiftAxeVPNFlutterPlugin.utils.currentStatus())
                    }else{
                        result(FlutterError(code: "-4", message: err?.localizedDescription, details: err?.localizedDescription));
                    }
                }
                self.initialized = true
                break;
            case "disconnect":
                SwiftAxeVPNFlutterPlugin.utils.stopVPN()
                break;
            case "connect":
                if !self.initialized {
                    result(FlutterError(code: "-1",
                                        message: "VPNEngine need to be initialize",
                                        details: nil));
                }
                let config: String? = (call.arguments as? [String : Any])? ["config"] as? String
                let username: String? = (call.arguments as? [String : Any])? ["username"] as? String
                let password: String? = (call.arguments as? [String : Any])? ["password"] as? String
                if config == nil{
                    result(FlutterError(code: "-2",
                                        message:"Config is empty or nulled",
                                        details: "Config can't be nulled"))
                    return
                }
                
                SwiftAxeVPNFlutterPlugin.utils.configureVPN(config: config, username: username, password: password, completion: {(success:Error?) -> Void in
                    if(success == nil){
                        result(nil)
                    }else{
                        result(FlutterError(code: "99",
                                            message: "permission denied",
                                            details: success?.localizedDescription))
                    }
                })
                break;
            case "dispose":
                self.initialized = false
            default:
                break;
            }
        })
    }
    
    // ✅ WireGuard method handler
    private func setupWireGuardMethodHandler(_ channel: FlutterMethodChannel) {
        channel.setMethodCallHandler({(call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
            switch call.method {
            case "initialize":
                let groupIdentifier: String? = (call.arguments as? [String: Any])?["groupIdentifier"] as? String
                SwiftAxeVPNFlutterPlugin.wgManager = WireGuardManager(groupIdentifier: groupIdentifier ?? "group.com.axevpn")
                result(nil)
                break;
                
            case "connect":
                let config: String? = (call.arguments as? [String: Any])?["config"] as? String
                let tunnelName: String? = (call.arguments as? [String: Any])?["tunnelName"] as? String
                
                if config == nil || config!.isEmpty {
                    result(FlutterError(code: "INVALID_CONFIG", message: "WireGuard config required", details: nil))
                    return
                }
                
                SwiftAxeVPNFlutterPlugin.wgManager?.connect(config: config!, tunnelName: tunnelName ?? "AxeVPN") { error in
                    if error == nil {
                        result(nil)
                    } else {
                        result(FlutterError(code: "CONNECT_FAILED", message: error?.localizedDescription, details: nil))
                    }
                }
                break;
                
            case "disconnect":
                SwiftAxeVPNFlutterPlugin.wgManager?.disconnect()
                result(nil)
                break;
                
            case "status":
                let stats = SwiftAxeVPNFlutterPlugin.wgManager?.getStatus() ?? "{\"byte_in\":\"0\",\"byte_out\":\"0\",\"duration\":\"0\",\"last_packet_receive\":\"0\"}"
                result(stats)
                break;
                
            case "stage":
                let currentStage = SwiftAxeVPNFlutterPlugin.wgManager?.getStage() ?? "disconnected"
                result(currentStage)
                break;
                
            default:
                result(FlutterMethodNotImplemented)
                break;
            }
        })
    }
    
    
    class StageHandler: NSObject, FlutterStreamHandler {
        func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
            SwiftAxeVPNFlutterPlugin.utils.stage = events
            return nil
        }
        
        func onCancel(withArguments arguments: Any?) -> FlutterError? {
            SwiftAxeVPNFlutterPlugin.utils.stage = nil
            return nil
        }
    }
    
    // ✅ WireGuard stage handler
    class WireGuardStageHandler: NSObject, FlutterStreamHandler {
        func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
            SwiftAxeVPNFlutterPlugin.wgStage = events
            SwiftAxeVPNFlutterPlugin.wgManager?.onStageChanged = { stage in
                events(stage)
            }
            return nil
        }
        
        func onCancel(withArguments arguments: Any?) -> FlutterError? {
            SwiftAxeVPNFlutterPlugin.wgStage = nil
            SwiftAxeVPNFlutterPlugin.wgManager?.onStageChanged = nil
            return nil
        }
    }
    
    
}

@available(iOS 9.0, *)
class VPNUtils {
    var providerManager: NETunnelProviderManager!
    var providerBundleIdentifier : String?
    var localizedDescription : String?
    var groupIdentifier : String?
    var stage : FlutterEventSink!
    var vpnStageObserver : NSObjectProtocol?

    private var sharedDefaults: UserDefaults? {
        guard let groupIdentifier else { return nil }
        return UserDefaults(suiteName: groupIdentifier)
    }

    private func normalizedString(_ value: String?) -> String? {
        guard let value else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    func diagnosticsSnapshot() -> [String: Any] {
        [
            "vpnStage": normalizedString(sharedDefaults?.string(forKey: "vpnStage")) as Any,
            "lastError": normalizedString(sharedDefaults?.string(forKey: "lastError")) as Any,
            "lastLog": normalizedString(sharedDefaults?.string(forKey: "lastLog")) as Any,
            "connectedOn": normalizedString(sharedDefaults?.string(forKey: "connected_on")) as Any,
            "connectionUpdate": normalizedString(sharedDefaults?.string(forKey: "connectionUpdate")) as Any
        ]
    }

    func clearDiagnostics() {
        sharedDefaults?.removeObject(forKey: "vpnStage")
        sharedDefaults?.removeObject(forKey: "lastError")
        sharedDefaults?.removeObject(forKey: "lastLog")
        sharedDefaults?.removeObject(forKey: "connected_on")
        sharedDefaults?.removeObject(forKey: "connectionUpdate")
    }

    private func stageFromSharedDefaults() -> String? {
        guard let sharedStage = normalizedString(sharedDefaults?.string(forKey: "vpnStage")) else {
            return nil
        }

        switch sharedStage.uppercased() {
        case "CONNECTED":
            return "connected"
        case "CONNECTING":
            return "connecting"
        case "DISCONNECTED":
            return "disconnected"
        case "DISCONNECTING":
            return "disconnecting"
        case "RECONNECTING":
            return "reasserting"
        default:
            return sharedStage.lowercased()
        }
    }

    private func matchingManager(from managers: [NETunnelProviderManager]?) -> NETunnelProviderManager? {
        guard let managers else { return nil }

        return managers.first { manager in
            guard let tunnelProtocol = manager.protocolConfiguration as? NETunnelProviderProtocol else {
                return false
            }

            let sameBundleIdentifier = tunnelProtocol.providerBundleIdentifier == providerBundleIdentifier
            let sameDescription = localizedDescription == nil || manager.localizedDescription == localizedDescription
            return sameBundleIdentifier && sameDescription
        }
    }

    private func extractServerAddress(from config: String) -> String {
        for rawLine in config.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            guard line.hasPrefix("remote ") else { continue }

            let components = line.split(separator: " ")
            if components.count >= 2 {
                return String(components[1])
            }
        }

        return "OpenVPN"
    }
    
    func loadProviderManager(completion:@escaping (_ error : Error?) -> Void)  {
        NETunnelProviderManager.loadAllFromPreferences { (managers, error)  in
            if error == nil {
                self.providerManager = self.matchingManager(from: managers) ?? NETunnelProviderManager()
                completion(nil)
            } else {
                completion(error)
            }
        }
    }
    
    func onVpnStatusChanged(notification : NEVPNStatus) {
        stage?(onVpnStatusChangedString(notification: notification) ?? "null")
    }
    
    func onVpnStatusChangedString(notification : NEVPNStatus?) -> String?{
        if notification == nil {
            return "disconnected"
        }
        switch notification! {
        case NEVPNStatus.connected:
            return "connected";
        case NEVPNStatus.connecting:
            return "connecting";
        case NEVPNStatus.disconnected:
            return "disconnected";
        case NEVPNStatus.disconnecting:
            return "disconnecting";
        case NEVPNStatus.invalid:
            return "invalid";
        case NEVPNStatus.reasserting:
            return "reasserting";
        default:
            return "";
        }
    }
    
    func currentStatus() -> String? {
        if let sharedStage = stageFromSharedDefaults() {
            return sharedStage
        }

        if self.providerManager != nil {
            return onVpnStatusChangedString(notification: self.providerManager.connection.status)}
        else{
            return "disconnected"
        }
    }
    
    func configureVPN(config: String?, username : String?,password : String?,completion:@escaping (_ error : Error?) -> Void) {
        guard let config = normalizedString(config) else {
            completion(NSError(domain: "AxeVPN", code: -2, userInfo: [NSLocalizedDescriptionKey: "Config is empty"]))
            return
        }

        if self.providerManager == nil {
            self.providerManager = NETunnelProviderManager()
        }

        let sanitizedUsername = normalizedString(username)
        let sanitizedPassword = normalizedString(password)
        sharedDefaults?.removeObject(forKey: "vpnStage")
        sharedDefaults?.removeObject(forKey: "lastError")

        self.providerManager?.loadFromPreferences { error in
            if error == nil {
                let tunnelProtocol = NETunnelProviderProtocol()
                tunnelProtocol.serverAddress = self.extractServerAddress(from: config)
                tunnelProtocol.providerBundleIdentifier = self.providerBundleIdentifier
                var providerConfiguration: [String: Data] = [
                    "config": config.data(using: .utf8) ?? Data(),
                    "groupIdentifier": (self.groupIdentifier ?? "").data(using: .utf8) ?? Data()
                ]

                if let sanitizedUsername {
                    providerConfiguration["username"] = sanitizedUsername.data(using: .utf8)
                }

                if let sanitizedPassword {
                    providerConfiguration["password"] = sanitizedPassword.data(using: .utf8)
                }

                tunnelProtocol.providerConfiguration = providerConfiguration
                tunnelProtocol.disconnectOnSleep = false
                self.providerManager.protocolConfiguration = tunnelProtocol
                self.providerManager.localizedDescription = self.localizedDescription // the title of the VPN profile which will appear on Settings
                self.providerManager.isEnabled = true
                self.providerManager.saveToPreferences(completionHandler: { (error) in
                    if error == nil  {
                        self.providerManager.loadFromPreferences(completionHandler: { (error) in
                            if error != nil {
                                completion(error);
                                return;
                            }
                            do {
                                if self.vpnStageObserver != nil {
                                    NotificationCenter.default.removeObserver(self.vpnStageObserver!,
                                                                              name: NSNotification.Name.NEVPNStatusDidChange,
                                                                              object: nil)
                                }
                                self.vpnStageObserver = NotificationCenter.default.addObserver(forName: NSNotification.Name.NEVPNStatusDidChange,
                                                                                               object: nil ,
                                                                                               queue: nil) { [weak self] notification in
                                    let nevpnconn = notification.object as! NEVPNConnection
                                    let status = nevpnconn.status
                                    self?.onVpnStatusChanged(notification: status)
                                }
                                
                                if let sanitizedUsername, let sanitizedPassword {
                                    let options: [String : NSObject] = [
                                        "username": sanitizedUsername as NSString,
                                        "password": sanitizedPassword as NSString
                                    ]
                                    try self.providerManager.connection.startVPNTunnel(options: options)
                                }else{
                                    try self.providerManager.connection.startVPNTunnel()
                                }
                                completion(nil);
                            } catch let error {
                                self.stopVPN()
                                print("Error info: \(error)")
                                completion(error);
                            }
                        })
                    } else {
                        completion(error);
                    }
                })
            }
        }
        
        
    }
    
    func stopVPN() {
        self.providerManager.connection.stopVPNTunnel();
    }
    
    func getTraffictStats(){
        if let session = self.providerManager?.connection as? NETunnelProviderSession {
            do {
                try session.sendProviderMessage("OPENVPN_STATS".data(using: .utf8)!) {(data) in
                    //Do nothing
                }
            } catch {
            // some error
            }
        }
    }
}
