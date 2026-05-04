import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart';
import 'model/openconnect_status.dart';

/// Stages of an OpenConnect (Cisco AnyConnect-compatible) connection lifecycle.
enum OCStage {
  /// Initial state.
  disconnected,

  /// Requesting VPN permission / preparing TUN interface.
  preparing,

  /// TCP/TLS handshake with the gateway.
  connecting,

  /// Authenticating (username + password / certificate).
  authenticating,

  /// Tunnel is established and traffic flows.
  connected,

  /// Graceful shutdown in progress.
  disconnecting,

  /// An unrecoverable error occurred.
  error,

  /// VPN permission was denied by the user.
  denied,

  /// Stage reported by the native side is unrecognised.
  unknown,
}

/// Authentication method used by the OpenConnect gateway.
enum OCAuthMethod {
  /// Username + password (most common).
  password,

  /// Client certificate (PEM/PKCS12).
  certificate,

  /// TOTP / one-time password.
  totp,
}

/// Engine that manages an OpenConnect (ocserv / Cisco AnyConnect-compatible)
/// VPN connection lifecycle.
///
/// **Usage**
/// ```dart
/// final oc = OpenConnect(
///   onVpnStageChanged: (stage, raw) => print(stage),
///   onVpnStatusChanged: (status) => print(status),
/// );
/// await oc.initialize();
/// await oc.connect(
///   serverUrl: 'https://vpn.example.com',
///   username: 'user',
///   password: 'pass',
///   name: 'My OpenConnect VPN',
/// );
/// ```
class OpenConnect {
  // ── Channel names ───────────────────────────────────────────────────────
  static const String _eventChannelStage =
      'com.axevpn.flutter.openconnect/vpnstage';
  static const String _methodChannelControl =
      'com.axevpn.flutter.openconnect/vpncontrol';

  static const MethodChannel _channelControl =
      MethodChannel(_methodChannelControl);

  static Stream<String> _stageSnapshot() =>
      const EventChannel(_eventChannelStage).receiveBroadcastStream().cast();

  // ── State ────────────────────────────────────────────────────────────────
  Timer? _statusTimer;
  DateTime? _tempDateTime;
  OCStage? _lastStage;

  bool initialized = false;

  // ── Callbacks ────────────────────────────────────────────────────────────
  final Function(OpenConnectStatus? data)? onVpnStatusChanged;
  final Function(OCStage stage, String rawStage)? onVpnStageChanged;

  OpenConnect({this.onVpnStatusChanged, this.onVpnStageChanged});

  // ── Public API ───────────────────────────────────────────────────────────

  /// Initialize the engine. Must be called once before [connect].
  ///
  /// On iOS, [providerBundleIdentifier], [localizedDescription] and
  /// [groupIdentifier] are required.
  Future<void> initialize({
    String? providerBundleIdentifier,
    String? localizedDescription,
    String? groupIdentifier,
    Function(OpenConnectStatus status)? lastStatus,
    Function(OCStage stage)? lastStage,
  }) async {
    if (Platform.isIOS) {
      assert(
        groupIdentifier != null &&
            providerBundleIdentifier != null &&
            localizedDescription != null,
        'groupIdentifier, providerBundleIdentifier and localizedDescription '
        'are required for iOS.',
      );
    }

    onVpnStatusChanged?.call(OpenConnectStatus.empty());
    initialized = true;
    _initializeListener();

    return _channelControl.invokeMethod('initialize', {
      'groupIdentifier': groupIdentifier,
      'providerBundleIdentifier': providerBundleIdentifier,
      'localizedDescription': localizedDescription,
    }).then((_) {
      Future.wait([
        status().then((s) => lastStatus?.call(s)),
        stage().then((s) {
          if (s == OCStage.connected && _statusTimer == null) {
            _startStatusTimer();
          }
          return lastStage?.call(s);
        }),
      ]);
    });
  }

