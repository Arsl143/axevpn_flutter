import Foundation
import NetworkExtension

/**
 * WireGuard VPN Manager for iOS
 * 
 * Manages WireGuard VPN connections using NetworkExtension framework.
 * Requires WireGuardKit integration for full functionality.
 */
class WireGuardManager {
    var providerManager: NETunnelProviderManager?
    var groupIdentifier: String
    var onStageChanged: ((String) -> Void)?
    
    private var currentStage: String = "disconnected"
    private var isConnecting = false
    
    init(groupIdentifier: String) {
        self.groupIdentifier = groupIdentifier
        subscribeToStatusChanges()
    }
    
    /**
     * Connect to WireGuard VPN
     */
    func connect(config: String, tunnelName: String, completion: @escaping (_ error: Error?) -> Void) {
        updateStage("preparing")
        
        NETunnelProviderManager.loadAllFromPreferences { (managers, error) in
            if error != nil {
                self.updateStage("error")
                completion(error)
                return
            }
            
            // Find existing manager or create new one
            self.providerManager = managers?.first ?? NETunnelProviderManager()
            self.providerManager?.localizedDescription = tunnelName
            
            let providerProtocol = NETunnelProviderProtocol()
            providerProtocol.providerBundleIdentifier = "com.axevpn.wireguard.extension" // ✅ Update with your bundle ID
            providerProtocol.serverAddress = "WireGuard"
            providerProtocol.providerConfiguration = ["config": config]
            
            self.providerManager?.protocolConfiguration = providerProtocol
            self.providerManager?.isEnabled = true
            
            // Save config to shared defaults
            let sharedDefaults = UserDefaults(suiteName: self.groupIdentifier)
            sharedDefaults?.set(config, forKey: "wg_config")
            sharedDefaults?.synchronize()
            
            self.providerManager?.saveToPreferences { error in
                if error != nil {
                    self.updateStage("error")
                    completion(error)
                    return
                }
                
                self.providerManager?.loadFromPreferences { error in
                    if error != nil {
                        self.updateStage("error")
                        completion(error)
                        return
                    }
                    
                    // Start VPN
                    self.startVPN(completion: completion)
                }
            }
        }
    }     * Start VPN connection
     */
    private func startVPN(completion: @escaping (_ error: Error?) -> Void) {
        guard let providerManager = providerManager else {
            completion(NSError(
                domain: "WireGuardManager",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Provider manager not initialized"]
            ))
            return
        }
        
        isConnecting = true
        updateStage("connecting")
        
        do {
            try providerManager.connection.startVPNTunnel()
            completion(nil)
        } catch let error {
            isConnecting = false
            updateStage("error")
            completion(error)
        }
    }
    
    /**
     * Stop VPN connection
     */
    /**
     * Start VPN connection
     */
    private func startVPN(completion: @escaping (_ error: Error?) -> Void) {
        guard let providerManager = providerManager else {
            let error = NSError(domain: "WireGuardManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "No provider manager"])
            self.updateStage("error")
            completion(error)
            return
        }
        
        isConnecting = true
        updateStage("connecting")
        
        do {
            try providerManager.connection.startVPNTunnel()
            completion(nil)
        } catch {
            self.updateStage("error")
            isConnecting = false
            completion(error)
        }
    }
    
    /**
     * Disconnect VPN
     */
    func disconnect() {
       updateStage("disconnecting")
        providerManager?.connection.stopVPNTunnel()
    }
    
    /**
     * Get current stage
     */
    func getStage() -> String {
        return currentStage
    }
    
    /**
     * Get connection status JSON
     */
    func getStatus() -> String {
        var stats: [String: String] = [
            "duration": "0",
            "byte_in": "0",
            "byte_out": "0",
            "last_packet_receive": "0"
        ]
        
        // Try to read stats from shared defaults (if network extension updates them)
        if let sharedDefaults = UserDefaults(suiteName: groupIdentifier),
           let savedStats = sharedDefaults.string(forKey: "wg_stats") {
            return savedStats
        }
        
        // Return empty stats as JSON string
        if let jsonData = try? JSONSerialization.data(withJSONObject: stats),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            return jsonString
        }
        
        return "{\"duration\":\"0\",\"byte_in\":\"0\",\"byte_out\":\"0\",\"last_packet_receive\":\"0\"}"
    }
    
    /**
     * Subscribe to VPN status changes
     */
    private func subscribeToStatusChanges() {
        NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange,
            object: nil,
            queue: OperationQueue.main
        ) { [weak self] notification in
            self?.statusChanged()
        }
    }
    
    /**
     * Handle status changes
     */
    private func statusChanged() {
        guard let providerManager = providerManager else {
            return
        }
        
        var stage: String
        switch providerManager.connection.status {
        case .connecting:
            stage = "connecting"
        case .connected:
            stage = "connected"
            isConnecting = false
        case .disconnecting:
            stage = "disconnecting"
        case .disconnected:
            stage = "disconnected"
            isConnecting = false
        case .reasserting:
            stage = "connecting"
        case .invalid:
            stage = "error"
        @unknown default:
            stage = "unknown"
        }
        
        updateStage(stage)
    }
    
    /**
     * Update stage and notify Flutter
     */
    private func updateStage(_ stage: String) {
        currentStage = stage
        onStageChanged?(stage)
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
}
