## [1.0.2] - 2026-03-17

### Fixed
- Fixed false positive device detection caused by ICMP "Destination Unreachable" responses being mistaken for live hosts on some Android kernels
- Fixed some Windows devices not being detected on some networks
- Fixed incorrect subnet being scanned on networks larger than /24
- Fixed port scanning running sequentially in batches instead of truly in parallel

### Improved
- More accurate OS detection with weighted port scoring
- Better service banner grabbing for non-HTTP ports
- mDNS discovery now waits for pending resolves before returning

## [1.0.1] - 2026-02-17

### Fixed
- Disabled Google DependencyInfoBlock for F-Droid compliance
- Hide version code in Release Build

## [1.0.0] - 2026-02-16

### Added
- Initial release of Network Scanner
- Network device discovery and scanning
- Device details view with IP, MAC address, and hostname
- Port scanning capabilities
- Material Design 3 UI with modern interface
- Network information display
- Device type detection and categorization
- Refresh functionality
- Settings and preferences
- Support for Android 8.0 (API 26) and above

### Features
- Fast and efficient network scanning
- Deep scan option for detailed device information
- Clean, ad-free experience
- No tracking or analytics
- Fully open source
- Works offline (no internet required for scanning)
