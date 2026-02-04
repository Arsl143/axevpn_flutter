#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint openvpn_flutter.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'openvpn_flutter'
  s.version          = '2.0.0'
  s.summary          = 'AxeVPN Flutter Plugin - Enhanced OpenVPN Integration'
  s.description      = <<-DESC
AxeVPN Flutter Plugin provides comprehensive OpenVPN connectivity for Flutter applications
with full support for iOS 16+ and Network Extension.
                       DESC
  s.homepage         = 'https://github.com/Arsl143/axevpn_flutter'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'AxeVPN' => 'support@axevpn.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  
  # ✅ UPDATED: iOS 16.0+ for modern VPN Extension support
  s.platform = :ios, '16.0'
  s.ios.deployment_target = '16.0'

  # ✅ CRITICAL: Network Extension framework for VPN
  s.frameworks = 'NetworkExtension'
  
  # ✅ Exclude simulator architectures for smaller build
  s.pod_target_xcconfig = { 
    'DEFINES_MODULE' => 'YES', 
    'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386 arm64'
  }
  
  # ✅ UPDATED: Swift 5.9+
  s.swift_version = '5.9'
end
