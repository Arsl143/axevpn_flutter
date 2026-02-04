# AxeVPN Flutter Plugin - Post-Migration Cleanup Guide

After the plugin has been successfully deployed to Git and integrated into your main app, follow this guide to clean up old files and finalize the migration.

## 🗑️ Old Files to Remove

### 1. Old Android Package Structure

The old package `id.laskarmedia.openvpn_flutter` is no longer needed:

```bash
# Navigate to plugin directory
cd C:\xampp\htdocs\axevpn_codecanyon\flutter_app\openvpn_flutter_plugin

# Remove old Java package (ONLY after Git deployment and verification)
rm -rf android/src/main/java/id/
```

**⚠️ WARNING**: Only delete after:
1. ✅ New plugin is pushed to Git
2. ✅ Main app is updated to use new plugin from Git
3. ✅ Main app builds successfully
4. ✅ VPN connection tested and works

### 2. Verify Old Files Don't Exist

Check these don't exist or remove if they do:

```bash
# Should NOT exist after cleanup:
android/src/main/java/id/laskarmedia/
android/src/main/kotlin/id/laskarmedia/
```

## ✅ What to Keep

### Plugin Directory Structure (Keep All)
```
openvpn_flutter_plugin/
├── lib/                          ✅ KEEP
│   ├── axevpn_flutter.dart
│   └── src/
├── android/                      ✅ KEEP
│   ├── src/main/java/com/axevpn/ ✅ KEEP (NEW)
│   ├── build.gradle              ✅ KEEP
│   ├── AndroidManifest.xml       ✅ KEEP
│   ├── consumer-rules.pro        ✅ KEEP
│   └── proguard-rules.pro        ✅ KEEP
├── ios/                          ✅ KEEP
├── pubspec.yaml                  ✅ KEEP
├── README.md                     ✅ KEEP
├── CHANGELOG.md                  ✅ KEEP
├── CONTRIBUTING.md               ✅ KEEP
├── GIT_DEPLOYMENT_GUIDE.md       ✅ KEEP
├── 16KB_VERIFICATION.md          ✅ KEEP
├── MIGRATION_SUMMARY.md          ✅ KEEP
├── CLEANUP_GUIDE.md              ✅ KEEP (this file)
└── .gitignore                    ✅ KEEP
```

## 📋 Post-Deployment Checklist

### Step 1: Verify Git Repository
- [ ] Plugin pushed to GitHub
- [ ] v2.0.0 tag created
- [ ] GitHub release published
- [ ] README displays correctly on GitHub
- [ ] All files visible in repository

### Step 2: Verify Main App Integration
- [ ] pubspec.yaml updated with git dependency
- [ ] `flutter pub get` succeeds
- [ ] Import statements updated
- [ ] MainActivity updated (Android)
- [ ] App compiles without errors
- [ ] No missing dependency errors

### Step 3: Functional Testing
- [ ] VPN connects successfully
- [ ] VPN disconnects properly
- [ ] Stage transitions work
- [ ] Status updates received
- [ ] Error handling works
- [ ] Split tunneling (if used)
- [ ] Background operation
- [ ] App restart persistence

### Step 4: Build Testing
- [ ] Debug APK builds
- [ ] Release APK builds
- [ ] Release AAB builds
- [ ] ProGuard doesn't break VPN
- [ ] No R8 optimization issues
- [ ] Native libraries included

### Step 5: Device Testing
- [ ] Test on Android 7.0 (minSdk 24)
- [ ] Test on Android 14
- [ ] Test on Android 15 (16 KB support)
- [ ] Test on physical device
- [ ] Test on emulator
- [ ] Test on different architectures

### Step 6: Cleanup Old Files
- [ ] Old Java package verified unused
- [ ] Delete `id/laskarmedia/` directory
- [ ] Commit cleanup changes
- [ ] Push to Git
- [ ] Create cleanup tag (optional)

## 🔍 Verification Commands

### Check No Old References Remain

```bash
# Search for old package references in plugin
cd openvpn_flutter_plugin
grep -r "id.laskarmedia" . --exclude-dir=.git

# Should only find references in:
# - CHANGELOG.md (historical)
# - README.md (migration guide)
# - MIGRATION_SUMMARY.md (documentation)
```

### Verify New Package Used

```bash
# Search for new package in plugin
grep -r "com.axevpn.flutter.openvpn" android/

# Should find in:
# - build.gradle
# - AndroidManifest.xml
# - AxeVPNFlutterPlugin.java
# - consumer-rules.pro
# - proguard-rules.pro
```

### Check Main App Updated

```bash
cd ../.. # Back to flutter_app root

# Check pubspec.yaml
cat pubspec.yaml | grep -A 5 "axevpn_flutter"

# Check imports
grep -r "import 'package:axevpn_flutter" lib/

# Check MainActivity (if Kotlin)
grep -r "com.axevpn.flutter.openvpn" android/app/src/
```

