# AxeVPN Flutter Plugin - Pre-Deployment Verification

Run these checks before deploying to Git to ensure everything is ready.

## ✅ Automated Verification Commands

### 1. Check Package Name in pubspec.yaml
```bash
cat pubspec.yaml | grep "name:"
# Expected: name: axevpn_flutter
```

### 2. Check Version
```bash
cat pubspec.yaml | grep "version:"
# Expected: version: 2.0.0
```

### 3. Verify Flutter SDK Constraint
```bash
cat pubspec.yaml | grep -A 1 "sdk:"
# Expected: sdk: ">=3.0.0 <4.0.0"
#           flutter: ">=3.10.0"
```

### 4. Check Android Package in build.gradle
```bash
cat android/build.gradle | grep "namespace"
# Expected: namespace = "com.axevpn.flutter.openvpn"
```

### 5. Verify 16 KB Experimental Flag
```bash
cat android/build.gradle | grep "16KPageSize"
# Expected: experimentalProperties["android.experimental.enable16KPageSize"] = true
```

### 6. Check NDK Version
```bash
cat android/build.gradle | grep "ndkVersion"
# Expected: ndkVersion = "27.0.12077973"
```

### 7. Verify SDK Versions
```bash
cat android/build.gradle | grep -E "(compileSdk|targetSdk|minSdk)"
# Expected: 
# compileSdk = 36
# minSdk = 24
# targetSdk = 36
```

### 8. Check AndroidManifest Package
```bash
cat android/src/main/AndroidManifest.xml | grep "package="
# Expected: package="com.axevpn.flutter.openvpn"
```

### 9. Verify 16 KB Metadata in Manifest
```bash
cat android/src/main/AndroidManifest.xml | grep "max_page_size"
# Expected: android:name="android.max_page_size"
#           android:value="16384"
```

### 10. Check ProGuard Rules Exist
```bash
ls -la android/*.pro
# Expected: consumer-rules.pro and proguard-rules.pro
```

### 11. Verify Main Plugin Java File
```bash
ls -la android/src/main/java/com/axevpn/flutter/openvpn/
# Expected: AxeVPNFlutterPlugin.java
```

### 12. Check Dart Library File
```bash
cat lib/axevpn_flutter.dart | head -n 5
# Should contain: library axevpn_flutter;
```

### 13. Verify Channel Names in VPN Engine
```bash
cat lib/src/vpn_engine.dart | grep "MethodChannel\|EventChannel"
# Expected: com.axevpn.flutter.openvpn/vpncontrol
#           com.axevpn.flutter.openvpn/vpnstage
```

### 14. Check Documentation Files
```bash
ls -la *.md
# Expected files:
# README.md
# CHANGELOG.md
# CONTRIBUTING.md
# GIT_DEPLOYMENT_GUIDE.md
# GIT_QUICK_START.md
# 16KB_VERIFICATION.md
# MIGRATION_SUMMARY.md
# CLEANUP_GUIDE.md
# INDEX.md
# VERIFICATION.md (this file)
```

### 15. Verify .gitignore Exists
```bash
ls -la .gitignore
# Expected: .gitignore file exists
```

---

## 📊 Manual Verification Checklist

### Package Configuration
- [ ] Package name is `axevpn_flutter`
- [ ] Version is `2.0.0`
- [ ] Flutter SDK >= 3.10.0
- [ ] Dart SDK >= 3.0.0 < 4.0.0
- [ ] Description updated
- [ ] Homepage URL set (optional)

### Android Configuration
- [ ] Package: `com.axevpn.flutter.openvpn`
- [ ] compileSdk: 36
- [ ] targetSdk: 36
- [ ] minSdk: 24
- [ ] NDK: 27.0.12077973
- [ ] Gradle: 8.7.3
- [ ] Kotlin: 2.1.0
- [ ] Java: 17

### 16 KB Support
- [ ] `experimentalProperties["android.experimental.enable16KPageSize"] = true` in build.gradle
- [ ] `android:name="android.max_page_size"` in AndroidManifest
- [ ] `android:value="16384"` in AndroidManifest
- [ ] NDK 27+ configured

### Code Files
- [ ] `AxeVPNFlutterPlugin.java` exists
- [ ] Channel names updated to `com.axevpn.flutter.openvpn`
- [ ] Library name: `axevpn_flutter`
- [ ] VPN engine updated
- [ ] No old package references

### ProGuard Rules
- [ ] `consumer-rules.pro` exists
- [ ] `proguard-rules.pro` exists
- [ ] VPN classes kept
- [ ] Flutter plugin kept
- [ ] OpenVPN library kept

### Documentation
- [ ] README.md comprehensive
- [ ] CHANGELOG.md detailed
- [ ] CONTRIBUTING.md present
- [ ] Git guides created
- [ ] Migration guide complete
- [ ] 16 KB verification doc
- [ ] Cleanup guide present

### Git Preparation
- [ ] .gitignore file present
- [ ] All sensitive files ignored
- [ ] No build outputs committed
- [ ] No IDE files committed
- [ ] No local.properties

---

## 🔍 Deep Verification

