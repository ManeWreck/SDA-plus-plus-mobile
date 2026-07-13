# SDA++ Mobile

Android companion app for SDA++.

The mobile app already supports secure local vault storage, Steam Guard usage, cloud backup flow, and a modern Steam QR approval experience.

## Current Scope

- Encrypted local vault for imported account data
- Import of classic SDA `.maFile` payloads
- On-device Steam Guard code generation
- PIN / biometric app lock
- WebDAV sync for encrypted backup pull / push
- Public profile enrichment for:
  - avatar
  - persona name
  - Steam level
- Account list with favorites, sorting, search, and localization
- Steam-style QR camera UI
- Modern Steam QR approval flow for accounts imported with a valid `Session` snapshot
- One-scan local pairing that sends saved WebDAV settings to a fresh SDA++ Desktop install

## One-Scan Desktop Pairing

On the first-run screen in SDA++ Desktop, choose **Connect cloud from SDA++ Mobile with one scan**. In the unlocked mobile app, open the QR scanner, scan the desktop code, and explicitly confirm sharing the saved WebDAV connection.

- Both devices must be on the same private network.
- The QR expires after two minutes and contains only a one-time session identifier, desktop public key, and local connection addresses.
- WebDAV settings are encrypted end-to-end with ephemeral P-256 ECDH and AES-256-GCM.
- Steam secrets, `.maFile` payloads, account data, and the local vault key are never placed in the QR or pairing message.
- Desktop stores the received WebDAV password with Windows DPAPI and offers the existing validated, backup-first cloud restore.
- Manual WebDAV setup remains available as a fallback.

## Important Limitation

Modern Steam QR approval on Android requires the imported `.maFile` to include a usable Steam mobile web `Session`.

If an account was imported from a stripped or outdated file without a valid `Session`, the app can still:

- generate Steam Guard codes
- store the account securely
- sync encrypted vault data

But QR approval and web-session actions may fail until the account is re-imported from a fresher desktop SDA++ backup.

## Cloud Sync

Implemented / present in UI:

- WebDAV
- provider architecture for other cloud backends

WebDAV currently covers the main backup workflow for:

- encrypted `manifest`
- encrypted `.maFile` artifacts

## Build

Requirements:

- Android Studio
- Android SDK
- JDK 17+

```powershell
cd .\SDAplusplus-Mobile
.\gradlew assembleDebug
```

Debug APK output:

- `app/build/outputs/apk/debug/app-debug.apk`

## Security Notes

- Do not commit `maFiles`, manifests, credentials, or local vault content
- Treat imported `.maFile` data as highly sensitive
- Prefer encrypted local storage and trusted devices only
- Use PIN / biometrics if the device is shared or can be lost

## Roadmap

- broader cloud provider parity with desktop
- cleaner Android settings / performance passes
- more complete Steam session refresh / recovery handling
- better mobile-specific UX polish
