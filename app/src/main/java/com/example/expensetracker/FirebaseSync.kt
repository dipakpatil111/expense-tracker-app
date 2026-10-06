package com.example.expensetracker

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FirebaseSync {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val collection = firestore.collection("transactions")

    fun saveToFirebase(transaction: Transaction) {
        val data = hashMapOf(
            "id" to transaction.id,
            "amount" to transaction.amount,
            "type" to transaction.type,
            "mode" to transaction.mode,
            "txCategory" to transaction.txCategory,
            "description" to transaction.description,
            "timestamp" to transaction.timestamp
        )
        collection.document(transaction.timestamp.toString()).set(data)
    }

    fun deleteFromFirebase(timestamp: Long) {
        collection.document(timestamp.toString()).delete()
    }

    suspend fun syncFromCloud(dao: TransactionDao) {
        try {
            val snapshot = collection.get().await()
            for (doc in snapshot.documents) {
                val amount = doc.getDouble("amount") ?: continue
                val type = doc.getString("type") ?: "DEBIT"
                val mode = doc.getString("mode") ?: "CASH"
                val cat = doc.getString("txCategory") ?: doc.getString("category") ?: "Other"
                val description = doc.getString("description") ?: ""
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                dao.insertTransaction(
                    Transaction(
                        amount = amount,
                        type = type,
                        mode = mode,
                        txCategory = cat,
                        description = description,
                        timestamp = timestamp
                    )
                )
            }
        } catch (_: Exception) { }
    }
}