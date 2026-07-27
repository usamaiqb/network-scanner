# Privacy Policy

**Network Scanner**
**Last updated: July 27, 2026**

## Overview

Network Scanner ("the App") is a local network discovery tool that scans your Wi-Fi network to identify connected devices. This privacy policy explains what data the App accesses, how it is used, and your choices regarding that data.

## Data Collection and Usage

### Data that stays on your device

All network scanning data is processed and stored **locally on your device**. The App does not transmit any collected data to external servers. Specifically, the App collects the following data solely for local use:

- **Device network information** — IP addresses, MAC addresses, hostnames, and device names of devices found on your local network.
- **Wi-Fi network details** — Your network's SSID, subnet, and gateway address, used to perform the scan.

### Which permissions you can decline

**On Android 15 and below, the App is fully functional without granting any permissions.** Every permission is optional there and only enhances the scanning experience; if you decline them, the App will still scan your network — some details like your network name (SSID) may simply be unavailable.

**On Android 16 and above, the Nearby Wi-Fi Devices permission is required in order to scan.** Android 16 restricts all local network communication — the pings, mDNS and SSDP queries, and port probes the App uses to find devices — behind this permission. Without it the App can still show your own network details, but it cannot discover any devices. This is an Android platform requirement, not a choice the App makes, and the permission is used only to reach devices on your own network.

Location remains optional on every Android version.

### Permissions and why they are requested

| Permission | Why it is requested |
|---|---|
| **Internet** | To send network probes (ping, mDNS, port scans) to devices on your local network. |
| **Access Network State** | To determine whether you are connected to a network. |
| **Access Wi-Fi State** | To read Wi-Fi network details (SSID, gateway, subnet) to enhance scan results. |
| **Change Wi-Fi Multicast State** | To enable mDNS (multicast DNS) discovery on the local network. |
| **Nearby Wi-Fi Devices** (Android 13+) | On Android 16 and above, to communicate with devices on your local network — this is what makes scanning possible. On Android 13 to 15, to read Wi-Fi network information without requiring location permission. |
| **Access Fine/Coarse Location** | Optional. Android requires location permission to read Wi-Fi network details such as the SSID; on Android 13 and above, Nearby Wi-Fi Devices can serve that purpose instead. The App does not use your GPS location for any purpose, and scanning works without this permission. |

You can deny or revoke any of these permissions at any time through your device's Settings. On Android 15 and below this does not affect the App's core functionality. On Android 16 and above, declining or revoking Nearby Wi-Fi Devices stops device discovery; the App will ask again the next time you scan, and you can restore the permission from Settings at any point.

### Data the App does NOT collect

- The App does **not** collect personal information (name, email, phone number, etc.).
- The App does **not** use analytics, tracking, or advertising SDKs.
- The App does **not** transmit any data off your device.
- The App does **not** store or access your GPS location.

## Third-Party Services

The App does not use any third-party services that collect data.

## Data Retention

All data is stored locally on your device and can be cleared at any time by clearing the App's data or uninstalling it.

## Children's Privacy

The App does not knowingly collect any data from children under the age of 13. The App does not collect personal information from any user.

## Changes to This Policy

We may update this privacy policy from time to time. Any changes will be reflected by the "Last updated" date at the top of this page.

## Contact

If you have questions or concerns about this privacy policy, please open an issue on the project's GitHub repository.
