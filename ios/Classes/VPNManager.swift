//
// VPNManager.swift
// OpenVPN Flutter Plugin - iOS VPN Extension Support
//
// Created for iOS 16.0+ with NetworkExtension framework
// Handles VPN connection lifecycle and tunnel management
//

import Foundation
import NetworkExtension

@available(iOS 16.0, *)
public class VPNManager: NSObject {
    
    // MARK: - Properties
    
    /// Shared singleton instance
    public static let shared = VPNManager()
    
    /// VPN manager instance
    private var vpnManager: NEVPNManager?
    
    /// Connection status callback
    public var onStatusChanged: ((NEVPNStatus) -> Void)?
    
    /// Current VPN status
    public private(set) var currentStatus: NEVPNStatus = .invalid
    
    // MARK: - Initialization
    
    private override init() {
        super.init()
        setupVPNManager()
        observeVPNStatus()
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
    
    // MARK: - Setup
    
    private func setupVPNManager() {
        vpnManager = NEVPNManager.shared()
    }
    
    private func observeVPNStatus() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(vpnStatusDidChange(_:)),
            name: .NEVPNStatusDidChange,
            object: nil
        )
    }
    
    // MARK: - VPN Configuration
    
    /// Configure OpenVPN with profile data
    /// - Parameters:
    ///   - config: OpenVPN configuration string (.ovpn content)
    ///   - username: Optional username for authentication
    ///   - password: Optional password for authentication
    ///   - completion: Completion handler with success/error
    public func configure(
        config: String,
        username: String? = nil,
        password: String? = nil,
        completion: @escaping (Error?) -> Void
    ) {
        guard let manager = vpnManager else {
            completion(VPNError.managerNotInitialized)
            return
        }
        
        manager.loadFromPreferences { [weak self] error in
            guard error == nil else {
                completion(error)
                return
            }
            
            // Configure OpenVPN protocol
            let proto = NEVPNProtocolIKEv2()
            proto.serverAddress = self?.extractServerAddress(from: config) ?? "unknown"
            proto.remoteIdentifier = proto.serverAddress
            proto.localIdentifier = "OpenVPN"
            proto.authenticationMethod = .none
            proto.useExtendedAuthentication = false
            proto.disconnectOnSleep = false
            
            // Set credentials if provided
            if let username = username, let password = password {
                proto.username = username
                proto.passwordReference = self?.savePasswordToKeychain(password)
            }
            
            manager.protocolConfiguration = proto
            manager.localizedDescription = "AXE VPN"
            manager.isEnabled = true
            
            // Save configuration
            manager.saveToPreferences { error in
                if let error = error {
                    completion(error)
                } else {
                    completion(nil)
                }
            }
        }
    }
    
    // MARK: - VPN Control
    
    /// Start VPN connection
    /// - Parameter completion: Completion handler with success/error
    public func connect(completion: @escaping (Error?) -> Void) {
        guard let manager = vpnManager else {
            completion(VPNError.managerNotInitialized)
            return
        }
        
        manager.loadFromPreferences { [weak self] error in
            guard error == nil else {
                completion(error)
                return
            }
            
            do {
                try manager.connection.startVPNTunnel()
                completion(nil)
            } catch {
                completion(error)
            }
        }
    }
    
    /// Stop VPN connection
    public func disconnect() {
        vpnManager?.connection.stopVPNTunnel()
    }
    
    /// Get current connection status
    /// - Returns: Current VPN status
    public func getStatus() -> NEVPNStatus {
        return vpnManager?.connection.status ?? .invalid
    }
    
    // MARK: - Status Handling
    
    @objc private func vpnStatusDidChange(_ notification: Notification) {
        guard let connection = notification.object as? NEVPNConnection else {
            return
        }
        
        currentStatus = connection.status
        onStatusChanged?(currentStatus)
        
        // Log status change
        print("📱 VPN Status Changed: \(statusString(from: currentStatus))")
    }
    
    // MARK: - Helper Methods
    
    private func extractServerAddress(from config: String) -> String? {
        // Parse .ovpn config to extract server address
        let lines = config.components(separatedBy: .newlines)
        
        for line in lines {
            if line.hasPrefix("remote ") {
                let components = line.components(separatedBy: " ")
                if components.count >= 2 {
                    return components[1]
                }
            }
        }
        
        return nil
    }
    
    private func savePasswordToKeychain(_ password: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: "vpn_password",
            kSecValueData as String: password.data(using: .utf8)!,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        
        // Delete existing
        SecItemDelete(query as CFDictionary)
        
        // Add new
        let status = SecItemAdd(query as CFDictionary, nil)
        
        if status == errSecSuccess {
            return password.data(using: .utf8)
        }
        
        return nil
    }
    
    private func statusString(from status: NEVPNStatus) -> String {
        switch status {
        case .invalid: return "Invalid"
        case .disconnected: return "Disconnected"
        case .connecting: return "Connecting"
        case .connected: return "Connected"
        case .reasserting: return "Reasserting"
        case .disconnecting: return "Disconnecting"
        @unknown default: return "Unknown"
        }
    }
    
    // MARK: - Error Types
    
    enum VPNError: LocalizedError {
        case managerNotInitialized
        case invalidConfiguration
        case connectionFailed
        
        var errorDescription: String? {
            switch self {
            case .managerNotInitialized:
                return "VPN Manager not initialized"
            case .invalidConfiguration:
                return "Invalid VPN configuration"
            case .connectionFailed:
                return "Failed to establish VPN connection"
            }
        }
    }
}

// MARK: - VPN Status Extension

extension NEVPNStatus {
    
    /// Convert to string representation
    public var stringValue: String {
        switch self {
        case .invalid: return "invalid"
        case .disconnected: return "disconnected"
        case .connecting: return "connecting"
        case .connected: return "connected"
        case .reasserting: return "reasserting"
        case .disconnecting: return "disconnecting"
        @unknown default: return "unknown"
        }
    }
    
    /// Convert to Flutter-compatible status
    public var flutterStatus: String {
        switch self {
        case .disconnected, .invalid: return "vpn_disconnected"
        case .connecting: return "vpn_connecting"
        case .connected: return "vpn_connected"
        case .reasserting: return "vpn_reconnecting"
        case .disconnecting: return "vpn_disconnecting"
        @unknown default: return "vpn_unknown"
        }
    }
}
