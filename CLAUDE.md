# BillMinder v2.0.0

## Tech Stack
- Kotlin 2.1.0, Jetpack Compose, Material 3
- Room 2.6.1 (bills + payments tables, migration v1->v2)
- AlarmManager (setAlarmClock) for exact reminders
- Glance 1.1.1 for home screen widget
- AndroidX Biometric for fingerprint/face lock
- Navigation Compose with bottom nav bar
- Target SDK 35, Min SDK 26

## Architecture
- MVVM: BillViewModel (AndroidViewModel) -> BillRepository -> BillDao -> Room
- FragmentActivity (required for BiometricPrompt) with NavHost
- Bottom nav: Home / Calendar / Stats / Settings
- Notification system: ReminderScheduler -> ReminderReceiver -> NotificationHelper
- Snooze: ReminderReceiver schedules new alarm via setAndAllowWhileIdle
- Widget: BillMinderWidget (GlanceAppWidget) -> BillMinderWidgetReceiver
- Backup: BackupManager handles JSON export/import and CSV export

## Key Files
- `data/Bill.kt` - Bill + Payment entities, enums (BillCategory 13 types, Recurrence, ReminderTiming, SortMode)
- `data/BillDao.kt` - Room DAO with search, category filter, spending aggregation, lifetime queries
- `data/BillDatabase.kt` - Room DB singleton with MIGRATION_1_2 (adds paymentUrl, tags, confirmationNumber)
- `data/BackupManager.kt` - JSON export/import, CSV export via SAF
- `notification/ReminderScheduler.kt` - AlarmManager scheduling, next due date calculation
- `notification/ReminderReceiver.kt` - Handles BILL_REMINDER, MARK_PAID, SNOOZE, SNOOZED_REMINDER, BOOT_COMPLETED
- `notification/NotificationHelper.kt` - Notifications with Paid/1hr/Tomorrow action buttons
- `viewmodel/BillViewModel.kt` - Search/sort/filter state, BillWithStatus, MonthlySummary, ChartData, backup/export
- `widget/BillMinderWidget.kt` - Glance widget showing top 5 upcoming bills
- `ui/screens/HomeScreen.kt` - Dashboard with search bar, filter chips, sort, swipe-to-delete, section badges
- `ui/screens/AddEditBillScreen.kt` - Form with tags, payment URL, 13 categories
- `ui/screens/BillDetailScreen.kt` - Bill info + lifetime spending card + payment history
- `ui/screens/CalendarScreen.kt` - Monthly calendar with bill dots
- `ui/screens/StatsScreen.kt` - Lifetime total, category pie chart (Canvas), monthly trend line (Canvas)
- `ui/screens/SettingsScreen.kt` - Biometric toggle, JSON backup/restore, CSV export
- `ui/theme/` - Catppuccin Mocha AMOLED dark theme

## Build
```bash
./gradlew assembleDebug
```

## Version History
- v1.0.0 - Initial release. Full bill CRUD, dual reminders, calendar, payment history.
- v2.0.0 - Major upgrade. Added: home screen widget (Glance), biometric lock, search/sort/filter, spending charts (pie + trend), lifetime spending, snooze notifications, JSON backup/restore, CSV export, swipe-to-delete, bottom navigation, tags, payment URL, 3 new categories, section count badges, staggered animations.

## Gotchas
- AlarmManager.setAlarmClock used for reliability (shows alarm icon in status bar)
- On Android 12+ checks canScheduleExactAlarms() and falls back to setAndAllowWhileIdle
- Due date for monthly bills uses coerceAtMost to handle months with fewer days (e.g., Feb)
- Payment matching uses dueDate field to track which cycle was paid
- Notification IDs: billId for reminders, billId+20000 for overdue, billId+30000/40000 for snooze actions, billId+60000 for snoozed alarm
- DB migration v1->v2 adds paymentUrl, tags (bill), confirmationNumber (payment)
- BiometricPrompt requires FragmentActivity (not ComponentActivity)
- Widget update period 30min; also updates when bills/payments change
- Chart drawing uses Canvas composable with nativeCanvas for text
