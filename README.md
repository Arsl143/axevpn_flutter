# axevpn_flutter

[![pub package](https://img.shields.io/pub/v/axevpn_flutter.svg)](https://pub.dev/packages/axevpn_flutter)
[![pub points](https://img.shields.io/pub/points/axevpn_flutter)](https://pub.dev/packages/axevpn_flutter/score)
[![popularity](https://img.shields.io/pub/popularity/axevpn_flutter)](https://pub.dev/packages/axevpn_flutter/score)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-brightgreen)](https://pub.dev/packages/axevpn_flutter)

A Flutter plugin that provides **OpenVPN** and **WireGuard** VPN connectivity for Android and iOS.  
Built with Android 15+ 16 KB page-size compatibility and modern Flutter 3.10+ / Dart 3.0+ support.

---

## Table of Contents

- [Features](#features)
- [Platform Support](#platform-support)
- [Installation](#installation)
- [Quick Start](#quick-start)
  - [OpenVPN](#openvpn)
  - [WireGuard](#wireguard)
- [Android Setup](#android-setup)
- [iOS Setup](#ios-setup)
- [API Reference](#api-reference)
  - [OpenVPN API](#openvpn-api)
  - [WireGuard API](#wireguard-api)
  - [VPN Stages](#vpn-stages)
- [Advanced Usage](#advanced-usage)
- [Troubleshooting](#troubleshooting)
- [Changelog](#changelog)
- [License](#license)

---

## Features

| Feature | OpenVPN | WireGuard |
|---|:---:|:---:|
| Android support | ✅ | ✅ |
| iOS support | ✅ | ✅ |
| Real-time status monitoring | ✅ | ✅ |
| Connection stats (bytes in/out) | ✅ | ✅ |
| Auto-reconnect handling | ✅ | ✅ |
| Split tunneling (package bypass) | ✅ | ➖ |
| TCP & UDP protocol | ✅ | ➖ |
| Android 15+ 16 KB page-size | ✅ | ✅ |
| Modern cryptography | ✅ | ✅ |

---

## Platform Support

| Platform | Minimum Version |
|---|---|
| Android | 7.0 (API 24) |
| iOS | 16.0 |

---

## Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
  axevpn_flutter: ^2.0.0
```

Then run:

```bash
flutter pub get
```

---

## Quick Start

### OpenVPN

```dart
import 'package:axevpn_flutter/openvpn_flutter.dart';

// 1. Instantiate
final vpn = OpenVPN(
  onVpnStatusChanged: (VpnStatus? status) {
    print('Duration: ${status?.duration}');
    print('Bytes in: ${status?.byteIn}  Bytes out: ${status?.byteOut}');
  },
  onVpnStageChanged: (VPNStage stage, String rawStage) {
    print('Stage: $stage');
  },
);

// 2. Initialize (required before connect)
await vpn.initialize(
  // iOS only ↓
  groupIdentifier: 'group.com.example.vpn',
  providerBundleIdentifier: 'com.example.app.VPNExtension',
  localizedDescription: 'My VPN',
);

// 3. Connect
final ovpnConfig = '''
client
dev tun
proto udp
remote vpn.example.com 1194
...
''';

await vpn.connect(ovpnConfig, 'My Server');

// 4. Disconnect
vpn.disconnect();
```

---

### WireGuard

```dart
import 'package:axevpn_flutter/wireguard_flutter.dart';

// 1. Instantiate
final wg = WireGuard(
  onVpnStatusChanged: (WireGuardStatus? status) {
    print('Duration: ${status?.duration}');
    print('Bytes in: ${status?.byteIn}  Bytes out: ${status?.byteOut}');
  },
  onVpnStageChanged: (WGStage stage, String rawStage) {
    print('Stage: $stage');
  },
);

// 2. Initialize
await wg.initialize(
  // iOS only ↓
  groupIdentifier: 'group.com.example.vpn',
  providerBundleIdentifier: 'com.example.app.WGExtension',
  localizedDescription: 'My VPN',
);

// 3. Connect — pass raw WireGuard .conf content
final wgConfig = '''
[Interface]
PrivateKey = <your_private_key>
Address = 10.0.0.2/24
DNS = 1.1.1.1

[Peer]
PublicKey = <server_public_key>
Endpoint = vpn.example.com:51820
AllowedIPs = 0.0.0.0/0
''';

await wg.connect(wgConfig, 'My WireGuard');

// 4. Disconnect
await wg.disconnect();
```

---

## Android Setup

### 1. Handle VPN permission result in MainActivity

**Kotlin (`MainActivity.kt`)**:
```kotlin
import com.axevpn.flutter.openvpn.AxeVPNFlutterPlugin

override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    AxeVPNFlutterPlugin.connectWhileGranted(requestCode == 24 && resultCode == RESULT_OK)
    super.onActivityResult(requestCode, resultCode, data)
}
```

**Java (`MainActivity.java`)**:
```java
import com.axevpn.flutter.openvpn.AxeVPNFlutterPlugin;

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    AxeVPNFlutterPlugin.connectWhileGranted(requestCode == 24 && resultCode == RESULT_OK);
    super.onActivityResult(requestCode, resultCode, data);
}
```

### 2. app/build.gradle (Kotlin DSL)

```kotlin
android {
    compileSdk = 36
    ndkVersion = "27.0.12077973"   // Required for 16 KB page-size support

    defaultConfig {
        minSdk = 24
        targetSdk = 36
    }

    // Enable Android 15+ 16 KB memory page size
    experimentalProperties["android.experimental.enable16KPageSize"] = true

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```

### 3. android/gradle.properties

```properties
android.experimental.enable16KPageSize=true
android.bundle.enableUncompressedNativeLibs=false
```

### 4. AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<application
    android:extractNativeLibs="true"
    ...>
```

---

## iOS Setup

### 1. Enable capabilities in Xcode

In your **Runner** target (and **Network Extension** target), enable:
- **App Groups** – create `group.com.yourapp.vpn`
- **Network Extensions** – enable **Packet Tunnel**

### 2. Add Network Extension target

In Xcode → **File → New → Target → Network Extension**.  
Name it (e.g., `VPNExtension`) and set the same App Group.

#### OpenVPN Extension

Add to `ios/Podfile` under the new target:

```ruby
target 'VPNExtension' do
  use_frameworks!
  pod 'OpenVPNAdapter', :git => 'https://github.com/ss-abramchuk/OpenVPNAdapter.git', :tag => '0.8.0'
end
```

Copy `PacketTunnelProvider.swift` to `VPNExtension/`:

```swift
// See example/ios/VPNExtension/ in the repository
```

#### WireGuard Extension

```ruby
target 'WGExtension' do
  use_frameworks!
  pod 'WireGuardKit', :git => 'https://git.zx2c4.com/wireguard-apple', :tag => '1.0.15-26'
end
```

### 3. Info.plist — Network Extension target

```xml
<key>NSExtension</key>
<dict>
    <key>NSExtensionPointIdentifier</key>
    <string>com.apple.networkextension.packet-tunnel</string>
    <key>NSExtensionPrincipalClass</key>
    <string>$(PRODUCT_MODULE_NAME).PacketTunnelProvider</string>
</dict>
```

---

## API Reference

### OpenVPN API

#### `OpenVPN` constructor

```dart
OpenVPN({
  Function(VpnStatus? data)? onVpnStatusChanged,
  Function(VPNStage stage, String rawStage)? onVpnStageChanged,
})
```

| Parameter | Type | Description |
|---|---|---|
| `onVpnStatusChanged` | `Function(VpnStatus?)` | Called when bytes/duration update |
| `onVpnStageChanged` | `Function(VPNStage, String)` | Called on every stage transition |

#### `initialize()`

```dart
Future<void> initialize({
  String? groupIdentifier,          // iOS: App Group ID
  String? providerBundleIdentifier, // iOS: Extension bundle ID
  String? localizedDescription,     // iOS: Description in Settings
  Function(VpnStatus)? lastStatus,  // Callback with last known status
  Function(VPNStage)? lastStage,    // Callback with last known stage
})
```

#### `connect()`

```dart
Future connect(
  String config,    // Raw .ovpn file content
  String name,      // Display name for notification
  {
    String? username,
    String? password,
    List<String>? bypassPackages, // Android: packages to exclude from VPN
    bool certIsRequired = false,
  }
)
```

#### `disconnect()`

```dart
void disconnect()
```

#### Other methods

```dart
Future<VPNStage> stage()
Future<VpnStatus> status()
Future<bool> isConnected()
Future<bool> requestPermissionAndroid()
static Future<String?> filteredConfig(String? config)
```

#### `VpnStatus`

```dart
class VpnStatus {
  final DateTime? connectedOn; // Time VPN connected
  final String? duration;      // e.g. "00:05:32"
  final String? byteIn;        // Bytes received (as string)
  final String? byteOut;       // Bytes sent (as string)
  final String? packetsIn;
  final String? packetsOut;
}
```

---

### WireGuard API

#### `WireGuard` constructor

```dart
WireGuard({
  Function(WireGuardStatus? data)? onVpnStatusChanged,
  Function(WGStage stage, String rawStage)? onVpnStageChanged,
})
```

#### `initialize()`

```dart
Future<void> initialize({
  String? groupIdentifier,
  String? providerBundleIdentifier,
  String? localizedDescription,
  Function(WireGuardStatus)? lastStatus,
  Function(WGStage)? lastStage,
})
```

#### `connect()`

```dart
Future connect(
  String config,      // Raw WireGuard .conf content
  String tunnelName,  // Display name
)
```

#### Other methods

```dart
Future<void> disconnect()
Future<WGStage> stage()
Future<WireGuardStatus> status()
```

#### `WireGuardStatus`

```dart
class WireGuardStatus {
  final Duration? duration;         // Connection duration
  final String? lastPacketReceive;  // Last handshake timestamp
  final String? byteIn;             // Bytes received
  final String? byteOut;            // Bytes sent
}
```

---

### VPN Stages

#### OpenVPN (`VPNStage`)

| Stage | Description |
|---|---|
| `prepare` | Preparing to connect |
| `authenticating` | Verifying credentials |
| `connecting` | Establishing tunnel |
| `connected` | Tunnel is up |
| `disconnecting` | Tearing down tunnel |
| `disconnected` | Tunnel is down |
| `denied` | VPN permission denied |
| `error` | Connection error |
| `wait_connection` | Waiting for network |
| `get_config` | Fetching configuration |
| `tcp_connect` | TCP handshake |
| `udp_connect` | UDP handshake |
| `assign_ip` | IP assignment |
| `resolve` | DNS resolution |
| `exiting` | Process exiting |

#### WireGuard (`WGStage`)

| Stage | Description |
|---|---|
| `preparing` | Preparing to connect |
| `connecting` | Establishing tunnel |
| `connected` | Tunnel is up |
| `disconnecting` | Tearing down tunnel |
| `disconnected` | Tunnel is down |
| `denied` | VPN permission denied |
| `error` | Error occurred |

---

## Advanced Usage

### Split Tunneling (OpenVPN only)

Exclude specific apps from the VPN tunnel on Android:

```dart
await vpn.connect(
  ovpnConfig,
  'My Server',
  bypassPackages: [
    'com.google.android.youtube',
    'com.whatsapp',
  ],
);
```

### Filter Duplicate Remotes

If your `.ovpn` has many `remote` entries and causes ANR on some devices:

```dart
String? singleRemoteConfig = await OpenVPN.filteredConfig(rawConfig);
```

### Request Android Permission Manually

```dart
bool granted = await vpn.requestPermissionAndroid();
if (granted) {
  await vpn.connect(config, 'Server');
}
```

### Toggle Connection

```dart
if (await vpn.isConnected()) {
  vpn.disconnect();
} else {
  await vpn.connect(config, 'Server');
}
```

---

## Troubleshooting

### Android

| Problem | Solution |
|---|---|
| Build fails with 16 KB error | Install NDK `27.0.12077973`; set `enable16KPageSize=true` |
| VPN permission dialog not shown | Call `requestPermissionAndroid()` before `connect()` |
| `OpenVPN need to be initialized` | Call `initialize()` before `connect()` |
| ANR on connect | Use `OpenVPN.filteredConfig()` to reduce remote entries |

### iOS

| Problem | Solution |
|---|---|
| `groupIdentifier is required` | Pass all three iOS params to `initialize()` |
| Network Extension not found | Verify bundle IDs match `providerBundleIdentifier` in Xcode |
| Status not updating after disconnect | Register `onVpnStageChanged` before calling `initialize()` |

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for a complete version history.

---

## License

Licensed under the **GNU General Public License v3.0** – see [LICENSE](LICENSE) for details.

OpenVPN® is a registered trademark of OpenVPN Inc.  
WireGuard® is a registered trademark of Jason A. Donenfeld.

---

*Forked from [openvpn_flutter](https://github.com/nizwar/openvpn_flutter) and extended with WireGuard support and Android 15+ 16 KB page-size compatibility.*
