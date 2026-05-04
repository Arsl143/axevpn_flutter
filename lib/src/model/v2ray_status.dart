/// Status data emitted by the V2Ray/Xray engine.
class V2RayStatus {
  /// Current connection state label (e.g. "connected", "disconnected").
  final String state;

  /// Bytes received since the tunnel was established.
  final int bytesIn;

  /// Bytes sent since the tunnel was established.
  final int bytesOut;

  /// Error message, non-null only when [state] == "error".
  final String? errorMessage;

  const V2RayStatus({
    required this.state,
    this.bytesIn = 0,
    this.bytesOut = 0,
    this.errorMessage,
  });

  factory V2RayStatus.empty() => const V2RayStatus(state: 'disconnected');

  factory V2RayStatus.fromJson(Map<String, dynamic> json) => V2RayStatus(
        state: json['state']?.toString() ?? 'unknown',
        bytesIn: int.tryParse(json['bytes_in']?.toString() ?? '0') ?? 0,
        bytesOut: int.tryParse(json['bytes_out']?.toString() ?? '0') ?? 0,
        errorMessage: json['error']?.toString(),
      );

  @override
  String toString() =>
      'V2RayStatus(state: $state, in: $bytesIn, out: $bytesOut)';
}
