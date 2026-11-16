# 🚀 DASMO Sound Box v1.0.10 — App Lock Refactoring, Kiosk Battery Protection & Announcement Stability

### 🏷️ Release Metadata
- **Version:** `v1.0.10`
- **versionCode:** `11`
- **Package:** `com.aistudio.soundbox.dsmo`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** November 2026

---

### 🌟 Highlights & What's New

- **🔐 App Lock Architecture Refactoring (`AppLockMethod`):**
  - **Unified Enum & State Management:** Centralized multi-method authentication across PIN, Pattern, and Password via the `AppLockMethod` enum.
  - **Enhanced Verification & Fallback:** Seamless transitions between active credentials on the dynamic lock screen with robust fail-open security against accidental lockout.
  - **Front Camera Intruder Selfie Hardening:** Improved lifecycle synchronization for intruder photo capture during failed unlock attempts.

- **🔋 Kiosk Battery Protection & State Synchronization:**
  - **Battery Optimization Notice:** Added persistent `hasSeenKioskBatteryNotice` setting in DataStore to guide merchants on battery optimization without repeated dialog prompts.
  - **Thread-Safe Settings Synchronization:** Hardened `SettingsRepository` with mutex-guarded cache updates to prevent race conditions during rapid settings toggles.
  - **Kiosk UI Dark Mode Polish:** Enhanced merchant-facing full-screen payment display with improved high-contrast visuals.

- **📊 Granular Transaction Status Tracking:**
  - Added `BELOW_THRESHOLD` to `AnnouncementStatus` for clear audit visibility when a payment is logged but filtered out below minimum announce amount settings.
  - Streamlined `ShopQrDialog` state machine with a sealed interface for crisp QR generation and error handling.

- **🔊 Payment Announcement & Speech Deduplication:**
  - **Zero Double-Speech:** Cross-channel deduplication suppresses redundant Bank SMS alerts following incoming UPI app notifications.
  - **Latest Message Extraction:** Strictly evaluates the latest incoming message in chat-style notifications (Google Pay, WhatsApp), eliminating replay of previous payment history.
  - **OS Notification Update Throttling:** 45-second sliding LRU signature cache prevents rapid-fire duplicate callback execution.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| `dasmo-soundbox-v1.0.10.apk` | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 25.17 MB | `39CDB64F4E5252AA6BAD3875AE345B620F1A1499626E86340081B69FD45E9B1F` |
