package com.example.expensetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

object Notifier {
    private const val CHANNEL = "expense_alert_channel"

    fun show(ctx: Context, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Expense Alerts", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify((System.nanoTime() and 0x7fffffff).toInt(), n)
    }
}

/**
 * FIX/NEW: the old receiver notified on every debit and never compared anything with the limit.
 * Now we alert once at 80% and once at 100% - for the overall monthly budget and for each category budget.
 */
object BudgetChecker {
    suspend fun check(ctx: Context, db: AppDatabase, prefs: AppPrefs, tx: Transaction) {
        if (tx.type != "DEBIT") return
        val (start, end) = monthBoundsOf(tx.timestamp)
        val mk = monthKey(tx.timestamp)

        val total = prefs.totalBudget.toDouble()
        if (total > 0) alertIf(ctx, prefs, "tot_$mk", db.transactionDao().sumDebits(start, end), total, "Monthly budget")

        val catLimit = prefs.categoryBudget(tx.txCategory).toDouble()
        if (catLimit > 0) {
            val spent = db.transactionDao().sumDebitsForCategory(tx.txCategory, start, end)
            alertIf(ctx, prefs, "cat_${tx.txCategory}_$mk", spent, catLimit, "${tx.txCategory} budget")
        }
    }

    private fun alertIf(ctx: Context, prefs: AppPrefs, id: String, spent: Double, limit: Double, label: String) {
        val ratio = spent / limit
        val level = if (ratio >= 1.0) 100 else if (ratio >= 0.8) 80 else return
        if (!prefs.shouldAlert("alert_${id}_$level")) return
        if (level == 100) Notifier.show(ctx, "⚠️ $label exceeded", "Spent ${money(spent)} of ${money(limit)}")
        else Notifier.show(ctx, "$label: 80% used", "Spent ${money(spent)} of ${money(limit)}")
    }
}

/** Interest reminders for loans (runs from the daily worker). */
object LoanReminder {
    suspend fun run(ctx: Context, db: AppDatabase, prefs: AppPrefs) {
        val now = System.currentTimeMillis()
        val mk = monthKey(now)
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val loans = db.loanDao().getAllLoansRaw().filter { !it.isDeleted && !it.isSettled && it.hasInterest }
        for (l in loans) {
            val startDay = Calendar.getInstance().apply { timeInMillis = l.startDate }.get(Calendar.DAY_OF_MONTH)
            if (today < minOf(startDay, lastDay)) continue
            val s = calcInterest(l, db.loanDao().getPaymentsForLoan(l.id), now)
            if (s.pending < 1.0) continue
            if (!prefs.shouldAlert("loan_${l.uid}_$mk")) continue
            val title = if (l.type == "TAKEN") "Interest dena hai: ${l.personName}" else "Interest lena hai: ${l.personName}"
            Notifier.show(ctx, title, "Pending ${money(s.pending)} @ ${l.monthlyRate}% / month")
        }
    }
}
