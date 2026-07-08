# SDA++ Mobile MVP

## Goal

Create an Android companion app for SDA++ that focuses on safe account backup, Steam Guard code generation, QR login approval, and cross-device cloud sync without relying on auto-login.

The first Android release should prioritize reliability, safe storage, and a clean mobile-first experience over feature count.

## Product Direction

SDA++ Mobile is not meant to be a direct WinForms port.

It should be:

- mobile-first
- vault-centric
- backup-safe
- cloud-friendly
- visually modern
- conservative with sensitive session logic

For v1, the app should not attempt automatic Steam session restoration through saved username/password credentials.

## MVP Scope

### Included in v1

- import and read `.maFile` accounts
- encrypted local vault on Android
- Steam Guard code generation
- Steam QR scanner for login approval
- cloud sync for vault data
- account list and account details screens
- sync status and last sync information
- manual `Pull` and `Push`
- support for account restore after phone change

### Excluded from v1

- auto-login with username/password
- background cloud sync automation
- trade confirmations
- advanced conflict merge
- multi-device live editing
- iOS support

## Recommended Stack

### Platform

- Android only for first release

### Language and UI

- Kotlin
- Jetpack Compose

### Why

- best Android-native UX path
- easiest secure storage integrations
- easier camera / QR / biometric support
- better long-term maintainability than forcing desktop UI patterns onto mobile

## Core User Scenarios

### 1. Recover after phone loss or replacement

The user installs SDA++ Mobile on a new Android phone, connects cloud sync, unlocks their vault, pulls the encrypted backup, and gets their accounts back.

### 2. Use the same account set across desktop and phone

The user keeps SDA++ Desktop and SDA++ Mobile pointed at the same encrypted vault and manually syncs when needed.

### 3. Generate Steam Guard codes on the go

The user opens the app, unlocks the vault, selects an account, and sees the current Steam Guard code with time remaining.

### 4. Approve Steam login through QR flow

The user scans a Steam login QR from another device and approves it with the selected account.

## Information Architecture

### Primary Screens

#### 1. Unlock Screen

- app logo / branding
- vault password or key input
- optional biometric unlock if enabled later
- entry to create or restore vault

#### 2. Home / Accounts Screen

- list of imported accounts
- pinned / favorite accounts at top
- search field
- sessionless design focused on code availability
- sync status chip

#### 3. Account Details Screen

- account name
- Steam Guard code
- code timer bar
- QR approval button
- account metadata
- cloud presence / backup state

#### 4. QR Scanner Screen

- camera QR scanning
- account selector if none preselected
- approval result sheet

#### 5. Cloud Sync Screen

- provider selector
- provider configuration
- test connection
- pull from cloud
- push to cloud
- last sync status
- last sync time
- conflict warning area

#### 6. Settings Screen

- theme settings
- localization
- vault settings
- export/import
- sync behavior
- security options

## Data Model

### Local Vault

Use an encrypted local vault containing:

- imported `.maFile` data
- app metadata
- favorites / UI preferences
- cloud sync settings

Do not store plain credentials for auto-login in v1.

### Suggested Vault Structure

```json
{
  "vaultVersion": 1,
  "accounts": [
    {
      "steamId": "7656119...",
      "accountName": "exampleaccount",
      "maFile": { }
    }
  ],
  "preferences": {
    "language": "en",
    "theme": "graphite"
  },
  "sync": {
    "provider": "webdav",
    "remotePath": "SDAppVaultMobile",
    "lastSyncUtc": "2026-06-24T12:00:00Z",
    "lastSyncAction": "pull",
    "lastSyncSuccess": true
  }
}
```

### Compatibility

The Android app should be compatible with existing SDA++ `.maFile` accounts.

For v1:

- import `.maFile` directly
- export `.maFile` directly
- keep desktop compatibility as a hard requirement

## Encryption Model

### Requirements

