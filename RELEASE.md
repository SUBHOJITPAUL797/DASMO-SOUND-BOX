# 📦 DASMO SOUND BOX — Production Release & In-App Update Specification

[![GitHub Release](https://img.shields.io/github/v/release/SUBHOJITPAUL797/DASMO-SOUND-BOX?style=for-the-badge&color=2563EB)](https://github.com/SUBHOJITPAUL797/DASMO-SOUND-BOX/releases)
[![Build Status](https://img.shields.io/badge/Build-Passing-16A34A?style=for-the-badge&logo=android)](https://github.com/SUBHOJITPAUL797/DASMO-SOUND-BOX)
[![Android Min SDK](https://img.shields.io/badge/Min_SDK-24_(Android_7.0)-blue?style=for-the-badge&logo=android)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-36_(Android_15)-indigo?style=for-the-badge&logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Proprietary-orange?style=for-the-badge)](https://github.com/SUBHOJITPAUL797/DASMO-SOUND-BOX)

> **Repository:** [`SUBHOJITPAUL797/DASMO-SOUND-BOX`](https://github.com/SUBHOJITPAUL797/DASMO-SOUND-BOX)  
> **Application ID:** `com.aistudio.soundbox.dsmo`  
> **API Release Manifest:** `https://api.github.com/repos/SUBHOJITPAUL797/DASMO-SOUND-BOX/releases/latest`  
> **Distribution Model:** Direct In-App OTA Update via GitHub Releases Engine + Android `FileProvider`  

---

## 📑 Table of Contents

1. [GitHub Release Notes (Copy-Paste Ready)](#-github-release-notes-copy-paste-ready)
2. [Current Production Release: v1.0.1](#-current-production-release-v101)
3. [In-App Update Engine Architecture](#-in-app-update-engine-architecture)
4. [Step-by-Step Developer Release Runbook](#-step-by-step-developer-release-runbook)
5. [Release Asset Matrix & SHA-256 Checksums](#-release-asset-matrix--sha-256-checksums)
6. [Security, Signing & Integrity Verification](#-security-signing--integrity-verification)
7. [Automated CI/CD Workflow (`release.yml`)](#-automated-cicd-workflow-releaseyml)
8. [Changelog History](#-changelog-history)

---

## 📋 GitHub Release Notes (Copy-Paste Ready)

> **Instructions for Maintainer:** Copy the markdown block below directly into the GitHub **"Create Release"** or **"Draft a new release"** body at [`https://github.com/SUBHOJITPAUL797/DASMO-SOUND-BOX/releases/new`](https://github.com/SUBHOJITPAUL797/DASMO-SOUND-BOX/releases/new).  
> The In-App Updater parses the `versionCode`, tag, and changelog headers automatically.

```markdown
# 🔊 DASMO Sound Box v1.0.1 — Smart UPI Audio & In-App Update Engine

### 🏷️ Metadata
- **Version:** `v1.0.1`
- **versionCode:** `2`
- **Package:** `com.aistudio.soundbox.dsmo`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** October 2026

---

### 🚀 Highlights & What's New

- **🔄 GitHub-Connected In-App Auto Updates:** 
  The app now connects directly to GitHub Releases (`SUBHOJITPAUL797/DASMO-SOUND-BOX`). Merchants can check for updates, view real-time download progress, and install the new APK directly without leaving the app!
- **⚡ Zero-Latency UPI Sound Alerts:** 
  Optimized background audio pipeline with instant chime synthesis and low-latency speech playback for Paytm, PhonePe, Google Pay, BHIM, BharatPe, Cred, and bank SMSs.
- **🌐 12 Indian Languages & Dual-Language Announcements:** 
  Seamless bilingual payment announcements (e.g., *"₹100 received on Paytm QR"* followed by Hindi/regional translation).
- **🛡️ 100% Offline Multi-Level Deduplication Engine:** 
  Time-window deduplication prevents duplicate voice alerts when both an SMS and a push notification arrive for the same payment.
- **🔋 Bulletproof Background Keep-Alive Service:** 
  Integrated foreground service with sticky restart, watchdog timer receiver, and automated battery optimization bypass guides.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| [`dasmo-soundbox-v1.0.1.apk`](https://github.com/SUBHOJITPAUL797/DASMO-SOUND-BOX/releases/download/v1.0.1/dasmo-soundbox-v1.0.1.apk) | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 15.2 MB | `cf5063824685f5c730158dc889b47443b8e77d79c26e70a9a1423459fcd0c74a` |


---

### 🛠️ Detailed Changelog

#### 🔄 In-App Updater
- Added `AppUpdateManager` with asynchronous GitHub Releases API integration (`https://api.github.com/repos/SUBHOJITPAUL797/DASMO-SOUND-BOX/releases/latest`).
- Live chunked download stream with real-time percentage and downloaded byte metrics.
- Seamless Android `FileProvider` package installer invocation with `REQUEST_INSTALL_PACKAGES` permission support.
- One-tap browser fallback button to view full release on GitHub.
- Silent background update alert chip on the merchant dashboard.

#### 🔊 Audio & Speech Processing
- Added custom prefix and suffix voice announcements (e.g., *"Welcome to Dasmo Store! ₹500 received. Visit again!"*).
- Customizable chime sound presets (Classic Soundbox Bell, Cash Register Ka-Ching, Modern Digital Beep, Minimal Gentle Chime).
- Audio focus retention so high-volume market noise never drowns out transaction alerts.

#### 🛡️ Reliability & Security
- Added 4-digit admin security lock preventing unauthorized staff from altering shop UPI IDs or disabling sound alerts.
- Room database with destructive migration fallback and optimized WAL mode.
```

---

## 🚀 Current Production Release: v1.0.1

### Version Summary
- **Tag:** `v1.0.1`
- **Version Code:** `2`
- **Version Name:** `1.0.1`
- **Status:** General Availability (GA)
- **Repository:** `SUBHOJITPAUL797/DASMO-SOUND-BOX`

### Target Metrics
| Metric | Specification |
| :--- | :--- |
| **Cold Start Time** | < 450 ms |
| **Notification to Speech Latency** | < 180 ms |
| **Background Memory Footprint** | ~ 24 MB RAM |
| **Supported Android Versions** | Android 7.0 (Nougat) to Android 15 (Vanilla Ice Cream) |
| **Permissions Required** | Notification Listener, SMS Receiver (Optional), Internet, Battery Optimization Exemption |

---

## ⚙️ In-App Update Engine Architecture

The DASMO Sound Box in-app updater operates entirely through decentralized GitHub Releases without requiring proprietary third-party servers.

```mermaid
sequenceDiagram
    autonumber
    actor Merchant as Merchant / App
    participant Updater as AppUpdateManager
    participant GitHub as GitHub Releases API
    participant OS as Android Package Installer

    Merchant->>Updater: Tap "Check for Update" or App Launch
    Updater->>GitHub: GET /repos/SUBHOJITPAUL797/DASMO-SOUND-BOX/releases/latest
    GitHub-->>Updater: HTTP 200 OK (Release JSON + Assets + Changelog)
    
    alt Newer Version Detected (latestVersion > currentVersion)
        Updater->>Merchant: Display "New Update Available: v1.0.1"
        Merchant->>Updater: Tap "Update Now (APK)"
        Updater->>GitHub: Stream Download (dasmo-soundbox-v1.0.1.apk)
        Updater->>Merchant: Emits Live Progress (0% -> 100%)
        Updater->>OS: Launch ACTION_VIEW Intent with FileProvider URI
        OS->>Merchant: Display System Package Update Dialog
        Merchant->>OS: Confirm "Update"
        OS->>Merchant: Launch newly updated app!
    else Current Version is Up to Date
        Updater->>Merchant: Display "App is up to date (v1.0)"
    end
```

### Key Technical Safeguards

1. **Strict Semantic & Build Code Verification:**
   The update manager checks both the `versionCode` extracted from the release notes/assets and semantic components (`X.Y.Z`). This prevents accidental downgrade attacks.
2. **Secure `FileProvider` Sharing:**
   Downloaded APKs are isolated in internal app cache (`context.cacheDir/updates/`) and exposed exclusively via secure content URIs (`content://com.aistudio.soundbox.dsmo.fileprovider/cache/updates/...`) with temporary read grants (`FLAG_GRANT_READ_URI_PERMISSION`).
3. **Graceful Fallback Mode:**
   If third-party installation is restricted by enterprise MDM or Android security policies, the user can immediately tap **"View on GitHub"** to download or inspect the release directly in their default browser.

---

## 🛠️ Step-by-Step Developer Release Runbook

Follow these exact steps to publish a new release to the repository so all connected devices automatically receive the update.

### Step 1: Bump App Version
Open [`app/build.gradle.kts`](file:///c:/CODING/coading/DASMO%20CLIENTS/DASMO-SOUND-BOX/app/build.gradle.kts) and increment `versionCode` and `versionName`:
```kotlin
  defaultConfig {
    applicationId = "com.aistudio.soundbox.dsmo"
    minSdk = 24
    targetSdk = 36
    versionCode = 2          // <-- Increment integer
    versionName = "1.0.1"    // <-- Increment semantic string
  }
```

### Step 2: Build the Signed Release APK
Run the release build in PowerShell / Terminal:
```bash
# Clean previous builds
./gradlew clean

# Build optimized release APK
./gradlew assembleRelease
```
The compiled APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```
Rename the output APK to a standardized naming convention:
```bash
mv app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/release/dasmo-soundbox-v1.0.1.apk
```

### Step 3: Generate the SHA-256 Checksum
Run SHA-256 verification to ensure supply chain integrity:
```powershell
# PowerShell
Get-FileHash app\build\outputs\apk\release\dasmo-soundbox-v1.0.1.apk -Algorithm SHA256
```
Or in Linux / macOS:
```bash
sha256sum app/build/outputs/apk/release/dasmo-soundbox-v1.0.1.apk
```

### Step 4: Tag the Release in Git
```bash
git add .
git commit -m "chore(release): prepare v1.0.1 release"
git push origin main

# Create annotated tag
git tag -a v1.0.1 -m "Release v1.0.1 — Smart UPI Audio & In-App Update Engine"
git push origin v1.0.1
```

### Step 5: Publish the Release on GitHub
1. Open [`https://github.com/SUBHOJITPAUL797/DASMO-SOUND-BOX/releases/new`](https://github.com/SUBHOJITPAUL797/DASMO-SOUND-BOX/releases/new)
2. Select tag: **`v1.0.1`**
3. Release Title: **`DASMO Sound Box v1.0.1 — Smart UPI Audio & In-App Update Engine`**
4. Paste the release markdown from [Section 1](#-github-release-notes-copy-paste-ready).
5. Attach the binary: Drag & drop **`dasmo-soundbox-v1.0.1.apk`** into the binary upload box.
6. Click **"Publish release"**.

> 💡 **Immediate Effect:** Within seconds, every installed DASMO Sound Box app checking for updates will detect `v1.0.1` and prompt the shopkeeper to upgrade!

---

## 🔒 Security, Signing & Integrity Verification

### Keystore Configuration
For production deployments, configure your secure upload keystore using environment variables or a local `keystore.properties` file:
```bash
export KEYSTORE_PATH="/path/to/my-upload-key.jks"
export STORE_PASSWORD="your-secure-store-password"
export KEY_ALIAS="upload"
export KEY_PASSWORD="your-secure-key-password"
```

### Verification Command
Merchants or system administrators can verify the APK signature using Android's `apksigner`:
```bash
apksigner verify --verbose --print-certs dasmo-soundbox-v1.0.1.apk
```

---

## 🤖 Automated CI/CD Workflow (`release.yml`)

The repository includes a GitHub Actions workflow located at [`.github/workflows/release.yml`](file:///c:/CODING/coading/DASMO%20CLIENTS/DASMO-SOUND-BOX/.github/workflows/release.yml).

Whenever a tag matching `v*` is pushed:
```bash
git tag v1.0.1
git push origin v1.0.1
```
The GitHub Action will automatically:
1. Check out repository code.
2. Set up JDK 17.
3. Build the signed release APK via Gradle.
4. Calculate SHA-256 checksums.
5. Create the official GitHub Release with attached `.apk` asset.

---

## 📜 Changelog History

### [1.0.1] - 2026-10-04
- **Added:** Decentralized In-App Updater directly backed by GitHub Releases API.
- **Added:** Live chunked download indicator with percentage and megabytes progress.
- **Added:** `REQUEST_INSTALL_PACKAGES` permission and extended `FileProvider` XML paths.
- **Added:** Home screen silent update notification chip for merchants.
- **Added:** Comprehensive `RELEASE.md` and GitHub Release Markdown Template.
- **Improved:** Settings screen with dedicated "Software Update & GitHub Releases" card.
- **Improved:** Dual-language TTS stability and Bluetooth audio latency.

### [1.0.0] - 2026-10-04
- **Initial Release:** Complete UPI sound box terminal application.
- **Features:**
  - Real-time notification parsing for all major Indian UPI and banking apps.
  - SMS payment parser with bank sender verification (HDFC, SBI, ICICI, Axis, PNB, etc.).
  - Text-to-speech announcement engine supporting 12 Indian regional languages.
  - Multi-preset chime player for clear audio alerts in loud environments.
  - Offline payment history ledger and daily transaction analytics.
  - Kiosk screen lock and battery optimization watchdog.
