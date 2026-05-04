import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart';
import 'model/v2ray_status.dart';

/// V2Ray sub-protocols supported by this engine.
enum V2RaySubProtocol {
  /// VLESS (recommended, no encryption overhead)
  vless,

  /// VMess (legacy V2Ray protocol)
  vmess,

  /// Trojan (TLS-camouflage)
  trojan,

  /// Shadowsocks
  shadowsocks,

  /// Unknown / unrecognised sub-protocol
  unknown,
}

/// Stages of a V2Ray/Xray connection lifecycle.
enum V2RayStage {
  /// Initial state.
  disconnected,

  /// Requesting VPN permission / preparing TUN interface.
  preparing,

  /// Connecting to the remote endpoint.
  connecting,

  /// Tunnel is up and traffic is flowing.
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

/// Engine that manages a V2Ray / Xray VPN connection.
///
/// **Usage**
/// ```dart
/// final engine = V2Ray(
///   onVpnStageChanged: (stage, raw) => print(stage),
///   onVpnStatusChanged: (status) => print(status),
/// );
/// await engine.initialize();
/// await engine.connect(configJson, 'My Server');
/// ```
class V2Ray {
  // ── Channel names ───────────────────────────────────────────────────────
  static const String _eventChannelStage = 'com.axevpn.flutter.v2ray/vpnstage';
  static const String _methodChannelControl =
      'com.axevpn.flutter.v2ray/vpncontrol';

  static const MethodChannel _channelControl =
      MethodChannel(_methodChannelControl);

  static Stream<String> _stageSnapshot() =>
      const EventChannel(_eventChannelStage).receiveBroadcastStream().cast();

  // ── State ────────────────────────────────────────────────────────────────
  Timer? _statusTimer;
  DateTime? _tempDateTime;
  V2RayStage? _lastStage;

  bool initialized = false;

  // ── Callbacks ────────────────────────────────────────────────────────────
  final Function(V2RayStatus? data)? onVpnStatusChanged;
  final Function(V2RayStage stage, String rawStage)? onVpnStageChanged;

  V2Ray({this.onVpnStatusChanged, this.onVpnStageChanged});

  // ── Public API ───────────────────────────────────────────────────────────

