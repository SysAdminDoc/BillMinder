# Changelog

All notable changes to BillMinder will be documented in this file.

## [Unreleased]

- Every bill now stores the date its schedule starts from, and every occurrence is derived from that date. Recurring bills no longer drift as the current date moves, and biweekly and quarterly cycles keep their phase.
- Payments are recorded against a cycle key instead of a recomputed timestamp. The same occurrence can only be paid once, so marking a bill paid twice (from the app, a notification, the watch, or an import) records one payment.
- Unpaid occurrences stay overdue instead of quietly rolling forward to the next cycle.
- The add and edit form now takes a due date from a date picker, so one-time and yearly bills can be entered properly. Day-of-month and day-of-week number entry is gone.
- The calendar shows real occurrences. A weekly bill no longer paints its day-of-week number onto that day of the month.
- Removed the destructive database fallback that could erase saved bills and payments on an unexpected schema change, and started exporting Room schemas.
- Reminder broadcasts now finish their database work before the receiver is released, and alarms carry the cycle they belong to.
- Saving, duplicating, importing, deleting, and undoing a bill each run as a single database transaction. A failure part-way through leaves the previous state alone rather than a half-written bill.
- Deleting a bill is permanent and takes its payees and payment history with it. Undo puts the whole thing back under its original identifier, and receipt files are only destroyed once the undo window has closed.
- If the saved database cannot be opened, the app now explains what happened and leaves the file alone instead of crashing.

## [v2.4.0]: 2026-08-31

- Reworked all six primary screens from approved image references using a deeper midnight ledger palette.
- Tightened Home totals, bill groups, date states, and mark-paid affordances around the selected direction.
- Refined Calendar status markers, the selected-day agenda, Insights charts, and Settings groups for closer visual parity.
- Reordered the add/edit flow around Essentials, Schedule, reminders, and additional details. Advanced variable and split controls stay available without crowding the main form.
- Expanded bill details with category, tags, website, payment actions, and a clearer overdue hierarchy.
- Added matched mockup and implementation captures for every redesigned page, plus a second density and accessibility pass.
- Fixed the swipe-delete background bleed that appeared during side-by-side visual review.

## [v2.3.0]: 2026-08-29

- Rebuilt Home, Calendar, Insights, Settings, bill details, and add/edit around a compact dark ledger design.
- Added shared grouped surfaces, square controls, restrained 12dp geometry, and a denser type scale.
- Added a seven-day payment horizon, clearer overdue grouping, richer due-state summaries, and compact bill rows.
- Kept search, sorting, payment actions, calendar handoff, security settings, import/export, forecasting, and split bill workflows working through the redesign.
- Added six approved visual references plus refreshed screenshots for every redesigned page.
- Fixed legacy color values so saved bill accents render consistently across old and new records.
- Added adaptive large-text layouts for the home summary and grouped settings rows.
- Fixed Wear OS packaging metadata, backup exclusions, transient activity history behavior, and release signing.
- Verified the redesigned pages on an isolated Android 15 emulator with populated bill and payment data, including a 130% font-scale pass.

## [v2.2.1]: 2026-08-29

- Added a new adaptive app icon with a monochrome themed variant and refreshed legacy assets.

## [v2.2.0]: 2026-08-03

- Added variable-amount bill ranges, observed-holiday reminder scheduling, dismissal-based reminder escalation, forecast and what-if planning, year-end CSV reports, and next-3/month-total widgets.
- Added salted PIN fallback, configurable auto-lock, screenshot protection, and an optional duress PIN decoy view.
- Hardened CSV quoting, backup restore ID remapping, and added JVM coverage for amount validation and holiday handling.
- Added split bills with Room-backed payees, percentage validation, share calculations, and undo/duplicate preservation.
- Added encrypted image/PDF receipt attachments to payment records, with private-file viewing and cleanup on undo.
- Added a 400+ entry merchant alias normalizer for bill entry and JSON restore, including common statement descriptors.
- Added native bill/payment currencies, offline dashboard FX conversion, manual rate overrides, and currency-aware widgets, notifications, and exports.
- Added an opt-in full-screen alarm reminder activity with mark-paid, snooze, and dismiss actions.
- Added an opt-in home geofence that reminds about the next unpaid bills after entering and dwelling within a configurable radius.
- Added an opt-in calendar handoff for the next due date, preserving recurring bill cadence in the calendar composer.
- Added a 12-month paid-versus-outstanding dashboard projection with offline multi-currency conversion.
- Added persisted per-category monthly budgets with selected-currency progress rings and over-limit states.
- Added a CSV import wizard with header suggestions, quoted-field parsing, bill/payment mapping, and skipped-row reporting.
- Added Mint, Tiller, and Empower migration presets that turn transaction exports into one-time paid bills.
- Added deterministic Bluecoins, YNAB, and Actual Budget CSV exports with target-currency conversion where required.
- Added a Wear OS companion tile with Data Layer synchronization and one-tap mark-paid handoff.
- Added a keyguard-capable compact widget that keeps payment amounts off the lock screen.
- Verified the release R8 minification and resource-shrinking task without invoking signing.
- Added an opt-in local SMS scanner with deterministic amount/date parsing and review-before-import proposals.
- Added Vacation Mode to pause auto-pay reminders while preserving manual-payment reminders.
- Added on-device receipt OCR for image attachments and first-page PDFs, with editable amount and payment-date suggestions.
- Added local CSV column-learning that remembers repeated mapping corrections after three confirmations.
- Added Play and F-Droid app flavors; the F-Droid build removes Play geofencing and Wear Data Layer integrations and contains no billing or crash-reporting dependency.

## [v2.1.1]: 2026-04-29

