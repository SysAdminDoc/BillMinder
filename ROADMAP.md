# BillMinder — Roadmap

Android bill tracking and reminder app. Kotlin + Jetpack Compose + Material 3, Room DB, AlarmManager-based reminders, Glance home-screen widget, biometric lock, Catppuccin AMOLED theme.

## Planned Features

### Core
- **Variable-amount bills** — estimate-range fields (min/expected/max) for utilities
- **Split bills** — one bill, multiple payees, share-with-roommate math
- **Attachments** — paste/snap receipt, store in encrypted internal storage, attach to payment record
- **Merchant normalizer** — 300-entry alias list so "NETFLX.COM/BILL" maps to "Netflix"
- **Holiday-aware due dates** — if due date lands on Sat/Sun/US federal holiday, surface the previous business day in the reminder
- **Multi-currency** with offline FX rates (bundled snapshot + manual override)

### Reminders
- Full-screen alarm-style notification option (same surface as alarm clock apps)
- Geofence reminder — "remind me when I'm home" via `Geofencing API`
- Cascading reminders — if dismissed, escalate at 4h / 24h / overdue
- Calendar integration — push due dates to the user's default calendar as an opt-in sync

### Dashboard
- **12-month view** with diverging-bar chart (paid vs outstanding)
- **Forecast panel** — 30/60/90-day upcoming total
- **Category budgets** — per-category monthly cap with progress ring
- **Year-end export** — tax-ready CSV grouped by category
- **"What-if" panel** — drop a subscription, see annual savings

### Data / Sync
- End-to-end encrypted Google Drive / Dropbox backup (user keypair)
- CSV import with column mapping wizard
- Migration assistant from Tiller / Mint / Empower exports
- Export to Bluecoins / YNAB / Actual Budget formats

### Widget / Wear
- Glance widget: "next 3 bills" variant, "month total" variant
- **Wear OS tile** — single next-due bill, tap to mark paid
- Lock-screen widget (Android 14+)

### Security
- PIN + biometric (pin fallback) with configurable auto-lock timeout
- Auto-lock on app-switcher screenshot
- Duress PIN — entering it shows a decoy empty DB

### Packaging / QA
- Release signing in GitHub Actions with `secrets.KEYSTORE_B64`
- R8 `minifyEnabled true` + `shrinkResources true` audit
- Play Internal Testing track pipeline (`.aab` upload)

## Competitive Research
- **Bills Monitor / Bills Monitor Pro** — popular, simple category+due-date tracker; reviewed as buggy (misses reminders) and lacks sharing. Our edge: exact-alarm reliability + biometric lock + AMOLED UI.
- **TimelyBills** — calendar view, in-app + system reminders, family sharing, no bank linking. Good reference for the sharing model if we add it.
- **BillOut** — manual entry, calendar UI, privacy-first. Overlaps most with BillMinder; differentiate via widget quality + export portability.
- **Monefy** — expense tracker, not bill reminder; not a direct competitor but the visual language is worth studying.

## Nice-to-Haves
- SMS reader (opt-in, READ_SMS) that auto-proposes a new bill from "Your Verizon bill of $X is due on Y" messages
- Email ingestion via IMAP for PDF receipts
- OCR on attached receipts (ML Kit on-device) → auto-fill amount/date
- CSV/JSON import that learns from 3 corrections and auto-maps columns next time
- "Vacation mode" — delays non-critical reminders while toggled
- Open-source on F-Droid build flavor (strip Google Play billing / crash reporters)

## Open-Source Research (Round 2)

### Related OSS Projects
- **mkdaly/Payment-Reminder** — https://github.com/mkdaly/Payment-Reminder — Closest functional twin; bills + reminders + sufficient-funds check.
- **mtotschnig/MyExpenses** — https://github.com/mtotschnig/MyExpenses — GPL Android expense tracker; recurring-transaction planner, widget support, password + device-lock security, bank-statement reconciliation.
- **dsolonenko/financisto** — https://github.com/dsolonenko/financisto — Long-lived personal finance app; reference for export/import formats and currency/locale handling.
- **firefly-iii/firefly-iii** — https://github.com/firefly-iii/firefly-iii — Self-hosted web finance manager; rule engine for transaction categorization is transplantable.
- **Tanq16/ExpenseOwl** — https://github.com/Tanq16/ExpenseOwl — Simple self-hosted tracker with PWA installable on Android; recurring transactions + custom categories + currency symbol.
- **Wapy.dev** — listed under the expense-tracker topic — subscription/recurring-expense dashboard, reminder-focused.
- **Material You recurring expense tracker** (on money-manager topic) — https://github.com/topics/money-manager — Material-3 expressive UI reference.

### Features to Borrow
- **Sufficient-funds pre-check reminder** (mkdaly/Payment-Reminder) — before a scheduled payment, remind the user to verify that the source account will have funds, not just "your bill is due." Adds real value on top of "bill tracker" apps.
- **Bank-statement reconciliation import** (MyExpenses) — import a bank CSV/OFX/QIF, auto-mark bills as Paid when a debit matches the amount/date/merchant, flag discrepancies.
- **Rule engine for auto-categorization** (firefly-iii) — user-defined rules: `if merchant matches "Comcast*" and amount > $50 then category=Internet`. Useful once import is wired.
- **Recurring-income tracking** (ExpenseOwl) — pair recurring bills with recurring paychecks, so the dashboard shows "estimated balance end of month," not just "estimated outflow."
- **Homescreen shortcut + widget to Quick-Add bill** (MyExpenses) — long-press launcher shortcut to jump straight into Add Bill pre-filled with today's date.
- **Device-lock + biometric gate** (MyExpenses) — hide amounts behind device credential, show masked totals on widget/lockscreen.
- **Export/import schema (CSV/JSON/XLSX)** (financisto, ExpenseOwl) — documented, versioned backup schema that round-trips through git; more trustworthy than proprietary DB dumps.
- **Currency-aware multi-account** (MyExpenses, financisto) — second account in EUR/GBP with live FX conversion, for users who pay international bills.
- **Subscription-specific reminders + cost-per-year rollup** (Wapy.dev) — subscriptions get their own section showing annualized cost, next-billing date, cancel URL.
- **PWA fallback + web companion** (ExpenseOwl) — a tiny read-only PWA for viewing on a laptop browser; shares the same JSON DB via optional sync folder.

### Patterns & Architectures Worth Studying
- **Plan/Schedule entity separate from Transaction** (MyExpenses) — a Plan owns the recurrence and generates Transactions; when a user edits "monthly rent", all future unpaid instances update atomically.
- **Rule engine on import** (firefly-iii) — pure-function matchers: each rule is `(tx) => Partial<Tx>` composed in order; testable without Android.
- **Room + KSP + Paging 3** (Material You recurring tracker) — idiomatic Compose stack; replace Paging 2 if BillMinder still uses the legacy version.
- **WorkManager for reminder scheduling with AlarmManager fallback** — most modern Android finance apps use WM for daily "scan bills, fire reminders" then AlarmManager for the precise notification time, avoiding the exact-alarm permission prompt where possible.
- **Widget (Glance) for "next 3 bills"** (MyExpenses widget pattern) — a Glance widget reads the Room DB via a repository shared with the app and renders the next 3 upcoming bills with tap-to-mark-paid.
