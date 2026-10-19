package com.example.expensetracker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** NEW: generates due recurring entries (rent, EMI, subscriptions...). Idempotent via a deterministic uid. */
object RecurringRunner {
    suspend fun run(ctx: Context, db: AppDatabase) {
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val now = System.currentTimeMillis()
        for (rule in db.recurringDao().getActive()) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (rule.lastRunMonth.isNotBlank()) {
                val last = runCatching { keyFmt.parse(rule.lastRunMonth) }.getOrNull()
                if (last != null) {
                    val next = Calendar.getInstance().apply { time = last; add(Calendar.MONTH, 1) }
                    val floor = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis; add(Calendar.MONTH, -11) }
                    cal.timeInMillis = maxOf(next.timeInMillis, floor.timeInMillis)
                }
            }
            var lastDone = rule.lastRunMonth
            while (cal.timeInMillis <= now) {
                val due = Calendar.getInstance().apply {
                    timeInMillis = cal.timeInMillis
                    set(Calendar.DAY_OF_MONTH, minOf(rule.dayOfMonth, getActualMaximum(Calendar.DAY_OF_MONTH)))
                    set(Calendar.HOUR_OF_DAY, 9)
                }
                if (due.timeInMillis > now) break
                val mk = keyFmt.format(cal.time)
                val tx = Transaction(
                    uid = "rec_${rule.uid}_$mk",
                    amount = rule.amount, type = rule.type, mode = rule.mode,
                    txCategory = rule.txCategory, description = rule.title,
                    timestamp = due.timeInMillis, source = "RECURRING"
                )
                val id = db.transactionDao().insertIgnore(tx)
                if (id > 0) FirebaseSync.pushTransaction(tx.copy(id = id))
                lastDone = mk
                cal.add(Calendar.MONTH, 1)
            }
            if (lastDone != rule.lastRunMonth) db.recurringDao().update(rule.copy(lastRunMonth = lastDone))
        }
    }
}

class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        RecurringRunner.run(applicationContext, db)
        LoanReminder.run(applicationContext, db, AppPrefs(applicationContext))
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("daily_worker", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
