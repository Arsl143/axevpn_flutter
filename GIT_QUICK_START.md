# AxeVPN Flutter Plugin - Quick Start Git Commands

This file contains ready-to-use Git commands for deploying the plugin. Copy and paste these commands to quickly set up your Git repository.

## 🚀 Initial Setup (Run Once)

### 1. Navigate to Plugin Directory
```bash
cd C:\xampp\htdocs\axevpn_codecanyon\flutter_app\openvpn_flutter_plugin
```

### 2. Initialize Git (if not already done)
```bash
git init
```

### 3. Configure Git (if first time)
```bash
# Set your name and email
git config user.name "Your Name"
git config user.email "your.email@example.com"

# Verify configuration
git config --list
```

### 4. Add All Files
```bash
git add .
```

### 5. Create Initial Commit
```bash
git commit -m "chore: Initial commit - AxeVPN Flutter Plugin v2.0.0

- Complete refactor from openvpn_flutter to axevpn_flutter
- Android 15+ support with 16 KB page sizes
- Upgraded to Flutter 3.10+ and Dart 3.0+
- Enhanced error handling and documentation
- ProGuard rules for release builds
- Comprehensive documentation with deployment guides"
```

## 📦 GitHub Repository Setup

### 1. Create Repository on GitHub
1. Go to https://github.com/new
2. Repository name: `axevpn_flutter`
3. Description: `OpenVPN Flutter plugin with Android 15+ and 16 KB page size support`
4. Visibility: **Private** (or Public if you prefer)
5. Do NOT initialize with README, .gitignore, or license
6. Click "Create repository"

### 2. Add Remote Origin
```bash
# Replace YOUR_USERNAME with your actual GitHub username or organization
git remote add origin https://github.com/YOUR_USERNAME/axevpn_flutter.git

# Verify remote
git remote -v
```

### 3. Push to GitHub
```bash
# Rename branch to main (if needed)
git branch -M main

# Push to GitHub
git push -u origin main
```

## 🏷️ Create Version Tag

### Create and Push v2.0.0 Tag
```bash
git tag -a v2.0.0 -m "Release v2.0.0 - Android 15+ Support

Major Features:
- Android 15+ support with 16 KB page sizes
- Flutter 3.10+ and Dart 3.0+ compatibility
- Enhanced VPN engine stability
- Comprehensive documentation
- ProGuard rules for production

Breaking Changes:
- Package renamed to axevpn_flutter
- Minimum Android SDK 24
- Minimum Flutter 3.10.0

Technical Highlights:
- NDK 27.0.12077973
- Gradle 8.7.3, Kotlin 2.1.0
- Target SDK 36 (Android 15)
- 16KB experimental flag enabled
- Modern AndroidX dependencies"

# Push tag
git push origin v2.0.0
```

### Verify Tag
```bash
# List all tags
git tag -l

# Show tag details
git show v2.0.0
```

## 📝 GitHub Release

After pushing the tag, create a GitHub release:

1. Go to: `https://github.com/YOUR_USERNAME/axevpn_flutter/releases/new`
2. Choose tag: `v2.0.0`
3. Release title: `v2.0.0 - Android 15+ Support & 16 KB Page Sizes`
4. Description: Copy from CHANGELOG.md
5. Click "Publish release"

Or use GitHub CLI:
```bash
# Install GitHub CLI first: https://cli.github.com/

# Create release
gh release create v2.0.0 \
  --title "v2.0.0 - Android 15+ Support" \
  --notes-file CHANGELOG.md
```

## 🔄 Future Updates

### Make Changes and Commit
```bash
# Make your code changes...

# Check status
git status

# Add changes
git add .

# Commit with descriptive message
git commit -m "feat: Add new feature XYZ"

# Push to GitHub
git push origin main
```

### Create New Version Tag
```bash
# For version 2.0.1 (patch)
git tag -a v2.0.1 -m "Release v2.0.1 - Bug fixes"
git push origin v2.0.1

# For version 2.1.0 (minor)
git tag -a v2.1.0 -m "Release v2.1.0 - New features"
git push origin v2.1.0

# For version 3.0.0 (major)
git tag -a v3.0.0 -m "Release v3.0.0 - Breaking changes"
git push origin v3.0.0
```

## 📱 Using Plugin in Main App

### Update pubspec.yaml
```yaml
dependencies:
  axevpn_flutter:
    git:
      url: https://github.com/YOUR_USERNAME/axevpn_flutter.git
      ref: v2.0.0  # or 'main' for latest
```

