/// Status data emitted by the OpenConnect engine.
class OpenConnectStatus {
  /// Current connection state label (e.g. "connected", "disconnected").
  final String state;

  /// Bytes received since the tunnel was established.
  final int bytesIn;

  /// Bytes sent since the tunnel was established.
  final int bytesOut;

  /// Assigned tunnel IP address (available when connected).
  final String? tunnelIp;

  /// Error message, non-null only when [state] == "error".
  final String? errorMessage;

  const OpenConnectStatus({
    required this.state,
    this.bytesIn = 0,
    this.bytesOut = 0,
    this.tunnelIp,
    this.errorMessage,
  });

  factory OpenConnectStatus.empty() =>
      const OpenConnectStatus(state: 'disconnected');

  factory OpenConnectStatus.fromJson(Map<String, dynamic> json) =>
      OpenConnectStatus(
        state: json['state']?.toString() ?? 'unknown',
        bytesIn: int.tryParse(json['bytes_in']?.toString() ?? '0') ?? 0,
        bytesOut: int.tryParse(json['bytes_out']?.toString() ?? '0') ?? 0,
        tunnelIp: json['tunnel_ip']?.toString(),
        errorMessage: json['error']?.toString(),
      );

  @override
  String toString() => 'OpenConnectStatus(state: $state, tunnelIp: $tunnelIp, '
      'in: $bytesIn, out: $bytesOut)';
}
