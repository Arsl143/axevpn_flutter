# AxeVPN Flutter Plugin

[![pub package](https://img.shields.io/badge/pub-v2.0.0-blue)](https://pub.dev/packages/axevpn_flutter)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-15%2B-brightgreen)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-12%2B-blue)](https://developer.apple.com/ios)

Advanced OpenVPN Flutter plugin with **16 KB page size support** for Android 15+. Forked and enhanced specifically for AxeVPN application with modern features and improved stability.

## ✨ Features

- ✅ **Android 15+ Support** - Full 16 KB memory page size compatibility
- ✅ **Modern SDK** - Built with latest Flutter 3.10+ and Dart 3.0+
- ✅ **Real-time Monitoring** - Live connection status and stage updates
- ✅ **Split Tunneling** - Package bypass support for selective routing
- ✅ **iOS Support** - Network Extension integration
- ✅ **Protocol Support** - Both TCP and UDP protocols
- ✅ **Auto-reconnection** - Built-in connection recovery
- ✅ **Enhanced Security** - Latest OpenVPN core with security updates

## 🎯 16 KB Page Size Support

This plugin is fully compatible with Google Play's Android 15+ requirement for 16 KB memory page sizes (deadline: May 31, 2026).

### What's Implemented:
- ✅ `android.experimental.enable16KPageSize=true` in build configuration
- ✅ NDK 27.0.12077973 with 16 KB alignment support
- ✅ compileSdk 36 and targetSdk 36 (Android 15+)
- ✅ Proper metadata in AndroidManifest
- ✅ 64-bit architecture support (arm64-v8a, x86_64)

## 📦 Installation

### From Git Repository

Add this to your `pubspec.yaml`:

```yaml
dependencies:
  axevpn_flutter:
    git:
      url: https://github.com/yourusername/axevpn_flutter.git
      ref: main  # or specific tag like v2.0.0
```

### From Local Path (Development)

```yaml
dependencies:
  axevpn_flutter:
    path: ../openvpn_flutter_plugin
```

Then run:

```bash
flutter pub get
```

## 🚀 Quick Start

### 1. Import the Package

```dart
import 'package:axevpn_flutter/axevpn_flutter.dart';
```

### 2. Initialize VPN Engine

```dart
late OpenVPN vpnEngine;

@override
void initState() {
  super.initState();
  
  vpnEngine = OpenVPN(
    onVpnStatusChanged: (status) {
      print('VPN Status: $status');
    },
    onVpnStageChanged: (stage, rawStage) {
      print('VPN Stage: $stage');
    },
  );
  
  // Initialize the engine
  vpnEngine.initialize(
    groupIdentifier: "group.com.yourapp.vpn",  // iOS only
    providerBundleIdentifier: "com.yourapp.vpnextension",  // iOS only
    localizedDescription: "AxeVPN Connection",  // iOS only
  );
}
```

### 3. Connect to VPN

```dart
Future<void> connectVPN() async {
  String ovpnConfig = """
client
dev tun
proto udp
remote your-server.com 1194
resolv-retry infinite
nobind
persist-key
persist-tun
remote-cert-tls server
auth SHA512
cipher AES-256-CBC
verb 3
<ca>
-----BEGIN CERTIFICATE-----
...your certificate...
-----END CERTIFICATE-----
</ca>
<cert>
-----BEGIN CERTIFICATE-----
...your certificate...
-----END CERTIFICATE-----
</cert>
<key>
-----BEGIN PRIVATE KEY-----
...your key...
-----END PRIVATE KEY-----
</key>
""";

  try {
    await vpnEngine.connect(
      ovpnConfig,
      "VPN Server Name",
      username: "your_username",  // Optional
      password: "your_password",  // Optional
      bypassPackages: [],  // Optional - packages to bypass VPN
    );
  } catch (e) {
    print('Connection error: $e');
  }
}
```

### 4. Disconnect from VPN

```dart
Future<void> disconnectVPN() async {
  await vpnEngine.disconnect();
}
```

### 5. Check Connection Status

```dart
Future<void> checkStatus() async {
  VPNStage? currentStage = await vpnEngine.getStage();
  print('Current stage: $currentStage');
}
```

## 📱 Platform Configuration

### Android Configuration

#### 1. Update `MainActivity.kt`:

```kotlin
import com.axevpn.flutter.openvpn.AxeVPNFlutterPlugin

override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    AxeVPNFlutterPlugin.connectWhileGranted(requestCode == 24 && resultCode == RESULT_OK)
    super.onActivityResult(requestCode, resultCode, data)
}
```

#### 2. Update `android/app/build.gradle.kts`:

```kotlin
android {
    compileSdk = 36
    ndkVersion = "27.0.12077973"
    
    // Enable 16 KB page size support
    experimentalProperties["android.experimental.enable16KPageSize"] = true
    
    defaultConfig {
        minSdk = 24
        targetSdk = 36
    }
    
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```

#### 3. Update `android/gradle.properties`:

```properties
android.experimental.enable16KPageSize=true
android.bundle.enableUncompressedNativeLibs=false
```

#### 4. Update `AndroidManifest.xml`:

```xml
<application
    android:extractNativeLibs="true"
    ...>
    
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
</application>
```

### iOS Configuration

#### 1. Add Capabilities

Add `App Groups` and `Network Extensions` capabilities to the Runner's target in Xcode.

#### 2. Create Network Extension Target

Add a new Network Extension target:
- Click `+` button
- Choose `NETWORK EXTENSION`
- Add the same capabilities as Runner

#### 3. Update Podfile

Add to `ios/Podfile`:

```ruby
target 'VPNExtension' do
  use_frameworks!
  pod 'OpenVPNAdapter', :git => 'https://github.com/ss-abramchuk/OpenVPNAdapter.git', :tag => '0.8.0'
end
```

#### 4. Configure PacketTunnelProvider

Copy [PacketTunnelProvider.swift](https://raw.githubusercontent.com/nizwar/openvpn_flutter/master/example/ios/VPNExtension/PacketTunnelProvider.swift) to `VPNExtension/PacketTunnelProvider.swift`.

## 🎨 VPN Stages

- `prepare` - Preparing to connect
- `authenticating` - Authenticating credentials
- `connecting` - Establishing connection
- `connected` - Successfully connected
- `disconnected` - Disconnected
- `disconnecting` - Disconnecting
- `denied` - Permission denied
- `error` - Connection error
- `wait_connection` - Waiting for connection
- `get_config` - Getting configuration
- `tcp_connect` - TCP connection
- `udp_connect` - UDP connection
- `assign_ip` - Assigning IP address
- `resolve` - Resolving DNS
- `exiting` - Exiting connection

## 🔧 Advanced Usage

### Split Tunneling (Package Bypass)

```dart
await vpnEngine.connect(
  ovpnConfig,
  "Server Name",
  bypassPackages: [
    'com.google.android.youtube',
    'com.whatsapp',
  ],
);
```

### Listen to Connection Stats

```dart
vpnEngine.onVpnStatusChanged = (status) {
  if (status != null) {
    print('Duration: ${status.duration}');
    print('Bytes In: ${status.byteIn}');
    print('Bytes Out: ${status.byteOut}');
  }
};
```

## 📋 Requirements

- **Flutter**: >= 3.10.0
- **Dart**: >= 3.0.0
- **Android**: >= 7.0 (API 24)
- **iOS**: >= 12.0
- **Android NDK**: 27.0.12077973 (for 16 KB support)

## 🐛 Troubleshooting

### Android Issues

**Build fails with "16 KB not supported":**
- Ensure NDK 27.0.12077973 is installed
- Check `android.experimental.enable16KPageSize=true` is set
- Verify compileSdk is 36

**VPN not connecting:**
- Check permissions are granted
- Verify .ovpn configuration is valid
- Ensure internet connection is available

### iOS Issues

**Network Extension not found:**
- Verify extension target is added to Xcode
- Check app groups are configured correctly
- Ensure bundle identifiers match

## 📝 Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Credits

- Forked from [openvpn_flutter](https://github.com/nizwar/openvpn_flutter) by nizwar
- Built on top of [openvpn_library](https://github.com/nizwar/openvpn_library)
- Enhanced and maintained by AxeVPN Team

---

**Made with ❤️ by M ARSLAB AxeVPN Team**
