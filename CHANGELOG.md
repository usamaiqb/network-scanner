## [1.1.3] - 2026-06-11

### Fixed
- Device list now sorts by IP address in correct numerical order (e.g. .2 before .10)

## [1.1.2] - 2026-05-20

### Added
- Network interface selection — switch between Wi-Fi, Ethernet, and VPN interfaces to scan the right network

### Improved
- Better smartphone detection on home networks: improved heuristics handle randomized MAC addresses, TTL fingerprinting, and ghost-device fallback for phones that evade all other signals

### Fixed
- Scan progress label wording (#13)

## [1.1.1] - 2026-04-13

### Improved
- Windows and Samba devices now show their actual hostname and workgroup via NetBIOS name resolution
- Smart devices (TVs, routers, NAS, printers) now display rich info like friendly name, manufacturer, and model number from SSDP device descriptions
- Increased SSDP discovery timeout to catch devices that respond slowly
- Cast-enabled devices identified via port probing when mDNS/SSDP aren't available
- Android TV devices detected via `_androidtvremote2` mDNS service type

### Fixed
- Fixed device hostnames being overwritten by mDNS names when a better name was already known
- Fixed garbage/binary hostnames appearing for some devices due to malformed DNS or mDNS responses
- Fixed mDNS device names showing raw UUID suffixes (e.g. "DEVICE-6d5e7bc166c2" → "DEVICE")

## [1.1.0] - 2026-03-25

### Changed
- Full UI rewrite using Jetpack Compose with Material Design 3 Expressive
- Improved device list layout and visual design
- Smoother animations throughout the app

### Added
- Updated app screenshots and store metadata

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
