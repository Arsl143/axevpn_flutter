## 3.0.1
* **Fix: WireGuard foreground-service crash (Android 12-15)** – `com.wireguard.android.backend.GoBackend$VpnService` (the service Envato's `ForegroundServiceDidNotStartInTimeException` report named) ships in the upstream `tunnel` AAR with no `foregroundServiceType` at all; declared it explicitly with `specialUse` so `startForeground()` no longer throws `MissingForegroundServiceTypeException` on API 34+.
* **Fix: removed a redundant, non-functional `AxeWireGuardVpnService` start** that raced `GoBackend`'s own `VpnService.Builder().establish()` call for the same tunnel, which was destabilizing the real WireGuard connection.
* **Fix: corrected VPN foreground service type** for OpenVPN, V2Ray, and WireGuard services from `connectedDevice`/`systemExempted` to `specialUse` with the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE=vpn` property. `systemExempted` requires a `signature|privileged` permission a third-party app can never hold, and `connectedDevice` is for companion/wearable links, not VPN — both were rejected by Play policy review and/or crashed on Android 14+.
* **Fix: V2Ray `libv2ray.aar` not packaged at runtime** – the AAR is `compileOnly` in this plugin (AGP rejects bundling an AAR inside another AAR); consuming apps must add `implementation(files("libs/libv2ray.aar"))` themselves. Documented this requirement and applied it in the bundled example/host app, fixing `ClassNotFoundException`/`UnsatisfiedLinkError` on V2Ray connect.

## 2.0.0
* **New: WireGuard protocol support** – full Android and iOS integration via native WireGuard-Go and WireGuardKit
* **Android 15+ 16 KB page-size compatibility** – NDK 27, `enable16KPageSize=true`, verified on API 36 device
* **Dual-protocol API** – separate `OpenVPN` and `WireGuard` engine classes with identical lifecycle (`initialize` → `connect` → `disconnect`)
* **New `WireGuardStatus` model** – duration, byteIn, byteOut, lastPacketReceive
* **New `WGStage` enum** – preparing, connecting, connected, disconnecting, disconnected, denied, error
* **Unified plugin class** – `AxeVPNFlutterPlugin` handles both OpenVPN and WireGuard on Android
* **iOS 16.0+** – WireGuardKit integration via Network Extension
* Upgraded compileSdk to 36 and minSdk to 24
* Updated Gradle wrapper and NDK to latest LTS versions
* Improved channel naming to `com.axevpn.flutter.*` namespace
* Added `isConnected()` helper to `OpenVPN` engine
* Updated dependencies: Flutter ≥ 3.10, Dart ≥ 3.0
* Comprehensive API documentation on all public classes and methods

## 1.3.4
* Fix notification issue for newest SDK
## 1.3.3
* Solving namespace for android build
## 1.3.2
* Fix datetime that being nulled (VPN Status)
## 1.3.1
* Upgrade library gradles
* Update native for Permission channel name ($APPNAME VPN Background & $APPNAME VPN Stats)
* Clearify notifications on readme.md
## 1.3.0
* Adept for SDK 34
* Solving #105, #29, #99
* Update examples to support flutter latest flutter (3.22.2 tested)
## 1.2.2
* Update openvpnlib
## 1.2.1
* Update openvpnlib
## 1.2.0+1
* Update docs
## 1.2.0
* Fix iOS issues (byteIn and byteOut not updated) #6
* Add lastStatus and lastStage listener while initialize
* Add packetsIn, packetsOut and connectedOn status
* Update licenses to GPL3 (Before it was MIT)
## 1.1.3
* Add permission request for android ```requestPermissionAndroid()``` #5
* Continue the connection after user grant vpn connection #8
## 1.1.2
* Fix exported errors on SDK 31
* PendingIntent errors fixes
## 1.1.1+1
* Update readme, more detail about iOS setup
## 1.1.1
* Fix iOS by Adding PacketTunnelProvider
* More detail at instructions
* Fix VPNStatus not updated after disconnected
## 1.1.0
* Add 'raw' on onVpnStageChanged
* Add details on doc
* Fix duration still going on failed to connect
## 1.0.1+2
* Solving scores
## 1.0.1+1
* iOS Support
* Android Support
