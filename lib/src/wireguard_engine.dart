import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart';
import 'model/wireguard_status.dart';

/// Stages of WireGuard VPN connections
enum WGStage {
  preparing,
  connecting,
  connected,
  disconnecting,
  disconnected,
  error,
  denied,
  unknown,
}

class WireGuard {
  /// Event channel for VPN stage updates
  static const String _eventChannelVpnStage =
      "com.axevpn.flutter.wireguard/vpnstage";

  /// Method channel for VPN control
  static const String _methodChannelVpnControl =
      "com.axevpn.flutter.wireguard/vpncontrol";

  /// Method channel to invoke methods from native side
  static const MethodChannel _channelControl = MethodChannel(
    _methodChannelVpnControl,
  );

  /// Event channel stream snapshot from native side
  static Stream<String> _vpnStageSnapshot() =>
      const EventChannel(_eventChannelVpnStage).receiveBroadcastStream().cast();

  /// Timer to periodically check VPN status
  Timer? _vpnStatusTimer;

  /// Indicates if the engine has been initialized
  bool initialized = false;

  /// Temporary DateTime for connection tracking
  DateTime? _tempDateTime;

  /// Last reported stage
  WGStage? _lastStage;

  /// Listener for VPN status changes
  final Function(WireGuardStatus? data)? onVpnStatusChanged;

  /// Listener for VPN stage changes
  final Function(WGStage stage, String rawStage)? onVpnStageChanged;

  /// WireGuard constructor - implement the listeners
  WireGuard({this.onVpnStatusChanged, this.onVpnStageChanged});

  /// Initialize WireGuard - must be called before any usage
  ///
  /// For iOS:
  /// - [providerBundleIdentifier]: Network Extension identifier
  /// - [localizedDescription]: Description shown in user's settings
  /// - [groupIdentifier]: App group identifier for sharing data
  ///
  /// Returns latest WGStage
  Future<void> initialize({
    String? providerBundleIdentifier,
    String? localizedDescription,
    String? groupIdentifier,
    Function(WireGuardStatus status)? lastStatus,
    Function(WGStage stage)? lastStage,
  }) async {
    if (Platform.isIOS) {
      assert(
        groupIdentifier != null &&
            providerBundleIdentifier != null &&
            localizedDescription != null,
        "These values are required for iOS.",
      );
    }

    onVpnStatusChanged?.call(WireGuardStatus.empty());
    initialized = true;
    _initializeListener();

    return _channelControl.invokeMethod("initialize", {
      "groupIdentifier": groupIdentifier,
      "providerBundleIdentifier": providerBundleIdentifier,
      "localizedDescription": localizedDescription,
    }).then((value) {
      Future.wait([
        status().then((value) => lastStatus?.call(value)),
        stage().then((value) {
          if (value == WGStage.connected && _vpnStatusTimer == null) {
            _createTimer();
          }
          return lastStage?.call(value);
        }),
      ]);
    });
  }

  /// Connect to WireGuard VPN
  ///
  /// [config]: WireGuard configuration content (.conf file content)
  /// [tunnelName]: Name that will show in user's notification/settings
  Future connect(
    String config,
    String tunnelName,
  ) {
    if (!initialized) throw ("WireGuard needs to be initialized");
    _tempDateTime = DateTime.now();

    try {
      return _channelControl.invokeMethod("connect", {
        "config": config,
        "tunnelName": tunnelName,
      });
    } catch (e) {
      throw e.toString();
    }
  }

  /// Disconnect from WireGuard VPN
  Future<void> disconnect() => _channelControl.invokeMethod("disconnect");

  /// Get current WireGuard connection status
  Future<WireGuardStatus> status() async {
    String? statusResult = await _channelControl.invokeMethod("status");
    if (statusResult == null || statusResult.isEmpty) {
      return WireGuardStatus.empty();
    }

    Map<String, dynamic> status = jsonDecode(statusResult);
    WireGuardStatus result = WireGuardStatus.fromJson(status);
    return result;
  }

  /// Get current WireGuard connection stage
  Future<WGStage> stage() async {
    String? stageResult = await _channelControl.invokeMethod("stage");
    if (stageResult == null || stageResult.isEmpty) return WGStage.disconnected;
    return _parseStage(stageResult);
  }

  /// Initialize listeners for stage and status changes
  void _initializeListener() {
    // Listen to stage changes
    _vpnStageSnapshot().listen((event) {
      WGStage stage = _parseStage(event);

      if (_lastStage != stage) {
        onVpnStageChanged?.call(stage, event);
        _lastStage = stage;
      }

      // Start timer when connected
      if (stage == WGStage.connected && _vpnStatusTimer == null) {
        _tempDateTime = DateTime.now();
        _createTimer();
      }

      // Stop timer when disconnected
      if (stage == WGStage.disconnected || stage == WGStage.error) {
        _destroyTimer();
      }
    });
  }

  /// Create timer for periodic status updates
  void _createTimer() {
    _vpnStatusTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      status().then((value) {
        // Update duration based on connection time
        if (_tempDateTime != null) {
          value.duration = DateTime.now().difference(_tempDateTime!);
        }
        onVpnStatusChanged?.call(value);
      });
    });
  }

  /// Destroy the status timer
  void _destroyTimer() {
    _vpnStatusTimer?.cancel();
    _vpnStatusTimer = null;
    _tempDateTime = null;
  }

  /// Parse stage string to WGStage enum
  WGStage _parseStage(String stage) {
    switch (stage.toLowerCase()) {
      case 'preparing':
        return WGStage.preparing;
      case 'connecting':
        return WGStage.connecting;
      case 'connected':
        return WGStage.connected;
      case 'disconnecting':
        return WGStage.disconnecting;
      case 'disconnected':
        return WGStage.disconnected;
      case 'error':
        return WGStage.error;
      case 'denied':
        return WGStage.denied;
      default:
        return WGStage.unknown;
    }
  }

  /// Dispose resources
  void dispose() {
    _destroyTimer();
  }
}
