package com.example.expensetracker

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FirebaseSync {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val collection = firestore.collection("transactions")

    // Firebase me entry save ya update karna
    fun saveToFirebase(transaction: Transaction) {
        val data = hashMapOf(
            "id" to transaction.id,
            "amount" to transaction.amount,
            "type" to transaction.type,
            "mode" to transaction.mode,
            "description" to transaction.description,
            "timestamp" to transaction.timestamp
        )
        // Timestamp ko unique document ID bana kar save karte hain
        collection.document(transaction.timestamp.toString()).set(data)
    }

    // Firebase se entry delete karna
    fun deleteFromFirebase(timestamp: Long) {
        collection.document(timestamp.toString()).delete()
    }

    // App reinstall hone par saara purana data local Room DB me restore karna
    suspend fun syncFromCloud(dao: TransactionDao) {
        try {
            val snapshot = collection.get().await()
            for (doc in snapshot.documents) {
                val amount = doc.getDouble("amount") ?: continue
                val type = doc.getString("type") ?: "DEBIT"
                val mode = doc.getString("mode") ?: "CASH"
                val description = doc.getString("description") ?: ""
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                dao.insertTransaction(
                    Transaction(
                        amount = amount,
                        type = type,
                        mode = mode,
                        description = description,
                        timestamp = timestamp
                    )
                )
            }
        } catch (_: Exception) {
            // Offline hone par local DB chalti rahegi
        }
    }
}