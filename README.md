# SDA++ Mobile

Android Steam Guard companion focused on encrypted account storage, WebDAV backup sync, and Steam QR approval.

SDA++ Mobile is built for personal account management convenience. It is not affiliated with Valve or Steam.

## Features

- Encrypted local vault for imported `.maFile` account data
- Steam Guard code generation on-device
- Steam-style QR scanner flow for modern Steam sign-in
- WebDAV pull/push backup sync
- PIN / biometrics app lock
- Public profile enrichment for avatar, persona name, and Steam level
- Favorites, account sorting, search, and Russian / English localization

## Screenshots

| Accounts | QR Scanner |
| --- | --- |
| ![Accounts](./assets/screenshots/accounts.png) | ![QR Scanner](./assets/screenshots/qr-scanner.png) |

| Cloud Sync | Settings |
| --- | --- |
| ![Cloud Sync](./assets/screenshots/cloud-sync.png) | ![Settings](./assets/screenshots/settings.png) |

| Account Detail | QR Account Picker |
| --- | --- |
| ![Account Detail](./assets/screenshots/account-detail.png) | ![QR Account Picker](./assets/screenshots/qr-account-picker.png) |

## How It Works

1. Import your Steam `.maFile` into the encrypted local vault.
2. Protect access with a PIN or biometrics.
3. Use WebDAV to back up encrypted vault data.
4. Open the QR screen and approve your Steam sign-in with the selected account.

Modern Steam QR approval on Android requires the imported account to include a usable Steam mobile web `Session` snapshot.

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

- `SDAplusplus-Mobile/app/build/outputs/apk/debug/app-debug.apk`

## Project Layout

- `SDAplusplus-Mobile/` - Android app source
- `assets/screenshots/` - repository screenshots for GitHub

More Android implementation details live in [SDAplusplus-Mobile/README.md](./SDAplusplus-Mobile/README.md).

## Safety

Before every push, never include:

- `maFiles`
- `manifest.json`
- credentials files
- token snapshots
- backup archives

## Support

- GitHub: [Manewreck](https://github.com/Manewreck)
- Ko-fi: [ko-fi.com/manewreck](https://ko-fi.com/manewreck)
