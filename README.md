# Network Scanner

[![CI](https://github.com/usamaiqb/network-scanner/actions/workflows/ci.yml/badge.svg)](https://github.com/usamaiqb/network-scanner/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/usamaiqb/network-scanner)](https://github.com/usamaiqb/network-scanner/releases/latest)
[![F-Droid](https://img.shields.io/f-droid/v/com.networkscanner.app)](https://f-droid.org/packages/com.networkscanner.app/)
[![Downloads](https://img.shields.io/github/downloads/usamaiqb/network-scanner/total)](https://github.com/usamaiqb/network-scanner/releases)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen)](https://developer.android.com/about/versions/oreo)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

A fast, privacy-focused network scanner for Android that helps you discover and analyze devices on your local network.

## Features

### Device Discovery
- 🔍 **Ping Sweep** - Parallel ICMP ping sweep with TCP fallback for devices that block ICMP (e.g. Windows with firewall)
- 📋 **ARP Cache** - Reads the ARP table to pick up devices without sending any traffic
- 📡 **mDNS / Bonjour** - Discovers services like AirPlay, Chromecast, printers, SSH, SMB, HomeKit, and more
- 📺 **SSDP / UPnP** - Finds UPnP devices and fetches their full description (friendly name, manufacturer, model number)
- 🪟 **NetBIOS** - Resolves hostnames and workgroups for Windows and Samba devices
- 🔌 **Port Heuristics** - Identifies Cast-enabled TVs and other devices via targeted port probes when other methods come up empty

### Device Information
- 🏷️ **MAC Address & Vendor** - Shows MAC address with OUI vendor lookup, including detection of randomized (private) MAC addresses
- 🖥️ **OS Fingerprinting** - Detects Windows, Linux, macOS, router firmware, and printer OS from open ports and banners
- 📱 **Device Type Icons** - Automatically identifies smartphones, laptops, desktops, TVs, routers, printers, NAS, and more
- 🔓 **Deep Port Scan** - Scans common ports, grabs service banners, and extracts software versions
- 🧩 **Full Port Scan** - Optional sweep of all 65,535 ports via a fast worker pool
- ⚙️ **Configurable Ports** - Customize which ports are probed during scans

### App
- 🏷️ **Custom Devices** - Rename devices and assign your own icons for easy identification
- 📶 **Interface Selection** - Choose which network interface to scan (Wi-Fi, Ethernet, VPN)
- 🌍 **Multilingual** - Available in English and Russian
- 🎨 **Material Design 3** - Modern interface following latest design guidelines
- 🔒 **Privacy First** - No ads, no tracking, no analytics
- 🚀 **Lightweight** - Minimal permissions, efficient battery usage
- 📡 **Offline** - Works completely offline, no internet required

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="200" alt="Main Screen" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="200" alt="Device List" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="200" alt="Device Details" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="200" alt="Settings Screen" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="200" alt="Main Screen Dark" />
</p>

## Download

### F-Droid
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.networkscanner.app/)

### GitHub Releases
Download the latest APK from the [Releases](https://github.com/usamaiqb/network-scanner/releases) page.

## Requirements

- Android 8.0 (Oreo) or higher
- WiFi connection to scan local network

## Permissions

Network Scanner requests only essential permissions:

- **INTERNET** - For network communication
- **ACCESS_NETWORK_STATE** - To check network connectivity
- **ACCESS_WIFI_STATE** - To get WiFi information
- **CHANGE_WIFI_MULTICAST_STATE** - For network device discovery
- **NEARBY_WIFI_DEVICES** (Android 13+) - To discover nearby WiFi devices
- **ACCESS_FINE_LOCATION** / **ACCESS_COARSE_LOCATION** - Required by Android for WiFi scanning (not used for location tracking)

## Building from Source

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API level 35

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/usamaiqb/network-scanner.git
cd network-scanner
```

2. Open in Android Studio or build from command line:
```bash
./gradlew assembleRelease
```

3. The APK will be in `app/build/outputs/apk/release/`

## Usage

1. Open the app and grant necessary permissions
2. Tap the scan button to discover devices
3. Tap any device to view detailed information
4. Use the deep scan option for port scanning, or run a full port scan for all 65,535 ports
5. Rename devices or assign custom icons to keep track of your network

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## Privacy

Network Scanner:
- ✅ Does NOT collect any personal data
- ✅ Does NOT require internet connection
- ✅ Does NOT contain ads or tracking
- ✅ Does NOT share data with third parties
- ✅ All scanning happens locally on your device

For full details, see the [Privacy Policy](PRIVACY_POLICY.md).

## Support

- **Issues**: [GitHub Issues](https://github.com/usamaiqb/network-scanner/issues)
- **Discussions**: [GitHub Discussions](https://github.com/usamaiqb/network-scanner/discussions)

## Acknowledgments

Built with:
- [Kotlin](https://kotlinlang.org/) - Modern programming language for Android
- [AndroidX](https://developer.android.com/jetpack/androidx) - Android Jetpack libraries
- [Material Design 3](https://m3.material.io/) - Modern design system
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) - Asynchronous programming

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history and changes.

---

Made with ❤️ for the open source community