- vault must be encrypted at rest
- cloud uploads must always be encrypted
- decrypted account data should live in memory only as long as necessary

### Recommended Approach

- derive encryption key from user passphrase using a modern KDF
- use Android Keystore for storing wrapped secrets if user enables device-assisted unlock later
- support manual passphrase unlock as the baseline

### v1 Simplicity Rule

Do not build a complex multi-key system first.

For MVP:

- one vault passphrase
- one encrypted local vault
- same encrypted payload uploaded to cloud

## Cloud Sync Model

### Sync Philosophy

Start simple and predictable:

- manual pull
- manual push
- visible last sync result
- explicit conflict warnings

### Recommended v1 Providers

Prioritize:

1. WebDAV
2. Google Drive

Optional after those:

- Dropbox
- OneDrive Personal
- S3-compatible storage

### Sync Rules

- push uploads encrypted vault blob
- pull downloads encrypted vault blob
- app compares timestamps / revision ids
- if remote changed after local, show warning before overwrite
- do not silently merge accounts in v1

### Last Sync UI

Show:

- provider name
- connected / not connected
- last action
- last result
- last sync time

## QR Login Flow

### v1 Behavior

- scan Steam login QR with phone camera
- decode QR payload
- if supported Steam login QR, approve with selected account
- show clear success or failure message

### Important Constraints

- QR flow should use the same safe Steam approval approach as desktop where possible
- user must choose the account used for approval
- no background auto-approval

## Steam Guard Code Screen

### Must Have

- large code display
- visible timer
- copy button
- account name
- quick switch to next account

### Design Direction

- dark graphite base
- glass-like cards only if performance remains smooth
- minimal clutter
- stronger emphasis on readability than decoration

## UI / UX Direction

### Tone

- premium but restrained
- more modern than classic SDA
- simple enough for frequent daily use

### Visual Rules

- separate mobile design language from WinForms desktop
- dark graphite palette
- sharp typography
- accent color for active states only
- avoid overly dense settings screens

### UX Principles

- one clear primary action per screen
- destructive actions require confirmation
- sync actions always show result
- sensitive state always visible

## Security Rules

- never log secrets or tokens
- never upload unencrypted vault data
- avoid exposing raw cloud credentials in UI after save
- clipboard copy should be explicit and temporary where practical
- do not store passwords for auto-login in MVP

## Suggested Project Modules

### `app`

- Compose screens
- navigation
- view models

### `core-model`

- account models
- vault models
- sync state models

### `core-crypto`

- encryption / decryption
- key derivation
- secure storage helpers

### `core-steam`

- Steam Guard generation
- QR approval logic
- `.maFile` import/export

### `core-sync`

- sync abstractions
- provider clients
- pull/push orchestration

## MVP Milestones

### Milestone 1: Vault Foundation

- create Android project
- implement encrypted local vault
- import `.maFile`
- render account list
- show Steam Guard code

### Milestone 2: Cloud Sync

- provider abstraction
- WebDAV support
- manual pull / push
- last sync status UI

### Milestone 3: QR Login

- camera QR scan
- decode Steam QR payload
- selected-account approval flow
- result and error handling

### Milestone 4: Polish

- favorites
- localization
- settings cleanup
- export / restore UX

## Definition of Done for v1

SDA++ Mobile v1 is ready when:

- a user can import or restore accounts safely
- Steam Guard codes are reliable
- encrypted cloud backup works end-to-end
- QR login approval works for a selected account
- the app feels stable and understandable on a normal Android phone

## How You Can Help

Useful input from you:

- choose the first sync providers
- choose the preferred visual direction
- provide 2 to 4 UI references you like
- define what absolutely must be on the first release
- test restore and sync flow expectations from a real-user perspective

## Next Recommended Step

After this spec, the next concrete step should be:

- scaffold `SDA++ Mobile` as a Kotlin + Jetpack Compose Android project
- define the shared vault format more formally
- implement local encrypted vault import for `.maFile`