- Fixed: Launch crash on Android 13+ ("Can only use lower 16 bits for requestCode") by pinning androidx.fragment 1.8.5 to override the old fragment lib pulled in by biometric 1.1.0.
- Fixed: Build failure on adaptive icon vectors (`ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml`): duplicate `android:pivotX` / `android:pivotY` attributes on the rotated outer group.
- Sideload-ready debug APK published as the release asset (no production keystore yet).

## [v2.1.0]: %Y->- (HEAD -> master, tag: v2.0.0, origin/master)

- BillMinder v2.1.0: UX polish, templates, streaks, undo, confetti
- Fixed: Fix build: add gradle wrapper, fix settings.gradle.kts, add missing category icons
- BillMinder v2.0.0: Major upgrade with features from competitor research
- BillMinder v1.0.0: Bill tracker with alarm-style reminders

## Roadmap archive: 2026-08-10: ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# BillMinder: Roadmap

Android bill tracking and reminder app. Kotlin + Jetpack Compose + Material 3, Room DB, AlarmManager-based reminders, Glance home-screen widget, biometric lock, Catppuccin AMOLED theme.

## Planned Features

### Core

### Reminders

### Dashboard

### Data / Sync

### Widget / Wear

### Security

### Packaging / QA

## Competitive Research
- **Bills Monitor / Bills Monitor Pro**: popular, simple category+due-date tracker; reviewed as buggy (misses reminders) and lacks sharing. Our edge: exact-alarm reliability + biometric lock + AMOLED UI.
- **TimelyBills**: calendar view, in-app + system reminders, family sharing, no bank linking. Good reference for the sharing model if we add it.
- **BillOut**: manual entry, calendar UI, privacy-first. Overlaps most with BillMinder; differentiate via widget quality + export portability.
- **Monefy**: expense tracker, not bill reminder; not a direct competitor but the visual language is worth studying.

## Nice-to-Haves

## Open-Source Research (Round 2)

### Related OSS Projects
- **mkdaly/Payment-Reminder**: https://github.com/mkdaly/Payment-Reminder: Closest functional twin; bills + reminders + sufficient-funds check.
- **mtotschnig/MyExpenses**: https://github.com/mtotschnig/MyExpenses: GPL Android expense tracker; recurring-transaction planner, widget support, password + device-lock security, bank-statement reconciliation.
- **dsolonenko/financisto**: https://github.com/dsolonenko/financisto: Long-lived personal finance app; reference for export/import formats and currency/locale handling.
- **firefly-iii/firefly-iii**: https://github.com/firefly-iii/firefly-iii: Self-hosted web finance manager; rule engine for transaction categorization is transplantable.
- **Tanq16/ExpenseOwl**: https://github.com/Tanq16/ExpenseOwl: Simple self-hosted tracker with PWA installable on Android; recurring transactions + custom categories + currency symbol.
- **Wapy.dev**: listed under the expense-tracker topic: subscription/recurring-expense dashboard, reminder-focused.
- **Material You recurring expense tracker** (on money-manager topic): https://github.com/topics/money-manager: Material-3 expressive UI reference.

### Features to Borrow
- **Sufficient-funds pre-check reminder** (mkdaly/Payment-Reminder): before a scheduled payment, remind the user to verify that the source account will have funds, not just "your bill is due." Adds real value on top of "bill tracker" apps.
- **Bank-statement reconciliation import** (MyExpenses): import a bank CSV/OFX/QIF, auto-mark bills as Paid when a debit matches the amount/date/merchant, flag discrepancies.
- **Rule engine for auto-categorization** (firefly-iii): user-defined rules: `if merchant matches "Comcast*" and amount > $50 then category=Internet`. Useful once import is wired.
- **Recurring-income tracking** (ExpenseOwl): pair recurring bills with recurring paychecks, so the dashboard shows "estimated balance end of month," not just "estimated outflow."
- **Homescreen shortcut + widget to Quick-Add bill** (MyExpenses): long-press launcher shortcut to jump straight into Add Bill pre-filled with today's date.
- **Device-lock + biometric gate** (MyExpenses): hide amounts behind device credential, show masked totals on widget/lockscreen.
- **Export/import schema (CSV/JSON/XLSX)** (financisto, ExpenseOwl): documented, versioned backup schema that round-trips through git; more trustworthy than proprietary DB dumps.
- **Currency-aware multi-account** (MyExpenses, financisto): second account in EUR/GBP with live FX conversion, for users who pay international bills.
- **Subscription-specific reminders + cost-per-year rollup** (Wapy.dev): subscriptions get their own section showing annualized cost, next-billing date, cancel URL.
- **PWA fallback + web companion** (ExpenseOwl): a tiny read-only PWA for viewing on a laptop browser; shares the same JSON DB via optional sync folder.

### Patterns & Architectures Worth Studying
- **Plan/Schedule entity separate from Transaction** (MyExpenses): a Plan owns the recurrence and generates Transactions; when a user edits "monthly rent", all future unpaid instances update atomically.
- **Rule engine on import** (firefly-iii): pure-function matchers: each rule is `(tx) => Partial<Tx>` composed in order; testable without Android.
- **Room + KSP + Paging 3** (Material You recurring tracker): idiomatic Compose stack; replace Paging 2 if BillMinder still uses the legacy version.
- **WorkManager for reminder scheduling with AlarmManager fallback**: most modern Android finance apps use WM for daily "scan bills, fire reminders" then AlarmManager for the precise notification time, avoiding the exact-alarm permission prompt where possible.
- **Widget (Glance) for "next 3 bills"** (MyExpenses widget pattern): a Glance widget reads the Room DB via a repository shared with the app and renders the next 3 upcoming bills with tap-to-mark-paid.
```

</details>
