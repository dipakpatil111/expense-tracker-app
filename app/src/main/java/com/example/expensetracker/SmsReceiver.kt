package com.example.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val prefs = AppPrefs(context)
        if (!prefs.smsTracking) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // FIX: a long SMS arrives in several parts - join them per sender before parsing.
        val grouped = messages.groupBy { it.originatingAddress ?: "" }

        // FIX: goAsync() keeps the process alive until the DB write finishes.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                for ((sender, parts) in grouped) {
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    val smsTs = parts.first().timestampMillis
                    val parsed = SmsParser.parse(body, sender) ?: continue
                    val tx = Transaction(
                        amount = parsed.amount,
                        type = parsed.type,
                        mode = "ONLINE",
                        txCategory = parsed.category,
                        description = parsed.description,
                        timestamp = if (smsTs > 0) smsTs else System.currentTimeMillis(),
                        source = "SMS",
                        smsHash = SmsParser.hash(sender, body, smsTs)
                    )
                    val id = db.transactionDao().insertIgnore(tx)
                    if (id <= 0) continue // duplicate SMS
                    val saved = tx.copy(id = id)
                    FirebaseSync.pushTransaction(saved)
                    val sign = if (saved.type == "DEBIT") "debited" else "credited"
                    Notifier.show(context, "Transaction detected", "${money(saved.amount)} $sign • ${saved.txCategory}")
                    BudgetChecker.check(context, db, prefs, saved)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
