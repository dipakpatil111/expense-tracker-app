package com.example.expensetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.messageBody ?: continue
                parseAndSaveSms(context, body)
            }
        }
    }

    private fun parseAndSaveSms(context: Context, body: String) {
        val lowerBody = body.lowercase()

        val isDebit = lowerBody.contains("debited") || lowerBody.contains("sent") || lowerBody.contains("paid")
        val isCredit = lowerBody.contains("credited") || lowerBody.contains("received") || lowerBody.contains("added")

        if (!isDebit && !isCredit) return

        val pattern = Pattern.compile("(?i)(?:inr|rs\\.?)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
        val matcher = pattern.matcher(body)

        if (matcher.find()) {
            val amountString = matcher.group(1)?.replace(",", "") ?: return
            val amount = amountString.toDoubleOrNull() ?: return
            val type = if (isDebit) "DEBIT" else "CREDIT"

            val db = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                // 1. Transaction object create karein
                val newTx = Transaction(
                    amount = amount,
                    type = type,
                    mode = "ONLINE", // Bank SMS automatically ONLINE mode me jayega
                    description = if (body.length > 60) body.take(60) + "..." else body
                )

                // 2. Local SQLite (Room) Database me save karein
                db.transactionDao().insertTransaction(newTx)

                // 3. Firebase Cloud Firestore me sync karein
                FirebaseSync.saveToFirebase(newTx)

                // 4. Agar Debit hua toh budget warning alert bhejein
                if (type == "DEBIT") {
                    checkAndNotifyBudget(context, amount)
                }
            }
        }
    }

    private fun checkAndNotifyBudget(context: Context, lastAmount: Double) {
        val sharedPref = context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val budgetLimit = sharedPref.getFloat("budget_limit", 10000f)

        val channelId = "expense_alert_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Expense Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Online Bank Transaction Detected")
            .setContentText("₹$lastAmount debited. Aapka monthly target limit: ₹$budgetLimit")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}