#import "AxeVPNFlutterPlugin.h"
#if __has_include(<axevpn_flutter/axevpn_flutter-Swift.h>)
#import <axevpn_flutter/axevpn_flutter-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "axevpn_flutter-Swift.h"
#endif

@implementation AxeVPNFlutterPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftAxeVPNFlutterPlugin registerWithRegistrar:registrar];
}
@end
