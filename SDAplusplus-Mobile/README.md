# SDA++ Mobile Android Project

This directory contains the Android application and its shared Kotlin modules.

## Modules

- `app` - Jetpack Compose interface, QR scanner, Steam session actions, and Android integration
- `core-model` - shared account and synchronization models
- `core-steam` - Steam Guard code generation interfaces
- `core-sync` - cloud provider abstractions
- `core-vault` - encrypted vault interfaces

## Supported Workflows

- Import and encrypt classic SDA `.maFile` payloads
- Generate Steam Guard codes locally
- Approve modern Steam QR sign-ins when a valid mobile session is available
- Pull and push encrypted vault data through WebDAV
- Review Steam confirmations across imported accounts
- Terminate Steam sessions for an authenticated account
- Transfer WebDAV settings to SDA++ Desktop through encrypted pairing

## Build

Use Android Studio with Android SDK 34 and JDK 17, or run:

```powershell
.\gradlew assembleDebug
```

The generated APK is located at `app/build/outputs/apk/debug/app-debug.apk`.

## Sensitive Data

Never commit `.maFile` files, decrypted manifests, credentials, session tokens, cloud secrets, or vault backups. Repository and release artifacts must contain application code and public assets only.
