/// AxeVPN Flutter Plugin - WireGuard Integration
///
/// This plugin provides comprehensive WireGuard connectivity for Flutter applications
/// with full support for Android and iOS.
///
/// Features:
/// - WireGuard connection management
/// - Real-time connection status monitoring
/// - Modern, high-performance VPN protocol
/// - Android native WireGuard-Android integration
/// - iOS Network Extension with WireGuardKit
///
/// Example usage:
/// ```dart
/// import 'package:axevpn_flutter/wireguard_flutter.dart';
///
/// final vpn = WireGuard(
///   onVpnStatusChanged: (status) => print(status),
///   onVpnStageChanged: (stage, raw) => print(stage),
/// );
///
/// await vpn.initialize();
/// await vpn.connect(configContent, tunnelName);
/// ```
library wireguard_flutter;

export 'src/wireguard_engine.dart';
export 'src/model/wireguard_status.dart';
