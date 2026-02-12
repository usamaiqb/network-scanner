# Contributing to Network Scanner

Thank you for your interest in contributing to Network Scanner! We welcome contributions from everyone.

## Code of Conduct

- Be respectful and inclusive
- Focus on constructive feedback
- Help create a welcoming environment for all contributors

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates.

**When submitting a bug report, include:**
- Device model and Android version
- App version
- Steps to reproduce the issue
- Expected vs actual behavior
- Screenshots (if applicable)
- Logcat output (if possible)

### Suggesting Features

Feature suggestions are welcome! Please:
- Check if the feature has already been suggested
- Explain the use case and benefits
- Consider if it aligns with the project's privacy-first philosophy

### Pull Requests

1. **Fork the repository** and create your branch from `main`
2. **Follow the code style** (see below)
3. **Test your changes** thoroughly
4. **Update documentation** if needed
5. **Write clear commit messages**
6. **Submit a pull request** with description of changes

## Development Setup

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Git

### Setup Steps

```bash
# Clone your fork
git clone https://github.com/your-username/network-scanner.git
cd network-scanner

# Add upstream remote
git remote add upstream https://github.com/usamaiqb/network-scanner.git

# Create a branch
git checkout -b feature/your-feature-name
```

### Building

```bash
# Debug build
./gradlew assembleDebug

# Run tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## Code Style

### Kotlin Style Guide

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

- Use 4 spaces for indentation
- Use camelCase for variable and function names
- Use PascalCase for class names
- Place opening braces on the same line
- Prefer expression bodies for simple functions
- Use meaningful variable names

### Example

```kotlin
class NetworkScanner(private val context: Context) {

    fun scanNetwork(): Flow<List<Device>> = flow {
        val devices = performScan()
        emit(devices)
    }

    private suspend fun performScan(): List<Device> {
        // Implementation
        return emptyList()
    }
}
```

### Android Best Practices

- Use ViewBinding instead of findViewById
- Follow MVVM architecture pattern
- Use Kotlin Coroutines for async operations
- Handle configuration changes properly
- Request permissions at runtime appropriately

## Commit Messages

Write clear and descriptive commit messages:

```
Short (72 chars or less) summary

More detailed explanatory text, if necessary. Wrap it to about 72
characters. The blank line separating the summary from the body is
critical.

- Bullet points are okay too
- Use present tense ("Add feature" not "Added feature")
- Use imperative mood ("Move cursor to..." not "Moves cursor to...")
```

### Examples
- ✅ `Add port scanning timeout option`
- ✅ `Fix crash when WiFi is disabled`
- ✅ `Update device detection algorithm`
- ❌ `fixed stuff`
- ❌ `WIP`

## Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

Please add tests for new features when possible.

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/networkscanner/app/
│   │   │   ├── data/          # Data models
│   │   │   ├── network/       # Network scanning logic
│   │   │   ├── ui/            # Activities and fragments
│   │   │   └── util/          # Utility classes
│   │   └── res/               # Resources
│   └── test/                  # Unit tests
└── build.gradle.kts
```

## Privacy Guidelines

This is a privacy-focused app. **Never add:**
- Analytics or tracking libraries
- Ad frameworks
- Data collection of any kind
- Network requests outside local network
- Proprietary dependencies

## License

By contributing, you agree that your contributions will be licensed under the GNU General Public License v3.0.

## Questions?

Feel free to open an issue for questions or clarifications!

---

Thank you for contributing! 🎉
