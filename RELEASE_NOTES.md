## SDA++ v1.1.0

This release expands SDA++ with stronger QR workflows, cleaner session handling, refreshed branding, and the first public SDA++ Mobile companion build.

### Added

- Desktop Steam QR scan and approval workflow
- Global QR hotkeys:
  - `Ctrl + Shift + S` to scan and approve Steam QR
  - `Ctrl + Shift + Q` to toggle QR hotkey mode / overlay
- Updated SDA++ branding and custom icon
- Account filtering and favorites workflow improvements
- Credentials-backed session recovery flow
- Better session health visibility
- Android companion app:
  - encrypted local vault
  - `.maFile` import
  - Steam Guard code generation
  - WebDAV backup sync
  - PIN / biometric lock
  - Steam-style QR camera screen
  - modern Steam QR approval for valid session-backed imports

### Improved

- Terminate-all-sessions behavior for selected accounts
- Steam session restore logic after session termination
- Cloud backup workflow structure
- Multi-account navigation and selection flow
- Dark UI consistency across multiple forms and screens
- Android settings / QR / account navigation usability

### Fixed

- Multiple localization issues
- Windows icon / branding inconsistencies
- QR parsing issues for large modern Steam client IDs
- Android dropdown interaction issues
- Settings screen clipping under bottom navigation
- QR camera lingering during screen transitions
- Kotlin / Gradle daemon instability caused by locked incremental cache files on Windows

### Notes

- Sensitive local data such as `maFiles`, manifests, credentials, and backups must never be committed
- SDA++ is not affiliated with Valve or Steam
- Android support already includes core vault, WebDAV, and QR foundations, with broader feature parity still in progress