### Get Dependencies
```bash
cd C:\xampp\htdocs\axevpn_codecanyon\flutter_app

flutter clean
flutter pub get
```

## 🔧 Useful Git Commands

### View Commit History
```bash
# Show recent commits
git log --oneline -10

# Show detailed log
git log --graph --decorate --all

# Show specific file history
git log -- README.md
```

### Check Repository Status
```bash
# Show modified files
git status

# Show differences
git diff

# Show differences for staged files
git diff --staged
```

### Undo Changes
```bash
# Discard unstaged changes
git checkout -- <file>

# Unstage file
git reset HEAD <file>

# Undo last commit (keep changes)
git reset --soft HEAD~1

# Undo last commit (discard changes)
git reset --hard HEAD~1
```

### Branch Management
```bash
# Create new branch
git branch feature-xyz
git checkout feature-xyz
# Or: git checkout -b feature-xyz

# Switch back to main
git checkout main

# Merge feature branch
git merge feature-xyz

# Delete branch
git branch -d feature-xyz
```

### Remote Management
```bash
# Show remotes
git remote -v

# Add remote
git remote add origin <url>

# Change remote URL
git remote set-url origin <new-url>

# Remove remote
git remote remove origin
```

### Tag Management
```bash
# List all tags
git tag

# Create lightweight tag
git tag v2.0.2

# Create annotated tag
git tag -a v2.0.2 -m "Version 2.0.2"

# Push specific tag
git push origin v2.0.2

# Push all tags
git push origin --tags

# Delete local tag
git tag -d v2.0.2

# Delete remote tag
git push origin --delete v2.0.2
```

## 🔐 SSH Setup (for Private Repos)

### Generate SSH Key
```bash
# Generate new SSH key
ssh-keygen -t ed25519 -C "your.email@example.com"

# Start SSH agent
eval "$(ssh-agent -s)"

# Add SSH key
ssh-add ~/.ssh/id_ed25519

# Copy public key
cat ~/.ssh/id_ed25519.pub
```

### Add to GitHub
1. Go to: https://github.com/settings/keys
2. Click "New SSH key"
3. Title: "AxeVPN Dev Machine"
4. Paste public key
5. Click "Add SSH key"

### Use SSH Remote
```bash
# Remove HTTPS remote
git remote remove origin

# Add SSH remote
git remote add origin git@github.com:YOUR_USERNAME/axevpn_flutter.git

# Push
git push -u origin main
```

## 📊 Repository Statistics

### View Repository Info
```bash
# Count commits
git rev-list --count HEAD

# Count files
git ls-files | wc -l

# Repository size
du -sh .git

# Contributors
git shortlog -sn
```

## 🎯 Quick Checklist

### Before First Push
- [ ] `git init` completed
- [ ] `.gitignore` file present
- [ ] All files added with `git add .`
- [ ] Initial commit created
- [ ] Remote origin added
- [ ] Pushed to GitHub successfully
- [ ] Tag v2.0.0 created
- [ ] Tag pushed to GitHub
- [ ] GitHub release created

### After Push
- [ ] Repository visible on GitHub
- [ ] All files present
- [ ] README renders correctly
- [ ] Tag appears in releases
- [ ] Can clone repository
- [ ] Main app can use as dependency

## 🚨 Common Issues

### "Authentication Failed"
```bash
# Use SSH instead of HTTPS
git remote set-url origin git@github.com:YOUR_USERNAME/axevpn_flutter.git
```

### "Permission Denied"
```bash
# Check SSH key
ssh -T git@github.com

# Add SSH key if needed
ssh-add ~/.ssh/id_ed25519
```

### "Already Exists"
```bash
# Force push (use with caution!)
git push -f origin main
```

### "Tag Already Exists"
```bash
# Delete and recreate tag
git tag -d v2.0.0
git tag -a v2.0.0 -m "New message"
git push -f origin v2.0.0
```

## 📞 Help

- GitHub Docs: https://docs.github.com/
- Git Docs: https://git-scm.com/doc
- GitHub CLI: https://cli.github.com/manual/

---

## 🎉 Ready to Deploy!

Copy the commands from "Initial Setup" section and execute them in order. Your plugin will be deployed to GitHub in minutes!

**Replace `YOUR_USERNAME` with your actual GitHub username before running commands.**

---

**Quick Start Version**: 1.0
**Last Updated**: February 4, 2026
