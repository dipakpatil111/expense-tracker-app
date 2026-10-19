package com.example.expensetracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * FIX: no more fallbackToDestructiveMigration() for real data. v3 -> v4 is a proper migration that
 * keeps every transaction / loan / payment. Only the ancient v1/v2 schemas may be wiped.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val sql = listOf(
            // transactions
            "DROP INDEX IF EXISTS index_transactions_timestamp",
            "ALTER TABLE transactions ADD COLUMN uid TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'",
            "ALTER TABLE transactions ADD COLUMN smsHash TEXT",
            "ALTER TABLE transactions ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
            "UPDATE transactions SET uid = lower(hex(randomblob(16))), updatedAt = timestamp",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_uid ON transactions(uid)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_smsHash ON transactions(smsHash)",
            "CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions(timestamp)",
            // loans
            "ALTER TABLE loans ADD COLUMN uid TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE loans ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE loans ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
            "UPDATE loans SET uid = lower(hex(randomblob(16))), updatedAt = startDate",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_loans_uid ON loans(uid)",
            // payments
            "ALTER TABLE interest_payments ADD COLUMN uid TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE interest_payments ADD COLUMN loanUid TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE interest_payments ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE interest_payments ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
            "UPDATE interest_payments SET uid = lower(hex(randomblob(16))), updatedAt = paymentDate, " +
                "loanUid = COALESCE((SELECT l.uid FROM loans l WHERE l.id = interest_payments.loanId), '')",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_interest_payments_uid ON interest_payments(uid)",
            "CREATE INDEX IF NOT EXISTS index_interest_payments_loanId ON interest_payments(loanId)",
            // recurring
            "CREATE TABLE IF NOT EXISTS recurring_rules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, uid TEXT NOT NULL, title TEXT NOT NULL, " +
                "amount REAL NOT NULL, type TEXT NOT NULL, mode TEXT NOT NULL, txCategory TEXT NOT NULL, " +
                "dayOfMonth INTEGER NOT NULL, isActive INTEGER NOT NULL, lastRunMonth TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_recurring_rules_uid ON recurring_rules(uid)"
        )
        sql.forEach { db.execSQL(it) }
    }
}

@Database(
    entities = [Transaction::class, LoanRecord::class, InterestPayment::class, RecurringRule::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun loanDao(): LoanDao
    abstract fun recurringDao(): RecurringDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
