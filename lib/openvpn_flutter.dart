/// AxeVPN Flutter Plugin - Advanced OpenVPN Integration
///
/// This plugin provides comprehensive OpenVPN connectivity for Flutter applications
/// with full support for Android 15+ (16 KB page size) and iOS.
///
/// Features:
/// - OpenVPN connection management
/// - Real-time connection status monitoring
/// - Support for both TCP and UDP protocols
/// - Android 15+ compatible with 16 KB page sizes
/// - iOS Network Extension support
///
/// Example usage:
/// ```dart
/// import 'package:axevpn_flutter/axevpn_flutter.dart';
///
/// final vpn = OpenVPN(
///   onVpnStatusChanged: (status) => print(status),
///   onVpnStageChanged: (stage, raw) => print(stage),
/// );
///
/// await vpn.initialize();
/// await vpn.connect(config, username, password);
/// ```
library axevpn_flutter;

export 'src/vpn_engine.dart';
export 'src/model/vpn_status.dart';
