package com.sysadmindoc.billminder.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromBillCategory(value: BillCategory): String = value.name

    @TypeConverter
    fun toBillCategory(value: String): BillCategory = BillCategory.valueOf(value)

    @TypeConverter
    fun fromRecurrence(value: Recurrence): String = value.name

    @TypeConverter
    fun toRecurrence(value: String): Recurrence = Recurrence.valueOf(value)

    @TypeConverter
    fun fromReminderTiming(value: ReminderTiming?): String? = value?.name

    @TypeConverter
    fun toReminderTiming(value: String?): ReminderTiming? = value?.let { ReminderTiming.valueOf(it) }
}
