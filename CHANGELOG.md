# Changelog

All notable changes to BillMinder will be documented in this file.

## [Unreleased]

- Added variable-amount bill ranges, observed-holiday reminder scheduling, dismissal-based reminder escalation, forecast and what-if planning, year-end CSV reports, and next-3/month-total widgets.
- Added salted PIN fallback, configurable auto-lock, screenshot protection, and an optional duress PIN decoy view.
- Hardened CSV quoting, backup restore ID remapping, and added JVM coverage for amount validation and holiday handling.
- Added split bills with Room-backed payees, percentage validation, share calculations, and undo/duplicate preservation.

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