  /// Initialize the engine.  Must be called before [connect].
  ///
  /// On iOS, [providerBundleIdentifier], [localizedDescription] and
  /// [groupIdentifier] are required.
  Future<void> initialize({
    String? providerBundleIdentifier,
    String? localizedDescription,
    String? groupIdentifier,
    Function(V2RayStatus status)? lastStatus,
    Function(V2RayStage stage)? lastStage,
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

    onVpnStatusChanged?.call(V2RayStatus.empty());
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
          if (s == V2RayStage.connected && _statusTimer == null) {
            _startStatusTimer();
          }
          return lastStage?.call(s);
        }),
      ]);
    });
  }

  /// Connect using a V2Ray/Xray JSON configuration string.
  ///
  /// [configJson]   – Full V2Ray/Xray configuration as a JSON string.
  /// [name]         – Display name shown in the system VPN notification.
  /// [subProtocol]  – Optional hint about which sub-protocol is used
  ///                  (vless, vmess, trojan, shadowsocks).
  /// [bypassPackages] – Android only: exclude these apps from the tunnel.
  Future<void> connect(
    String configJson,
    String name, {
    V2RaySubProtocol subProtocol = V2RaySubProtocol.vless,
    List<String>? bypassPackages,
  }) async {
    if (!initialized) throw StateError('V2Ray must be initialized first.');

    _tempDateTime = DateTime.now();

    try {
      await _channelControl.invokeMethod('connect', {
        'config_json': configJson,
        'name': name,
        'sub_protocol': subProtocol.name,
        'bypass_packages': bypassPackages ?? [],
      });
    } on PlatformException catch (e) {
      throw ArgumentError(e.message);
    }
  }

  /// Disconnect the active V2Ray tunnel.
  Future<void> disconnect() async {
    _tempDateTime = null;
    _cancelStatusTimer();
    try {
      await _channelControl.invokeMethod('disconnect');
    } on PlatformException catch (e) {
      throw ArgumentError(e.message);
    }
  }

  /// Returns the current [V2RayStatus] by querying the native side.
  Future<V2RayStatus> status() async {
    try {
      final raw = await _channelControl.invokeMethod<String>('status');
      if (raw == null || raw.isEmpty) return V2RayStatus.empty();
      return V2RayStatus.fromJson(
          Map<String, dynamic>.from(jsonDecode(raw) as Map));
    } catch (_) {
      return V2RayStatus.empty();
    }
  }

  /// Returns the current [V2RayStage] by querying the native side.
  Future<V2RayStage> stage() async {
    try {
      final raw = await _channelControl.invokeMethod<String>('stage');
      return _parseStage(raw ?? 'unknown');
    } catch (_) {
      return V2RayStage.unknown;
    }
  }

  // ── Private helpers ──────────────────────────────────────────────────────

  void _initializeListener() {
    _stageSnapshot().listen((rawStage) {
      final parsedStage = _parseStage(rawStage);

      // Suppress spurious disconnect events at the start of a new connection
      if (parsedStage == V2RayStage.disconnected) {
        if (_tempDateTime != null &&
            DateTime.now().difference(_tempDateTime!).inSeconds < 2) {
          return;
        }
        _cancelStatusTimer();
      }

      if (parsedStage == V2RayStage.connected && _statusTimer == null) {
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

  static V2RayStage _parseStage(String raw) {
    switch (raw.toLowerCase().trim()) {
      case 'disconnected':
        return V2RayStage.disconnected;
      case 'preparing':
        return V2RayStage.preparing;
      case 'connecting':
        return V2RayStage.connecting;
      case 'connected':
        return V2RayStage.connected;
      case 'disconnecting':
        return V2RayStage.disconnecting;
      case 'error':
        return V2RayStage.error;
      case 'denied':
        return V2RayStage.denied;
      default:
        return V2RayStage.unknown;
    }
  }

  // ── Config builder helpers ───────────────────────────────────────────────

  /// Build a minimal VLESS-over-Reality JSON config string.
  ///
  /// All string parameters are sanitised – they must not contain unescaped
  /// JSON special characters supplied by the server.
  static String buildVlessConfig({
    required String address,
    required int port,
    required String uuid,
    String flow = 'xtls-rprx-vision',
    String network = 'tcp',
    String security = 'reality',
    String? serverName,
    String? publicKey,
    String? shortId,
  }) {
    final outbound = {
      'protocol': 'vless',
      'settings': {
        'vnext': [
          {
            'address': address,
            'port': port,
            'users': [
              {'id': uuid, 'flow': flow, 'encryption': 'none'},
            ],
          },
        ],
      },
      'streamSettings': {
        'network': network,
        'security': security,
        if (security == 'reality')
          'realitySettings': {
            'serverName': serverName ?? address,
            'publicKey': publicKey ?? '',
            'shortId': shortId ?? '',
          },
        if (security == 'tls')
          'tlsSettings': {
            'serverName': serverName ?? address,
            'allowInsecure': false,
          },
      },
    };

    return jsonEncode({
      'log': {'loglevel': 'warning'},
      'inbounds': [
        {
          'port': 10808,
          'protocol': 'socks',
          'settings': {'auth': 'noauth'},
        },
      ],
      'outbounds': [outbound],
    });
  }

  /// Build a minimal Trojan JSON config string.
  static String buildTrojanConfig({
    required String address,
    required int port,
    required String password,
    String? serverName,
    bool allowInsecure = false,
  }) {
    return jsonEncode({
      'log': {'loglevel': 'warning'},
      'inbounds': [
        {
          'port': 10808,
          'protocol': 'socks',
          'settings': {'auth': 'noauth'},
        },
      ],
      'outbounds': [
        {
          'protocol': 'trojan',
          'settings': {
            'servers': [
              {
                'address': address,
                'port': port,
                'password': password,
              },
            ],
          },
          'streamSettings': {
            'network': 'tcp',
            'security': 'tls',
            'tlsSettings': {
              'serverName': serverName ?? address,
              'allowInsecure': allowInsecure,
            },
          },
        },
      ],
    });
  }

  /// Build a minimal VMess JSON config string.
  static String buildVmessConfig({
    required String address,
    required int port,
    required String uuid,
    int alterId = 0,
    String network = 'tcp',
    String security = 'tls',
    String? serverName,
    String? path,
  }) {
    return jsonEncode({
      'log': {'loglevel': 'warning'},
      'inbounds': [
        {
          'port': 10808,
          'protocol': 'socks',
          'settings': {'auth': 'noauth'},
        },
      ],
      'outbounds': [
        {
          'protocol': 'vmess',
          'settings': {
            'vnext': [
              {
                'address': address,
                'port': port,
                'users': [
                  {
                    'id': uuid,
                    'alterId': alterId,
                    'security': 'auto',
                  },
                ],
              },
            ],
          },
          'streamSettings': {
            'network': network,
            if (network == 'ws') 'wsSettings': {'path': path ?? '/'},
            'security': security,
            if (security == 'tls')
              'tlsSettings': {
                'serverName': serverName ?? address,
                'allowInsecure': false,
              },
          },
        },
      ],
    });
  }

  /// Build a Shadowsocks JSON config string.
  static String buildShadowsocksConfig({
    required String address,
    required int port,
    required String password,
    String method = 'aes-256-gcm',
  }) {
    return jsonEncode({
      'log': {'loglevel': 'warning'},
      'inbounds': [
        {
          'port': 10808,
          'protocol': 'socks',
          'settings': {'auth': 'noauth'},
        },
      ],
      'outbounds': [
        {
          'protocol': 'shadowsocks',
          'settings': {
            'servers': [
              {
                'address': address,
                'port': port,
                'method': method,
                'password': password,
              },
            ],
          },
        },
      ],
    });
  }
}
