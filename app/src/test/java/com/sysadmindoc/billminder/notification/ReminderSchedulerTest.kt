package com.sysadmindoc.billminder.notification

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.data.Recurrence
import com.sysadmindoc.billminder.data.ReminderTiming
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        BillDatabase.closeInstance()
        context.deleteDatabase("billminder.db")
        clearAlarms()
    }

    @After
    fun tearDown() {
        BillDatabase.closeInstance()
        context.deleteDatabase("billminder.db")
    }

    private fun clearAlarms() {
        shadowOf(alarmManager).scheduledAlarms.toList()
            .mapNotNull { it.operation }
            .forEach { alarmManager.cancel(it) }
    }

    private fun scheduledUris(): List<String> =
        shadowOf(alarmManager).scheduledAlarms.mapNotNull {
            shadowOf(it.operation).savedIntent?.dataString
        }.sorted()

    private fun futureBill(
        id: Long,
        second: ReminderTiming? = ReminderTiming.ONE_WEEK
    ): Bill {
        val anchor = LocalDate.now(ZoneId.systemDefault()).plusMonths(1).withDayOfMonth(15)
        return Bill(
            id = id,
            name = "Bill $id",
            amount = 100.0,
            dueDay = 15,
            recurrence = Recurrence.MONTHLY,
            reminderTiming = ReminderTiming.ONE_DAY,
            secondReminderTiming = second,
            anchorEpochDay = anchor.toEpochDay()
        )
    }

    @Test
    fun `primary second and overdue alarms coexist for one bill`() {
        ReminderScheduler.scheduleReminder(context, futureBill(1L))
        assertEquals(
            listOf(
                "billminder://overdue_alarm/1",
                "billminder://primary_reminder/1",
                "billminder://second_reminder/1"
            ),
            scheduledUris()
        )
    }

    @Test
    fun `rescheduling replaces rather than duplicates`() {
        val bill = futureBill(1L)
        ReminderScheduler.scheduleReminder(context, bill)
        ReminderScheduler.scheduleReminder(context, bill)
        ReminderScheduler.scheduleReminder(context, bill)
        assertEquals(3, scheduledUris().size)
    }

    @Test
    fun `bill ids that differ by a legacy offset no longer collide`() {
        // The old scheme used billId + 50000 for the second reminder, so these two bills shared
        // a request code and silently cancelled each other.
        ReminderScheduler.scheduleReminder(context, futureBill(1L))
        ReminderScheduler.scheduleReminder(context, futureBill(50_001L))
        val uris = scheduledUris()
        assertEquals(6, uris.size)
        assertEquals(6, uris.toSet().size)
        assertTrue(uris.contains("billminder://second_reminder/1"))
        assertTrue(uris.contains("billminder://primary_reminder/50001"))
    }

    @Test
    fun `a bill id past the int range keeps its own alarms`() {
        ReminderScheduler.scheduleReminder(context, futureBill(1L))
        ReminderScheduler.scheduleReminder(context, futureBill(Int.MAX_VALUE + 2L))
        assertEquals(6, scheduledUris().toSet().size)
    }

    @Test
    fun `cancelling a bill removes exactly its own alarms`() {
        ReminderScheduler.scheduleReminder(context, futureBill(1L))
        ReminderScheduler.scheduleReminder(context, futureBill(2L))
        ReminderScheduler.cancelReminder(context, 1L)
        assertEquals(
            listOf(
                "billminder://overdue_alarm/2",
                "billminder://primary_reminder/2",
                "billminder://second_reminder/2"
            ),
            scheduledUris()
        )
    }

    @Test
    fun `a bill without a second reminder schedules two alarms`() {
        ReminderScheduler.scheduleReminder(context, futureBill(1L, second = null))
        assertEquals(
            listOf("billminder://overdue_alarm/1", "billminder://primary_reminder/1"),
            scheduledUris()
        )
    }

    @Test
    fun `the reminder fires the configured number of days before the occurrence`() {
        val zone = ZoneId.of("UTC")
        val cycle = LocalDate.parse("2026-06-15")
        val fired = ReminderScheduler.reminderTimeFor(cycle, daysBeforeDue = 1, zone = zone)
        val expected = LocalDate.parse("2026-06-14").atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, fired)
    }

    @Test
    fun `reboot app update time and timezone broadcasts all rebuild the alarms`() {
        val repo = BillRepository(BillDatabase.getDatabase(context))
        val saved = runBlocking { repo.saveBillWithPayees(futureBill(0L), null) }!!
        val expected = listOf(
            "billminder://overdue_alarm/${saved.id}",
            "billminder://primary_reminder/${saved.id}",
            "billminder://second_reminder/${saved.id}"
        )
        val receiver = ReminderReceiver()
        try {
            listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED
            ).forEach { action ->
                clearAlarms()
                assertTrue(scheduledUris().isEmpty())
                receiver.onReceive(context, Intent(action))
                assertEquals("$action must restore every alarm", expected, awaitAlarms(expected.size))
            }
        } finally {
            runBlocking { repo.deleteBillGraph(saved.id) }
            clearAlarms()
        }
    }

    /** The receiver reschedules off the main thread, so give it a moment to land. */
    private fun awaitAlarms(count: Int): List<String> {
        val deadline = System.currentTimeMillis() + 10_000L
        var uris = scheduledUris()
        while (uris.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(25L)
            uris = scheduledUris()
        }
        return uris
    }

    @Test
    fun `a disabled bill gets no alarms`() {
        ReminderScheduler.scheduleAllReminders(context, listOf(futureBill(1L).copy(isEnabled = false)))
        assertTrue(scheduledUris().isEmpty())
    }
}
