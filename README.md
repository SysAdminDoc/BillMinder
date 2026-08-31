![Version 2.4.0](https://img.shields.io/badge/version-2.4.0-58A6FF?style=for-the-badge)
![MIT license](https://img.shields.io/badge/license-MIT-4ade80?style=for-the-badge)
![Android](https://img.shields.io/badge/platform-Android-58A6FF?style=for-the-badge)

# BillMinder v2.4.0

BillMinder keeps recurring bills, due dates, and payment history in one Android app. It works offline and asks for no network permission.

## Screenshots

| Home | Calendar | Insights |
| --- | --- | --- |
| ![Home dashboard](docs/screenshots/v2.4.0/home.png) | ![Monthly calendar](docs/screenshots/v2.4.0/calendar.png) | ![Spending insights](docs/screenshots/v2.4.0/insights.png) |

| Add bill | Bill details | Settings |
| --- | --- | --- |
| ![Add bill form](docs/screenshots/v2.4.0/add-bill.png) | ![Bill details](docs/screenshots/v2.4.0/bill-detail.png) | ![Settings](docs/screenshots/v2.4.0/settings.png) |

## What it tracks

- Bills can carry an amount, due date, category, notes, tags, a payment URL, and optional receipt files.
- Six schedules cover weekly, biweekly, monthly, quarterly, yearly, and one-time bills. Every occurrence stays anchored to the date the user chose.
- Payment history is tied to the exact occurrence. An unpaid cycle stays overdue instead of disappearing when the next cycle begins.
- Variable amounts and percentage splits handle utility bills or shared household costs.
- Each bill keeps its native currency. Dashboard totals use an offline FX snapshot with optional manual overrides.

The Home page has search, category filters, due-state groups, and one-tap payment actions. Calendar shows the actual occurrences for each day. Insights covers category totals, budgets, forecasts, annual cost, and a paid-versus-outstanding plan.

## Reminders

BillMinder schedules two optional reminders per bill. Available timing ranges from the due day to one month ahead. Exact delivery is used when Android allows it, with a documented fallback when exact alarms are unavailable.

Notifications include Paid, one-hour snooze, and tomorrow actions. Unpaid bills get a separate overdue alert. Reminders are rebuilt after a reboot, app update, clock change, or timezone change.

The full sideload build also has an optional alarm-style due screen and local SMS proposal scan. Shared payment text can be reviewed without granting inbox access.

## Privacy and security

- Biometric unlock requires a PIN fallback, so loss of biometric access cannot lock the user out.
- PIN and duress PIN records use versioned PBKDF2-HMAC-SHA256 with a random salt and 600,000 rounds. Older PIN records migrate after the next successful entry.
- Five incorrect entries start a persisted wait. Continued failures increase the delay up to one hour, while a successful PIN or biometric unlock clears it.
- Screenshot protection follows the live lock setting. Turning security on applies it without restarting the app.
- Private Notifications & Widgets replaces bill names and amounts on external surfaces. Hide Amounts in App masks financial values throughout the read-only UI.
- Receipt images and PDFs stay encrypted in app-private storage. OCR runs on the device with the bundled ML Kit model.

The app declares no internet or location permission. It has no analytics SDK, crash reporter, billing library, or bank connection.

## Data and portability

CSV import starts with a preview and supports learned column mappings plus Mint, Tiller, and Empower presets. Exports include payment CSV, year-end CSV, and formats for Bluecoins, YNAB, or Actual Budget.

The current JSON path exports bills and payment history. It does not yet include settings, split payees, or receipt bytes, so it should not be treated as a complete device backup.

Room stores the live data and has explicit migrations for every shipped schema. An unreadable database is left untouched and shown through the recovery screen.

## Current builds

BillMinder is distributed as a sideloaded APK from GitHub Releases. The repository still has two phone flavors:

- `fdroid` is the full build. It includes the local SMS scanner, full-screen reminders, and `USE_EXACT_ALARM`.
- `play` omits those restricted permissions and accepts shared payment text instead.

Neither flavor requests network access. Store publishing is not part of the release process.

## Build locally

Use JDK 21 and an Android SDK with API 36 installed.

```bash
./gradlew :app:assemblePlayDebug :app:assembleFdroidDebug
```

The APKs are written under:

```text
app/build/outputs/apk/play/debug/
app/build/outputs/apk/fdroid/debug/
```

Run the JVM suite and Android lint before installing a build:

```bash
./gradlew test
./gradlew lint
```

### Signed release APKs

Copy the signing template and enter credentials for a keystore you control:

```bash
cp keystore.properties.example keystore.properties
```

The file is already ignored by Git. It accepts `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`.

```bash
./gradlew :app:assemblePlayRelease :app:assembleFdroidRelease
```

Both tasks stop with a direct error when signing details are missing. A successful build creates signed, minified APKs in each flavor's `release` directory.

## License

BillMinder is available under the [MIT License](LICENSE).
