# 🔊 DASMO Sound Box v1.0.9 — Duplicate Speech & Previous Message Replay Fix

### 🏷️ Release Metadata
- **Version:** `v1.0.9`
- **versionCode:** `10`
- **Package:** `com.aistudio.soundbox.dsmo`
- **Channel:** Production (Stable)
- **Minimum OS:** Android 7.0 (API Level 24)
- **Target OS:** Android 15 (API Level 36)
- **Release Date:** November 2026

---

### 🚀 Highlights & What's Fixed

- **🔇 Elimination of "Twice" Double-Announcement Bug:**
  - **Cross-Channel Transaction Deduplication:** Fixed an issue where receiving both a UPI App Notification and a Bank Confirmation SMS for the same transaction caused the speech to trigger twice. The deduplication engine now matches transactions across channels by amount and sliding-window timestamp without requiring identical field symmetry.
  - **Sliding Window Boundary Alignment:** Replaced fixed bucket boundary hashing with dynamic sliding window expiration (`expiresAt > now`), preventing transactions near window borders from being announced twice.
  - **Notification OS Callback Throttling:** Added an in-memory LRU signature cache in `PaymentNotificationListener` to filter out rapid-fire OS update events on active notification bars.
  - **Foreground Service Intent Queue Idempotency:** Guarded `SoundBoxForegroundService` intent extras consumption to prevent intent redelivery replays.

- **⏮️ Fixed Previous Message Re-Announcement in Chat-Style UPI Apps:**
  - **Latest Message Extraction:** Completely ceased parsing `android.messages.historic` and multi-message history queues in `MessagingStyle` notifications (Google Pay, WhatsApp, etc.). The listener now strictly parses the **latest incoming message**.
  - **Suppression of Chat Replies:** Conversational messages in payment threads (such as "Thanks", "Ok", "Done") no longer scan backwards to re-announce the old payment amount.
  - **Regex Boundary Anchoring:** Updated `NAME_FIRST_PATTERN` regex in `PaymentParser` to ensure payer names are cleanly detected without prefix ambiguity.

- **✅ Multi-Customer Safety Maintained:**
  - Distinct payments of the same amount from different customers (or with distinct UTR / transaction IDs) within the same time window are never suppressed and continue to announce immediately.

---

### 📦 Artifacts & Downloads

| File | Type | Target Architecture | Size | Checksum (SHA-256) |
| :--- | :--- | :--- | :--- | :--- |
| `dasmo-soundbox-v1.0.9.apk` | Production APK | Universal (arm64-v8a, armeabi-v7a, x86_64) | 25.1 MB | `AA6BCA97BB4595C50F1946188E95FD275FB8BA9A1DB48417E1E1224A295EB518` |
