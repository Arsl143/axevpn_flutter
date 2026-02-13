/// AxeVPN Flutter Plugin - Advanced VPN Integration
///
/// This plugin provides comprehensive VPN connectivity for Flutter applications
/// with full support for Android 15+ (16 KB page size) and iOS.
///
/// Supported Protocols:
/// - OpenVPN (TCP/UDP)
/// - WireGuard (Modern, high-performance)
///
/// Features:
/// - VPN connection management for multiple protocols
/// - Real-time connection status monitoring
/// - Support for both TCP and UDP protocols (OpenVPN)
/// - Android 15+ compatible with 16 KB page sizes
/// - iOS Network Extension support
///
/// Example usage:
/// ```dart
/// import 'package:axevpn_flutter/axevpn_flutter.dart';
///
/// // OpenVPN
/// final vpn = OpenVPN(
///   onVpnStatusChanged: (status) => print(status),
///   onVpnStageChanged: (stage, raw) => print(stage),
/// );
/// await vpn.initialize();
/// await vpn.connect(config, username, password);
///
/// // WireGuard
/// final wg = WireGuard(
///   onVpnStatusChanged: (status) => print(status),
///   onVpnStageChanged: (stage, raw) => print(stage),
/// );
/// await wg.initialize();
/// await wg.connect(configContent, tunnelName);
/// ```
library axevpn_flutter;

export 'src/vpn_engine.dart';
export 'src/model/vpn_status.dart';
export 'src/wireguard_engine.dart';
export 'src/model/wireguard_status.dart';