  /// Connect to an OpenConnect / ocserv gateway.
  ///
  /// [serverUrl]   – HTTPS URL of the VPN gateway (e.g. `https://vpn.host.com`).
  /// [username]    – User account for password or TOTP auth.
  /// [password]    – Password or TOTP code.
  /// [certPath]    – Path to a PEM/P12 client certificate (certificate auth).
  /// [certPassword]– Passphrase for an encrypted client certificate.
  /// [caPath]      – Custom CA certificate path to trust. Supply for self-signed servers.
  /// [authGroup]   – Optional group/realm name required by some gateways.
  /// [servercert]  – SHA1 fingerprint of the server cert to pin (e.g. `sha1:<hex>`).
  ///                 Set to `pin-sha256:<b64>` for SHA-256 pinning.
  /// [name]        – Display name shown in system VPN notification.
  /// [authMethod]  – Hint to the native layer about the auth flow.
  /// [bypassPackages] – Android only: apps to exclude from the tunnel.
  Future<void> connect({
    required String serverUrl,
    required String name,
    String? username,
    String? password,
    String? certPath,
    String? certPassword,
    String? caPath,
    String? authGroup,
    String? servercert,
    OCAuthMethod authMethod = OCAuthMethod.password,
    List<String>? bypassPackages,
  }) async {
    if (!initialized) {
      throw StateError('OpenConnect must be initialized first.');
    }

    _tempDateTime = DateTime.now();

    try {
      await _channelControl.invokeMethod('connect', {
        'server_url': serverUrl,
        'name': name,
        'username': username,
        'password': password,
        'cert_path': certPath,
        'cert_password': certPassword,
        'ca_path': caPath,
        'auth_group': authGroup,
        'servercert': servercert,
        'auth_method': authMethod.name,
        'bypass_packages': bypassPackages ?? [],
      });
    } on PlatformException catch (e) {
      throw ArgumentError(e.message);
    }
  }

  /// Gracefully disconnect the active OpenConnect tunnel.
  Future<void> disconnect() async {
    _tempDateTime = null;
    _cancelStatusTimer();
    try {
      await _channelControl.invokeMethod('disconnect');
    } on PlatformException catch (e) {
      throw ArgumentError(e.message);
    }
  }

  /// Returns the current [OpenConnectStatus] by querying the native side.
  Future<OpenConnectStatus> status() async {
    try {
      final raw = await _channelControl.invokeMethod<String>('status');
      if (raw == null || raw.isEmpty) return OpenConnectStatus.empty();
      return OpenConnectStatus.fromJson(
          Map<String, dynamic>.from(jsonDecode(raw) as Map));
    } catch (_) {
      return OpenConnectStatus.empty();
    }
  }

  /// Returns the current [OCStage] by querying the native side.
  Future<OCStage> stage() async {
    try {
      final raw = await _channelControl.invokeMethod<String>('stage');
      return _parseStage(raw ?? 'unknown');
    } catch (_) {
      return OCStage.unknown;
    }
  }

  // ── Private helpers ──────────────────────────────────────────────────────

  void _initializeListener() {
    _stageSnapshot().listen((rawStage) {
      final parsedStage = _parseStage(rawStage);

      if (parsedStage == OCStage.disconnected) {
        if (_tempDateTime != null &&
            DateTime.now().difference(_tempDateTime!).inSeconds < 2) {
          return;
        }
        _cancelStatusTimer();
      }

      if (parsedStage == OCStage.connected && _statusTimer == null) {
        _tempDateTime = DateTime.now();
        _startStatusTimer();
      }

      if (parsedStage != _lastStage) {
        _lastStage = parsedStage;
        onVpnStageChanged?.call(parsedStage, rawStage);
      }
    });
  }

  void _startStatusTimer() {
    _statusTimer ??= Timer.periodic(
      const Duration(seconds: 1),
      (_) async {
        final s = await status();
        onVpnStatusChanged?.call(s);
      },
    );
  }

  void _cancelStatusTimer() {
    _statusTimer?.cancel();
    _statusTimer = null;
  }

  static OCStage _parseStage(String raw) {
    switch (raw.toLowerCase().trim()) {
      case 'disconnected':
        return OCStage.disconnected;
      case 'preparing':
        return OCStage.preparing;
      case 'connecting':
        return OCStage.connecting;
      case 'authenticating':
        return OCStage.authenticating;
      case 'connected':
        return OCStage.connected;
      case 'disconnecting':
        return OCStage.disconnecting;
      case 'error':
        return OCStage.error;
      case 'denied':
        return OCStage.denied;
      default:
        return OCStage.unknown;
    }
  }
}
