# Contributing to AxeVPN Flutter Plugin

Thank you for considering contributing to the AxeVPN Flutter Plugin! This document outlines the guidelines for contributing to the project.

## 🎯 How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When creating a bug report, include:

- **Clear and descriptive title**
- **Steps to reproduce** the behavior
- **Expected behavior** vs. **actual behavior**
- **Screenshots** if applicable
- **Environment details**:
  - Flutter version
  - Dart version
  - Android/iOS version
  - Device model
- **Error logs** or stack traces

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion:

- **Use a clear and descriptive title**
- **Provide a detailed description** of the suggested enhancement
- **Explain why this enhancement would be useful**
- **Provide examples** or mockups if applicable

### Pull Requests

1. **Fork the repository** and create your branch from `main`
2. **Follow the coding style** of the project
3. **Write clear commit messages**
4. **Add tests** if applicable
5. **Update documentation** as needed
6. **Ensure all tests pass**
7. **Submit the pull request**

## 📝 Development Guidelines

### Code Style

- Follow the [Dart Style Guide](https://dart.dev/guides/language/effective-dart/style)
- Use `flutter format` before committing
- Run `flutter analyze` to check for issues
- Keep methods concise and focused
- Add meaningful comments for complex logic

### Commit Messages

Use clear and meaningful commit messages:

```
feat: Add split tunneling for specific apps
fix: Resolve connection timeout on Android 14+
docs: Update README with 16 KB configuration
refactor: Improve VPN engine error handling
test: Add unit tests for connection flow
```

Prefix types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style changes
- `refactor`: Code refactoring
- `test`: Adding tests
- `chore`: Maintenance tasks

### Testing

- Write unit tests for new functionality
- Test on both Android and iOS
- Test on different Android versions (especially 14+)
- Verify 16 KB page size compatibility
- Test with various VPN configurations

### Building

```bash
# Get dependencies
flutter pub get

# Analyze code
flutter analyze

# Format code
flutter format lib/ android/ ios/

# Run tests
flutter test

# Build example app
cd example
flutter build apk --release
flutter build ios --release
```

## 🔧 Project Structure

```
openvpn_flutter_plugin/
├── lib/                   # Dart code
│   ├── src/              # Source files
│   └── axevpn_flutter.dart  # Main export
├── android/              # Android native code
│   ├── src/
│   ├── build.gradle
│   └── AndroidManifest.xml
├── ios/                  # iOS native code
├── example/              # Example app
├── test/                 # Unit tests
├── CHANGELOG.md          # Version history
└── README.md             # Documentation
```

## 🚀 Release Process

1. Update version in `pubspec.yaml`
2. Update `CHANGELOG.md` with release notes
3. Commit changes: `git commit -m "chore: Release v2.x.x"`
4. Create tag: `git tag v2.x.x`
5. Push to GitHub: `git push origin main --tags`
6. Create GitHub release with notes

## ✅ Pull Request Checklist

- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Commented complex code sections
- [ ] Documentation updated
- [ ] No new warnings generated
- [ ] Tests added/updated
- [ ] Tests pass locally
- [ ] Changes work on Android and iOS
- [ ] 16 KB page size compatibility maintained

## 📞 Questions?

Feel free to open an issue for questions or discussions.

## 📄 License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to AxeVPN Flutter Plugin! 🎉
