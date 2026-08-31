package com.sysadmindoc.billminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sysadmindoc.billminder.domain.CycleEngine
import java.time.LocalDate
import java.time.ZoneId

/** Whether the stored database can be opened with this build. */
sealed interface DatabaseHealth {
    data object Ready : DatabaseHealth
    data class Unusable(val reason: String, val databasePath: String) : DatabaseHealth
}

@Database(entities = [Bill::class, Payment::class, BillPayee::class], version = 7, exportSchema = true)
@TypeConverters(Converters::class)
abstract class BillDatabase : RoomDatabase() {
    abstract fun billDao(): BillDao

    companion object {
        @Volatile
        private var INSTANCE: BillDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN paymentUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE payments ADD COLUMN confirmationNumber TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN isVariableAmount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN amountMin REAL")
                db.execSQL("ALTER TABLE bills ADD COLUMN amountMax REAL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bill_payees " +
                        "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "billId INTEGER NOT NULL, name TEXT NOT NULL, " +
                        "sharePercent REAL NOT NULL, " +
                        "FOREIGN KEY(billId) REFERENCES bills(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_payees_billId ON bill_payees(billId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN attachmentName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE payments ADD COLUMN attachmentFile TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE payments ADD COLUMN attachmentMime TEXT NOT NULL DEFAULT 'application/octet-stream'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN currency TEXT NOT NULL DEFAULT 'USD'")
                db.execSQL("ALTER TABLE payments ADD COLUMN currency TEXT NOT NULL DEFAULT 'USD'")
            }
        }

        /**
         * Gives every bill a stored anchor date and every payment a canonical cycle key, then
         * rebuilds the payments table so a bill and cycle can only ever hold one payment.
         */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN anchorEpochDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE payments ADD COLUMN cycleKey TEXT NOT NULL DEFAULT ''")

                backfillAnchors(db)
                backfillCycleKeys(db)

                db.execSQL("DELETE FROM payments WHERE billId NOT IN (SELECT id FROM bills)")
                db.execSQL(
                    "DELETE FROM payments WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM payments GROUP BY billId, cycleKey)"
                )

                db.execSQL(
                    "CREATE TABLE payments_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "billId INTEGER NOT NULL, amount REAL NOT NULL, paidAt INTEGER NOT NULL, " +
                        "dueDate INTEGER NOT NULL, note TEXT NOT NULL, " +
                        "confirmationNumber TEXT NOT NULL, attachmentName TEXT NOT NULL, " +
                        "attachmentFile TEXT NOT NULL, attachmentMime TEXT NOT NULL, " +
                        "currency TEXT NOT NULL, cycleKey TEXT NOT NULL, " +
                        "FOREIGN KEY(billId) REFERENCES bills(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO payments_new (id, billId, amount, paidAt, dueDate, note, " +
                        "confirmationNumber, attachmentName, attachmentFile, attachmentMime, " +
                        "currency, cycleKey) SELECT id, billId, amount, paidAt, dueDate, note, " +
                        "confirmationNumber, attachmentName, attachmentFile, attachmentMime, " +
                        "currency, cycleKey FROM payments"
                )
                db.execSQL("DROP TABLE payments")
                db.execSQL("ALTER TABLE payments_new RENAME TO payments")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_payments_billId_cycleKey " +
                        "ON payments (billId, cycleKey)"
                )
            }

            private fun backfillAnchors(db: SupportSQLiteDatabase) {
                val zone = ZoneId.systemDefault()
                val rows = mutableListOf<Pair<Long, Long>>()
                db.query(
                    "SELECT id, dueDay, dueMonth, dueYear, recurrence, createdAt FROM bills"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val template = Bill(
                            id = id,
                            name = "",
                            amount = 0.0,
                            dueDay = cursor.getInt(1),
                            dueMonth = if (cursor.isNull(2)) null else cursor.getInt(2),
                            dueYear = if (cursor.isNull(3)) null else cursor.getInt(3),
                            recurrence = runCatching { Recurrence.valueOf(cursor.getString(4)) }
                                .getOrDefault(Recurrence.MONTHLY),
                            createdAt = cursor.getLong(5)
                        )
                        val reference = CycleEngine.toLocalDate(template.createdAt, zone)
                        rows.add(id to CycleEngine.deriveAnchor(template, reference).toEpochDay())
                    }
                }
                rows.forEach { (id, epochDay) ->
                    db.execSQL("UPDATE bills SET anchorEpochDay = ? WHERE id = ?", arrayOf<Any>(epochDay, id))
                }
            }

            private fun backfillCycleKeys(db: SupportSQLiteDatabase) {
                val zone = ZoneId.systemDefault()
                val rows = mutableListOf<Pair<Long, String>>()
                db.query("SELECT id, dueDate FROM payments").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val dueDate = cursor.getLong(1)
                        val date = if (dueDate > 0L) {
                            CycleEngine.toLocalDate(dueDate, zone)
                        } else {
                            LocalDate.ofEpochDay(0L)
                        }
                        rows.add(id to CycleEngine.cycleKey(date))
                    }
                }
                rows.forEach { (id, key) ->
                    db.execSQL("UPDATE payments SET cycleKey = ? WHERE id = ?", arrayOf<Any>(key, id))
                }
            }
        }

        internal val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7
        )

        /**
         * Opens the database once so a failure surfaces before the app tries to use it. The
         * database is never deleted to recover: an unreadable or newer schema is reported so the
         * user can update the app or restore a backup with their data still on disk.
         */
        fun checkHealth(context: Context): DatabaseHealth = try {
            getDatabase(context).openHelper.readableDatabase.version
            DatabaseHealth.Ready
        } catch (error: Throwable) {
            val file = context.getDatabasePath(DATABASE_NAME)
            DatabaseHealth.Unusable(
                reason = error.message ?: error::class.java.simpleName,
                databasePath = file.absolutePath
            )
        }

        private const val DATABASE_NAME = "billminder.db"

        /**
         * Drops the cached instance. Tests share a process, and a database held open across them
         * outlives the connections underneath it.
         */
        @androidx.annotation.VisibleForTesting
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun getDatabase(context: Context): BillDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BillDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
