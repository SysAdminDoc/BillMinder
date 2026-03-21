package com.sysadmindoc.billminder.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromBillCategory(value: BillCategory): String = value.name

    @TypeConverter
    fun toBillCategory(value: String): BillCategory =
        try { BillCategory.valueOf(value) } catch (_: Exception) { BillCategory.OTHER }

    @TypeConverter
    fun fromRecurrence(value: Recurrence): String = value.name

    @TypeConverter
    fun toRecurrence(value: String): Recurrence =
        try { Recurrence.valueOf(value) } catch (_: Exception) { Recurrence.MONTHLY }

    @TypeConverter
    fun fromReminderTiming(value: ReminderTiming?): String? = value?.name

    @TypeConverter
    fun toReminderTiming(value: String?): ReminderTiming? =
        value?.let { try { ReminderTiming.valueOf(it) } catch (_: Exception) { ReminderTiming.ONE_DAY } }
}
