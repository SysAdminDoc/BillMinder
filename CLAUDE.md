# BillMinder v1.0.0

## Tech Stack
- Kotlin 2.1.0, Jetpack Compose, Material 3
- Room 2.6.1 (bills + payments tables)
- AlarmManager (setAlarmClock) for exact reminders
- Navigation Compose for routing
- Target SDK 35, Min SDK 26

## Architecture
- MVVM: BillViewModel (AndroidViewModel) -> BillRepository -> BillDao -> Room
- Single Activity (MainActivity) with NavHost
- Notification system: ReminderScheduler (AlarmManager) -> ReminderReceiver (BroadcastReceiver) -> NotificationHelper
- Two notification channels: bill_reminders (high), bill_overdue (high, ongoing)
- Boot receiver reschedules all alarms on device restart

## Key Files
- `data/Bill.kt` - Bill + Payment entities, enums (BillCategory, Recurrence, ReminderTiming)
- `data/BillDao.kt` - Room DAO with Flow queries
- `data/BillDatabase.kt` - Room DB singleton
- `notification/ReminderScheduler.kt` - AlarmManager scheduling, next due date calculation
- `notification/ReminderReceiver.kt` - Handles BILL_REMINDER, MARK_PAID, BOOT_COMPLETED
- `viewmodel/BillViewModel.kt` - BillWithStatus, MonthlySummary, all bill operations
- `ui/screens/HomeScreen.kt` - Dashboard with summary + bill list (overdue/upcoming/paid sections)
- `ui/screens/AddEditBillScreen.kt` - Form with dropdowns, color picker, toggles
- `ui/screens/BillDetailScreen.kt` - Bill info + payment history
- `ui/screens/CalendarScreen.kt` - Monthly calendar with bill dots
- `ui/theme/` - Catppuccin Mocha AMOLED dark theme

## Build
```bash
./gradlew assembleDebug
```

## Version History
- v1.0.0 - Initial release. Full bill CRUD, dual reminders, calendar, payment history.

## Gotchas
- AlarmManager.setAlarmClock used for reliability (shows alarm icon in status bar)
- On Android 12+ checks canScheduleExactAlarms() and falls back to setAndAllowWhileIdle
- Due date for monthly bills uses coerceAtMost to handle months with fewer days (e.g., Feb)
- Payment matching uses dueDate field to track which cycle was paid
- Notification IDs: billId for reminders, billId+20000 for overdue, billId+50000 for second reminder alarms
