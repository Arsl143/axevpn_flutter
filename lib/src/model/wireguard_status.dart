import 'dart:convert';

/// Model class for WireGuard VPN status
class WireGuardStatus {
  /// Connection duration
  Duration? duration;

  /// Last packet received timestamp
  String? lastPacketReceive;

  /// Bytes received (download)
  String? byteIn;

  /// Bytes sent (upload)
  String? byteOut;

  /// Constructor
  WireGuardStatus({
    this.duration,
    this.lastPacketReceive,
    this.byteIn,
    this.byteOut,
  });

  /// Create empty status
  factory WireGuardStatus.empty() {
    return WireGuardStatus(
      duration: const Duration(),
      lastPacketReceive: "0",
      byteIn: "0",
      byteOut: "0",
    );
  }

  /// Create from JSON
  factory WireGuardStatus.fromJson(Map<String, dynamic> json) {
    return WireGuardStatus(
      duration: json['duration'] != null
          ? Duration(seconds: int.tryParse(json['duration'].toString()) ?? 0)
          : const Duration(),
      lastPacketReceive: json['last_packet_receive']?.toString() ?? '0',
      byteIn: json['byte_in']?.toString() ?? '0',
      byteOut: json['byte_out']?.toString() ?? '0',
    );
  }

  /// Convert to JSON
  Map<String, dynamic> toJson() {
    return {
      'duration': duration?.inSeconds.toString() ?? '0',
      'last_packet_receive': lastPacketReceive ?? '0',
      'byte_in': byteIn ?? '0',
      'byte_out': byteOut ?? '0',
    };
  }

  @override
  String toString() => jsonEncode(toJson());
}
