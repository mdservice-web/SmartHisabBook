# Smart Hisab Book (initial working build)

A simple, offline-first Android ledger for customers and suppliers.

## Features in this build
- Add customer/supplier records
- Record customer credit and received payments
- Record supplier bills and payments
- View customer/supplier balances and recent transactions
- Export a CSV backup through Android's document picker
- No internet permission; transaction data stays on-device

## Build APK using GitHub Actions
1. Create a GitHub repository named `SmartHisabBook`.
2. Upload all files/folders in this project to the repository root.
3. Open **Actions** and enable workflows if GitHub asks.
4. Select **Build Smart Hisab Book APK** and press **Run workflow** (or push to `main`).
5. Open the completed workflow run, scroll to **Artifacts**, and download `SmartHisabBook-debug-apk`.
6. Extract the ZIP to get `app-debug.apk`.

## Important limitations before charging customers
- This is an initial working build, not a fully tested commercial release.
- It currently has no annual licence activation, payment verification, or one-device enforcement.
- The CSV export is a backup/export, not a restore/import feature yet.
- Balances are a basic ledger calculation; thoroughly test with sample entries before using real shop accounts.
- Do not promise a secure one-year licence or collect ₹499 for a locked/verified licence until that system is implemented and tested.
- Keep CSV backups. Clearing app data or uninstalling can remove local records.

## Version
1.0 (debug build via GitHub Actions)
