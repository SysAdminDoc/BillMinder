# Changelog

All notable changes to BillMinder will be documented in this file.

## [Unreleased]

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

## [v2.1.1] - 2026-04-29

- Fixed: Launch crash on Android 13+ ("Can only use lower 16 bits for requestCode") by pinning androidx.fragment 1.8.5 to override the old fragment lib pulled in by biometric 1.1.0.
- Fixed: Build failure on adaptive icon vectors (`ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml`) — duplicate `android:pivotX` / `android:pivotY` attributes on the rotated outer group.
- Sideload-ready debug APK published as the release asset (no production keystore yet).

## [v2.1.0] - %Y->- (HEAD -> master, tag: v2.0.0, origin/master)

- Changed: Update CLAUDE.md for v2.1.0
- BillMinder v2.1.0 - UX polish, templates, streaks, undo, confetti
- Fixed: Fix build: add gradle wrapper, fix settings.gradle.kts, add missing category icons
- BillMinder v2.0.0 - Major upgrade with features from competitor research
- BillMinder v1.0.0 - Bill tracker with alarm-style reminders
