# SDA++ by Manewreck

SDA++ is a Steam Desktop Authenticator fork focused on faster QR approvals, cleaner session tools, safer backup workflows, and quality-of-life improvements for multi-account Steam usage.

This repository currently contains:

- `Steam Desktop Authenticator/` - the main Windows desktop fork
- `SDAplusplus-Mobile/` - the Android companion app

SDA++ is built for personal account management convenience. It is not affiliated with Valve or Steam.

## What SDA++ Adds

### Desktop

- Steam QR scan and approval flow directly from desktop
- Global QR hotkeys:
  - `Ctrl + Shift + S` - scan the screen for a Steam QR login
  - `Ctrl + Shift + Q` - enable or disable QR hotkey mode / overlay
- Improved session visibility:
  - session live
  - access expired
  - refresh expired
- Better `Terminate all sessions` behavior with SDA++ auto-restore logic
- Account filtering, favorites, and quick multi-account workflows
- Cloud sync foundation for encrypted backup workflows
- Updated SDA++ branding, dark UI refresh, and custom icon

### Mobile

- Local encrypted vault for imported `.maFile` data
- Steam Guard code generation on-device
- WebDAV pull/push backup flow
- Vault lock with PIN / biometrics
- Public profile enrichment for avatar / level display
- Steam-style QR camera screen
- Modern Steam QR approval flow for imported accounts that include a valid `Session` snapshot

More Android details live in [SDAplusplus-Mobile/README.md](./SDAplusplus-Mobile/README.md).

## QR Login Workflow

SDA++ desktop was built to speed up approving your own Steam login requests from another PC.

Typical flow:

1. Open the Steam login page with a QR code.
2. In SDA++, select the account that should approve the login.
3. Press `Ctrl + Shift + S` or use the QR scan action.
4. SDA++ captures the screen, finds the Steam QR, decodes it, and confirms it for the selected account.

On Android, the QR screen is now wired to the modern Steam QR format and can approve valid `https://s.team/q/...` payloads when the imported account includes a usable Steam mobile web session snapshot.

## Session Tools

SDA++ adds practical recovery and session handling improvements:

- detect expired web sessions before a workflow fails
- restore the SDA++ session with saved encrypted credentials
- terminate Steam sessions for the selected account
- automatically recover the SDA++ session after session termination when saved credentials are available
- show clearer session health and token state

## Cloud Sync

Current backup-oriented providers:

- WebDAV
- S3-compatible storage
- Dropbox
- OneDrive Personal
- Google Drive

The goal is backup convenience, not blind trust. Sensitive data should always remain encrypted locally and in cloud storage.

## Repository Safety

Before every push, make sure these are never included:

- `maFiles`
- `manifest.json`
- encrypted or plain credentials files
- token snapshots
- backup archives
- local publish folders

The current `.gitignore` already covers the most sensitive local artifacts.

## Build

### Desktop

Requirements:

- [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0)
- Windows

```powershell
git clone --recurse-submodules https://github.com/Manewreck/SDAplusplus.git
cd SDAplusplus
dotnet build .\SteamDesktopAuthenticator.sln -c Release
```

If you already cloned without submodules:

```powershell
git submodule update --init --recursive
```

### Android

Requirements:

- Android Studio
- Android SDK / platform tools
- JDK 17+

Open:

- `SDAplusplus-Mobile/`

Then build:

```powershell
cd .\SDAplusplus-Mobile
.\gradlew assembleDebug
```

## Command Line Options

```text
-k [encryption key]
  Set your encryption key when opened

-s
  Auto-minimize to tray when opened
```

## Support

- GitHub: [Manewreck](https://github.com/Manewreck)
- Ko-fi: [ko-fi.com/manewreck](https://ko-fi.com/manewreck)

## Disclaimer

You are responsible for:

- your own backups
- local system security
- encrypted storage of sensitive files
- safe use on trusted devices only

If you lose your encryption key and your backups, recovery may be impossible.