## 🧹 Git Cleanup (Plugin Repository)

After verification, clean up the plugin repository:

```bash
cd openvpn_flutter_plugin

# Remove old Java package
rm -rf android/src/main/java/id/

# Commit cleanup
git add .
git commit -m "chore: Remove old package structure

- Removed id.laskarmedia.openvpn_flutter package
- All references migrated to com.axevpn.flutter.openvpn
- Plugin fully refactored to axevpn_flutter"

# Push cleanup
git push origin main

# Optional: Create cleanup tag
git tag -a v2.0.1 -m "Cleanup old package structure"
git push origin v2.0.1
```

## 📱 Main App Migration Checklist

### Files to Update in Main App

1. **pubspec.yaml**
```yaml
dependencies:
  axevpn_flutter:
    git:
      url: https://github.com/YOUR_USERNAME/axevpn_flutter.git
      ref: v2.0.0
```

2. **All Dart files using the plugin**
```dart
// Old
import 'package:openvpn_flutter/openvpn_flutter.dart';

// New
import 'package:axevpn_flutter/axevpn_flutter.dart';
```

3. **android/app/src/main/kotlin/.../MainActivity.kt**
```kotlin
// Old
import id.laskarmedia.openvpn_flutter.OpenVPNFlutterPlugin

// New
import com.axevpn.flutter.openvpn.AxeVPNFlutterPlugin
```

4. **android/app/build.gradle.kts**
```kotlin
android {
    compileSdk = 36
    ndkVersion = "27.0.12077973"
    
    experimentalProperties["android.experimental.enable16KPageSize"] = true
    
    defaultConfig {
        minSdk = 24
        targetSdk = 36
    }
}
```

5. **android/gradle.properties**
```properties
android.experimental.enable16KPageSize=true
android.bundle.enableUncompressedNativeLibs=false
```

6. **android/app/src/main/AndroidManifest.xml**
```xml
<application
    android:extractNativeLibs="true">
</application>
```

## 🚀 Final Build and Release

### Build Release Versions

```bash
cd flutter_app

# Clean build
flutter clean
flutter pub get

# Build APK
flutter build apk --release

# Build AAB for Play Store
flutter build appbundle --release

# Verify outputs
ls -lh build/app/outputs/bundle/release/
ls -lh build/app/outputs/apk/release/
```

### Verify 16 KB Support

```bash
# Check app build config
cat android/gradle.properties | grep "16K"
cat android/app/build.gradle.kts | grep "16K"

# Extract and verify AAB
bundletool build-apks \
  --bundle=build/app/outputs/bundle/release/app-release.aab \
  --output=app.apks

# Check APK set
bundletool get-size total --apks=app.apks
```

## 📊 Success Metrics

### Plugin Quality
✅ All old package references removed
✅ Only new package structure exists
✅ Git repository clean
✅ No build warnings
✅ ProGuard rules work

### App Integration
✅ App builds successfully
✅ VPN connects reliably
✅ No runtime errors
✅ 16 KB support verified
✅ Ready for Play Store

### Documentation
✅ All guides up to date
✅ README accurate
✅ CHANGELOG complete
✅ Migration paths clear
✅ Troubleshooting helpful

## 🎯 Timeline

### Immediate (Day 1)
- [x] Complete plugin refactor
- [x] Create documentation
- [x] Push to Git
- [ ] Deploy to GitHub
- [ ] Test Git dependency

### Short-term (Week 1)
- [ ] Update main app
- [ ] Functional testing
- [ ] Build testing
- [ ] Device testing
- [ ] Clean up old files

### Mid-term (Week 2-4)
- [ ] Deploy to Play Store
- [ ] Monitor crash reports
- [ ] Address issues
- [ ] Optimize performance
- [ ] User feedback

## ⚠️ Important Reminders

1. **Backup Everything**: Before deleting old files
2. **Test Thoroughly**: On multiple devices and Android versions
3. **Version Control**: Commit frequently during migration
4. **Documentation**: Update as you discover issues
5. **Rollback Plan**: Keep old version available temporarily

## 📞 Support

If you encounter issues during cleanup:

1. **Check GitHub Issues**: See if others had similar problems
2. **Review Documentation**: README, CHANGELOG, guides
3. **Test in Isolation**: Create minimal test project
4. **Verify Dependencies**: Ensure all packages compatible
5. **Check Logs**: Android Studio logcat, Flutter console

## 🎊 Migration Complete!

Once all checkboxes are marked:
- ✅ Plugin deployed to Git
- ✅ Main app updated and tested
- ✅ Old files cleaned up
- ✅ Builds succeed
- ✅ VPN works reliably
- ✅ Ready for production

**Congratulations!** The migration from `openvpn_flutter` to `axevpn_flutter` is complete. 🚀

---

**Last Updated**: February 4, 2026
**Migration Version**: 1.3.4 → 2.0.0
**Author**: AxeVPN Team
