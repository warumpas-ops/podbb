# PodBB 🎙️

**A lightweight, standalone podcast player engineered specifically for BlackBerry 10 devices (BlackBerry Q5, Q10, Classic, and Passport).**

[![Platform](https://img.shields.io/badge/Platform-BlackBerry%2010%20(Android%20Runtime)-000000.svg?logo=blackberry)](https://github.com/warumpas-ops/podbb)
[![Android](https://img.shields.io/badge/Android%20Runtime-4.3%20(Jelly%20Bean)-3DDC84.svg?logo=android)](https://github.com/warumpas-ops/podbb)
[![Screen](https://img.shields.io/badge/Screen-720x720%20%26%201:1%20Optimized-blue.svg)](https://github.com/warumpas-ops/podbb)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## Why PodBB?

With the official shutdown of BlackBerry World and the expiration of legacy BlackBerry 10 root CA certificates, finding a working, modern podcast app for BB10 devices has become almost impossible. Modern Android podcast clients require Android 8.0+ and Google Play Services, while older podcast apps fail to connect to modern feeds due to TLS 1.2/1.3 and SNI requirements.

**PodBB** solves this by providing a clean, bloat-free podcast client compiled specifically for the BlackBerry 10 Android Runtime (Android 4.3 Jelly Bean / API 18), packed with workarounds for modern web standards.

---

## Features

* **Zero Google Play Services Dependency:** Completely standalone APK that runs natively within the BB10 Android subsystem.
* **Modern SSL/TLS Engine:**
  * Injects modern **TLSv1.2** and cipher suites into Android 4.3.
  * **SNI (Server Name Indication)** enabled via Java reflection to communicate with modern Cloudflare/CDN-hosted podcast feeds.
  * Permissive CA fallback to handle BlackBerry 10's expired root certificates.
  * Automatic **GZIP decompression** and HTTPS-to-HTTP fallback.
* **1:1 Square Screen UI (720×720 & 1440×1440):**
  * Specially designed layouts that fit the BlackBerry Q5, Q10, and Classic (720×720) and BlackBerry Passport (1440×1440).
  * **Collapsible Hero Banner:** Smooth scroll-minimizing podcast header that maximizes screen space for episode lists.
* **Apple Podcasts Directory Search:** Instant search powered by the Apple Podcasts / iTunes directory, plus direct RSS / Atom feed support.
* **Offline Episode Downloads:**
  * Background episode downloading with resume capability.
  * Configurable storage location (Internal Storage or **MicroSD Card**) — essential for devices with limited storage like the Q5.
* **Background Audio Player:** Persistent playback service with mini-player controls and seamless background audio while multitasking on BB10.
* **Lightweight & Battery-Friendly:** Written in pure Java without heavy modern libraries or telemetry bloat, ensuring snappy performance and cool battery temps on Snapdragon S4 devices.
* **Custom Pixel Art Icon:** Sleek cyber wireframe cassette launcher tile designed to match the BlackBerry 10 aesthetic.

---

## Screenshots & Layout

| Grid Subscriptions | Search & Discovery | Episode Player |
| :---: | :---: | :---: |
| *Visual cover grid tailored for 1:1 displays* | *Live Apple Podcasts & RSS parsing* | *Full playback controls with scrub bar* |

---

## Installation on BlackBerry 10

1. **Download the APK:**  
   Download the latest `podbb-vX.X.X.apk` from the [Releases](https://github.com/warumpas-ops/podbb/releases) section.
2. **Enable App Sideloading on your BlackBerry:**  
   * Open **Settings** on your BlackBerry device.
   * Navigate to **Security and Privacy** > **Application Permissions**.
   * Toggle **Allow Installation of Apps from Other Sources** to **ON**.
3. **Install:**  
   * Transfer the APK to your phone via USB, MicroSD, or download it directly using the BB10 browser.
   * Open the native BlackBerry **File Manager**, locate the `.apk`, and tap **Install** in the top right corner.

> **Tip for Q5 & Q10 Users:**  
> If any UI element feels snug on the square screen, swipe down from the **top bezel** while inside PodBB, tap the **Size / Ratio** icon, and adjust the view mode according to your preference.

---

## Building from Source

### Prerequisites
* JDK 17
* Android SDK (API 34 build-tools, Android API 18/34 platforms)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/warumpas-ops/podbb.git
cd podbb

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

The compiled APK will be located at:
```
app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## Technical Specifications

| Parameter | Value |
| :--- | :--- |
| **Package Name** | `com.podbb.app` |
| **Min SDK** | `14` (Android 4.0 Ice Cream Sandwich) |
| **Target SDK** | `18` (Android 4.3 Jelly Bean — Native BB10 Runtime) |
| **Compile SDK** | `34` |
| **Database** | SQLite (`DatabaseHelper`) |
| **Audio Engine** | Android `MediaPlayer` via `PlayerService` |
| **Network Client** | Custom `HttpHelper` with TLS 1.2 + SNI reflection |

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
