# AxeVPN Flutter Plugin - Git Deployment Guide

This guide explains how to prepare, upload, and use the AxeVPN Flutter plugin from a Git repository.

## 📋 Pre-Deployment Checklist

### ✅ Code Completeness
- [x] All Dart files refactored to `axevpn_flutter`
- [x] Android package updated to `com.axevpn.flutter.openvpn`
- [x] Android build configurations upgraded (Gradle 8.7.3, NDK 27)
- [x] 16 KB page size support fully implemented
- [x] ProGuard rules created
- [x] Documentation updated (README, CHANGELOG, CONTRIBUTING)
- [x] License file present

### ✅ Version Information
- Package name: `axevpn_flutter`
- Version: `2.0.0`
- Flutter SDK: `>=3.10.0`
- Dart SDK: `>=3.0.0 <4.0.0`

## 🚀 Step-by-Step Deployment

### Step 1: Initialize Git Repository

```bash
cd C:\xampp\htdocs\axevpn_codecanyon\flutter_app\openvpn_flutter_plugin

# Initialize git (if not already done)
git init

# Add .gitignore
git add .gitignore

# Add all files
git add .

# Create initial commit
git commit -m "chore: Initial commit - AxeVPN Flutter Plugin v2.0.0

- Complete refactor from openvpn_flutter to axevpn_flutter
- Android 15+ support with 16 KB page sizes
- Upgraded to Flutter 3.10+ and Dart 3.0+
- Enhanced error handling and documentation
- ProGuard rules for release builds"
```

### Step 2: Create GitHub Repository

1. Go to https://github.com/new
2. Create a new repository:
   - Name: `axevpn_flutter`
   - Description: "OpenVPN Flutter plugin with Android 15+ and 16 KB page size support"
   - Visibility: Private or Public (your choice)
   - **Do NOT** initialize with README, .gitignore, or license

### Step 3: Push to GitHub

```bash
# Add remote origin (replace with your GitHub username/org)
git remote add origin https://github.com/YOUR_USERNAME/axevpn_flutter.git

# Verify remote
git remote -v

# Push to main branch
git branch -M main
git push -u origin main
```

### Step 4: Create Release Tag

```bash
# Create annotated tag for v2.0.0
git tag -a v2.0.0 -m "Release v2.0.0

Major Features:
- Android 15+ support with 16 KB page sizes
- Flutter 3.10+ and Dart 3.0+ compatibility
- Enhanced VPN engine stability
- Comprehensive documentation
- ProGuard rules for production

Breaking Changes:
- Package renamed to axevpn_flutter
- Minimum Android SDK 24
- Minimum Flutter 3.10.0"

# Push tag to GitHub
git push origin v2.0.0
```

### Step 5: Create GitHub Release

1. Go to your repository on GitHub
2. Click "Releases" → "Create a new release"
3. Choose tag `v2.0.0`
4. Release title: `v2.0.0 - Android 15+ Support`
5. Copy description from CHANGELOG.md
6. Attach any additional files if needed
7. Click "Publish release"

## 📦 Using the Plugin from Git

### Option 1: Main Branch (Latest)

In your Flutter app's `pubspec.yaml`:

```yaml
dependencies:
  axevpn_flutter:
    git:
      url: https://github.com/YOUR_USERNAME/axevpn_flutter.git
      ref: main
```

### Option 2: Specific Version Tag (Recommended)

```yaml
dependencies:
  axevpn_flutter:
    git:
      url: https://github.com/YOUR_USERNAME/axevpn_flutter.git
      ref: v2.0.0
```

### Option 3: Specific Commit

```yaml
dependencies:
  axevpn_flutter:
    git:
      url: https://github.com/YOUR_USERNAME/axevpn_flutter.git
      ref: abc1234  # commit hash
```

### Option 4: Private Repository with Authentication

For private repos, use SSH:

```yaml
dependencies:
  axevpn_flutter:
    git:
      url: git@github.com:YOUR_USERNAME/axevpn_flutter.git
      ref: v2.0.0
```

Setup SSH keys:
```bash
# Generate SSH key (if you don't have one)
ssh-keygen -t ed25519 -C "your_email@example.com"

# Add to GitHub: Settings → SSH and GPG keys → New SSH key
# Copy public key:
cat ~/.ssh/id_ed25519.pub
```

