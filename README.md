# BillMinder v1.0.0

A bill tracking and reminder app for Android. Never miss a payment again.

## Features

- **Bill Management** - Add, edit, delete bills with name, amount, due date, category, recurrence, and notes
- **Smart Reminders** - Alarm-style notifications via AlarmManager with configurable timing (day of, 1-14 days before)
- **Dual Reminders** - Set two separate reminder timings per bill (e.g., 1 week before + 1 day before)
- **Quick Mark Paid** - One-tap mark as paid from the home screen or directly from the notification
- **Monthly Dashboard** - Summary card showing total due, paid, remaining, with animated progress bar
- **Calendar View** - See all bills on a monthly calendar with color-coded dots
- **Payment History** - Track every payment with timestamps for each bill
- **Auto-Pay Tags** - Flag bills with auto-pay enabled for visual distinction
- **Overdue Detection** - Persistent notifications for overdue unpaid bills
- **Boot Persistence** - Reminders survive device restarts via BOOT_COMPLETED receiver
- **10 Categories** - Rent, Utilities, Insurance, Phone/Internet, Subscription, Loan, Medical, Transportation, Groceries, Other
- **6 Recurrence Types** - Weekly, Bi-Weekly, Monthly, Quarterly, Yearly, One-Time
- **Color Coding** - 10 Catppuccin accent colors, auto-assigned by category

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- Room database for persistence
- AlarmManager for exact alarm-style reminders
- Navigation Compose for routing
- AMOLED dark theme (Catppuccin Mocha)

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
