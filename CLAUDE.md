# BillMinder v2.1.0

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
- `data/BillDao.kt` - Room DAO with search, category filter, spending aggregation, lifetime/streak queries
- `data/BillDatabase.kt` - Room DB singleton with MIGRATION_1_2
- `data/BillTemplates.kt` - 28 quick-add templates (Netflix, Spotify, Rent, etc.)
- `data/BackupManager.kt` - JSON export/import, CSV export via SAF
- `notification/ReminderScheduler.kt` - AlarmManager scheduling, next due date calculation
- `notification/ReminderReceiver.kt` - Handles BILL_REMINDER, MARK_PAID, SNOOZE, SNOOZED_REMINDER, BOOT_COMPLETED
- `notification/NotificationHelper.kt` - Notifications with Paid/1hr/Tomorrow action buttons
- `viewmodel/BillViewModel.kt` - Search/sort/filter, undo delete, duplicate, custom payment, chart data
- `widget/BillMinderWidget.kt` - Glance widget showing top 5 upcoming bills
- `ui/screens/HomeScreen.kt` - Dashboard with search, filter chips, sort, swipe+undo, mark-paid dialog
- `ui/screens/AddEditBillScreen.kt` - Form with quick-add templates, tags, payment URL
- `ui/screens/BillDetailScreen.kt` - Bill info, streak badge, pay now, duplicate, share, lifetime spending
- `ui/screens/CalendarScreen.kt` - Monthly calendar with bill dots
- `ui/screens/StatsScreen.kt` - Lifetime total, yearly projection, category pie chart, monthly trend line
- `ui/screens/SettingsScreen.kt` - Biometric toggle, backup/restore, CSV export
- `ui/components/MarkPaidDialog.kt` - Custom amount + confirmation # dialog
- `ui/components/SummaryCard.kt` - Monthly summary with next due, confetti on all-paid
- `ui/components/BillCard.kt` - Bill card with long-press for custom payment
- `ui/theme/` - Catppuccin Mocha AMOLED dark theme

## Build
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
./gradlew assembleDebug
```

## Version History
- v1.0.0 - Initial release. Full bill CRUD, dual reminders, calendar, payment history.
- v2.0.0 - Major upgrade. Widget, biometric, search/sort/filter, charts, snooze, backup/export, swipe-to-delete, bottom nav.
- v2.1.0 - UX polish. Undo delete, custom payment dialog, quick-add templates (28), on-time streak, duplicate/share/pay-now, confetti, yearly projection, next-due in summary.

## Gotchas
- AlarmManager.setAlarmClock used for reliability (shows alarm icon in status bar)
- On Android 12+ checks canScheduleExactAlarms() and falls back to setAndAllowWhileIdle
- Due date for monthly bills uses coerceAtMost to handle months with fewer days
- Payment matching uses dueDate field to track which cycle was paid
- Notification IDs: billId for reminders, billId+20000 for overdue, billId+30000/40000 for snooze, billId+60000 for snoozed alarm
- DB migration v1->v2 adds paymentUrl, tags (bill), confirmationNumber (payment)
- BiometricPrompt requires FragmentActivity (not ComponentActivity)
- On-time streak counts consecutive payments where paidAt <= dueDate
- Confetti uses rememberInfiniteTransition with Canvas particles
- Long-press pay button opens MarkPaidDialog for custom amount