## 🔄 Updating Your Flutter App

After adding the dependency:

```bash
# Clean and get dependencies
flutter clean
flutter pub get

# Update imports in your code
# Old: import 'package:openvpn_flutter/openvpn_flutter.dart';
# New: import 'package:axevpn_flutter/axevpn_flutter.dart';

# Update MainActivity (Android)
# Old: import id.laskarmedia.openvpn_flutter.OpenVPNFlutterPlugin;
# New: import com.axevpn.flutter.openvpn.AxeVPNFlutterPlugin;

# Build and test
flutter build apk --release
flutter build ios --release
```

## 🔐 Environment Variables for CI/CD

If using GitHub Actions or other CI/CD:

```yaml
# .github/workflows/build.yml
name: Build
on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Flutter
        uses: subosito/flutter-action@v2
        with:
          flutter-version: '3.24.0'
          
      - name: Get dependencies
        run: flutter pub get
        
      - name: Build APK
        run: flutter build apk --release
        
      - name: Build AAB
        run: flutter build appbundle --release
```

## 📊 Version Management

### Updating the Plugin

When making changes to the plugin:

```bash
# Make your changes
git add .
git commit -m "feat: Your feature description"

# Update version in pubspec.yaml (e.g., 2.0.0 → 2.1.0)
# Update CHANGELOG.md

# Commit version bump
git commit -am "chore: Bump version to 2.1.0"

# Create new tag
git tag -a v2.1.0 -m "Release v2.1.0"

# Push changes and tags
git push origin main
git push origin v2.1.0

# Create GitHub release
```

### Semantic Versioning

- **MAJOR** (2.0.0 → 3.0.0): Breaking changes
- **MINOR** (2.0.0 → 2.1.0): New features, backward compatible
- **PATCH** (2.0.0 → 2.0.1): Bug fixes, backward compatible

## 🛠️ Troubleshooting

### Issue: "Package has no pubspec.yaml"

**Solution**: Ensure the repository contains `pubspec.yaml` in the root directory.

### Issue: "Git authentication failed"

**Solution**: 
- Use SSH instead of HTTPS for private repos
- Generate and add SSH key to GitHub
- Or use personal access token

### Issue: "Version solving failed"

**Solution**:
```bash
flutter pub cache repair
flutter clean
flutter pub get
```

### Issue: "Plugin not found after pub get"

**Solution**:
```bash
# Delete pubspec.lock
rm pubspec.lock

# Clean and reinstall
flutter clean
flutter pub get

# Restart IDE
```

## 📱 Testing the Plugin

### Test in Main App

```bash
cd C:\xampp\htdocs\axevpn_codecanyon\flutter_app

# Update pubspec.yaml
# dependencies:
#   axevpn_flutter:
#     git:
#       url: https://github.com/YOUR_USERNAME/axevpn_flutter.git
#       ref: v2.0.0

flutter pub get
flutter run
```

### Create Test Project

```bash
flutter create test_axevpn
cd test_axevpn

# Edit pubspec.yaml to add git dependency

flutter pub get
flutter run
```

## 🔍 Verifying 16 KB Support

After integration:

```bash
# Build release APK
flutter build apk --release

# Check gradle.properties
cat android/gradle.properties | grep "16K"

# Verify NDK version
cat android/app/build.gradle.kts | grep "ndkVersion"

# Check manifest metadata
cat android/app/src/main/AndroidManifest.xml | grep "max_page_size"
```

## 📝 Documentation Links

- [README.md](README.md) - Usage guide
- [CHANGELOG.md](CHANGELOG.md) - Version history
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- [LICENSE](LICENSE) - License information

## 🎯 Next Steps

1. ✅ Initialize Git repository
2. ✅ Create GitHub repository
3. ✅ Push code to GitHub
4. ✅ Create release tag
5. ✅ Create GitHub release
6. ✅ Update main app's pubspec.yaml
7. ✅ Test integration
8. ✅ Build and verify

## 📞 Support

For issues or questions:
- GitHub Issues: https://github.com/YOUR_USERNAME/axevpn_flutter/issues
- Email: support@axevpn.com

---

**Ready for Production! 🚀**