### Build Configuration Validation
```bash
# Check if all required properties are set
cat android/build.gradle | grep -E "compileSdk|targetSdk|minSdk|ndkVersion|namespace|experimentalProperties"

# Verify Kotlin version
cat android/build.gradle | grep "kotlin"

# Check Java toolchain
cat android/build.gradle | grep "JavaLanguageVersion"
```

### Documentation Completeness
```bash
# Check README has installation section
cat README.md | grep -i "installation"

# Check CHANGELOG has version 2.0.0
cat CHANGELOG.md | grep "2.0.0"

# Verify Git guide has commands
cat GIT_QUICK_START.md | grep "git init"
```

### Code Quality
```bash
# Find any old package references (should only be in docs)
grep -r "id.laskarmedia" . --exclude-dir=.git

# Find any old import references
grep -r "openvpn_flutter" lib/ --exclude="*.md"

# Check for TODO or FIXME
grep -r "TODO\|FIXME" lib/ android/src/
```

---

## ⚠️ Common Issues to Check

### Issue 1: Old Package References
```bash
# Search for old package in code (not docs)
grep -r "id\.laskarmedia\|openvpn_flutter" lib/ android/src/ --exclude="*.md"

# Should return: No results (empty)
```

### Issue 2: Missing 16 KB Configuration
```bash
# Check both files have 16 KB flag
grep -r "16KPageSize" android/

# Should find in:
# - android/build.gradle
```

### Issue 3: Wrong SDK Versions
```bash
# Verify all SDK versions are correct
cat android/build.gradle | grep -E "compileSdk.*36|targetSdk.*36|minSdk.*24"

# Should match all three
```

### Issue 4: Missing Documentation
```bash
# Count markdown files
ls *.md | wc -l

# Should be: 10 files
```

---

## ✅ Final Checklist Before Git Init

### Code
- [ ] No compilation errors
- [ ] All package names updated
- [ ] Channel names updated
- [ ] No old references in code

### Configuration
- [ ] 16 KB flags enabled
- [ ] NDK 27 configured
- [ ] SDK 36 targeted
- [ ] ProGuard rules present

### Documentation
- [ ] All 10 .md files present
- [ ] README comprehensive
- [ ] Git guides complete
- [ ] Migration guide detailed

### Git Preparation
- [ ] .gitignore present
- [ ] No sensitive data
- [ ] No build outputs
- [ ] Ready to commit

---

## 🚀 Run All Checks at Once

```bash
#!/bin/bash

echo "AxeVPN Flutter Plugin - Pre-Deployment Verification"
echo "======================================================"
echo ""

# 1. Package name
echo "1. Package Name:"
grep "^name:" pubspec.yaml
echo ""

# 2. Version
echo "2. Version:"
grep "^version:" pubspec.yaml
echo ""

# 3. Android package
echo "3. Android Package:"
grep "namespace" android/build.gradle
echo ""

# 4. 16 KB flag
echo "4. 16 KB Support:"
grep "16KPageSize" android/build.gradle
echo ""

# 5. NDK version
echo "5. NDK Version:"
grep "ndkVersion" android/build.gradle
echo ""

# 6. SDK versions
echo "6. SDK Versions:"
grep -E "(compileSdk|targetSdk|minSdk)" android/build.gradle
echo ""

# 7. Plugin Java file
echo "7. Plugin Java File:"
ls android/src/main/java/com/axevpn/flutter/openvpn/AxeVPNFlutterPlugin.java
echo ""

# 8. ProGuard rules
echo "8. ProGuard Rules:"
ls android/*.pro
echo ""

# 9. Documentation files
echo "9. Documentation Files:"
ls *.md | wc -l
echo ""

# 10. Old references (should be none in code)
echo "10. Old Package References in Code:"
grep -r "id\.laskarmedia" lib/ android/src/ 2>/dev/null | wc -l
echo "(Should be 0)"
echo ""

echo "======================================================"
echo "Verification Complete!"
echo ""
echo "If all checks pass, run: git init && git add . && git commit -m 'Initial commit'"
```

Save as `verify.sh`, make executable with `chmod +x verify.sh`, then run:
```bash
./verify.sh
```

---

## 🎯 Success Criteria

All checks should show:
- ✅ Package name: axevpn_flutter
- ✅ Version: 2.0.0
- ✅ Android package: com.axevpn.flutter.openvpn
- ✅ 16 KB flag: enabled
- ✅ NDK: 27.0.12077973
- ✅ compileSdk: 36, targetSdk: 36, minSdk: 24
- ✅ Plugin Java file: exists
- ✅ ProGuard rules: 2 files
- ✅ Documentation: 10 files
- ✅ Old references: 0 in code

---

## 📝 Post-Verification

Once all checks pass:

1. ✅ Initialize Git
   ```bash
   git init
   ```

2. ✅ Add all files
   ```bash
   git add .
   ```

3. ✅ Create initial commit
   ```bash
   git commit -m "chore: Initial commit - AxeVPN Flutter Plugin v2.0.0"
   ```

4. ✅ Proceed to GitHub deployment
   See: [GIT_QUICK_START.md](GIT_QUICK_START.md)

---

**Verification Complete! Ready for Git Deployment! 🚀**

---

**Last Updated**: February 4, 2026  
**Plugin Version**: 2.0.0  
**Author**: AxeVPN Team
