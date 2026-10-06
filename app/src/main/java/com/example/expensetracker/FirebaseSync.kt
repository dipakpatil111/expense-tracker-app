package com.example.expensetracker

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FirebaseSync {
    private const val TAG = "FirebaseSync"
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val txCollection = firestore.collection("transactions")
    private val loanCollection = firestore.collection("loans")
    private val paymentCollection = firestore.collection("interest_payments")

    // ================= 1. TRANSACTIONS SYNC =================
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
        txCollection.document(transaction.timestamp.toString())
            .set(data)
            .addOnSuccessListener { Log.d(TAG, "Transaction synced: ${transaction.description}") }
            .addOnFailureListener { e -> Log.e(TAG, "Error syncing transaction", e) }
    }

    fun deleteFromFirebase(timestamp: Long) {
        txCollection.document(timestamp.toString()).delete()
    }

    // ================= 2. LOANS / KHATA SYNC =================
    fun saveLoanToFirebase(loan: LoanRecord) {
        val data = hashMapOf(
            "id" to loan.id,
            "personName" to loan.personName,
            "amount" to loan.amount,
            "originalAmount" to loan.originalAmount,
            "type" to loan.type,
            "hasInterest" to loan.hasInterest,
            "monthlyRate" to loan.monthlyRate,
            "startDate" to loan.startDate,
            "isSettled" to loan.isSettled,
            "note" to loan.note
        )
        loanCollection.document(loan.id.toString())
            .set(data)
            .addOnSuccessListener { Log.d(TAG, "Loan synced: ${loan.personName}") }
            .addOnFailureListener { e -> Log.e(TAG, "Error syncing loan", e) }
    }

    fun deleteLoanFromFirebase(loanId: Long) {
        loanCollection.document(loanId.toString()).delete()
    }

    // ================= 3. INTEREST / PRINCIPAL PAYMENTS SYNC =================
    fun savePaymentToFirebase(payment: InterestPayment) {
        val data = hashMapOf(
            "id" to payment.id,
            "loanId" to payment.loanId,
            "amount" to payment.amount,
            "paymentType" to payment.paymentType,
            "paymentMode" to payment.paymentMode,
            "paymentDate" to payment.paymentDate,
            "note" to payment.note
        )
        paymentCollection.document(payment.paymentDate.toString())
            .set(data)
            .addOnSuccessListener { Log.d(TAG, "Payment synced: ${payment.amount}") }
            .addOnFailureListener { e -> Log.e(TAG, "Error syncing payment", e) }
    }

    // ================= 4. FULL CLOUD RESTORE / SYNC =================
    suspend fun syncFromCloud(transactionDao: TransactionDao, loanDao: LoanDao) {
        try {
            // Restore Transactions
            val txSnapshot = txCollection.get().await()
            for (doc in txSnapshot.documents) {
                val amount = doc.getDouble("amount") ?: continue
                val type = doc.getString("type") ?: "DEBIT"
                val mode = doc.getString("mode") ?: "CASH"
                val cat = doc.getString("txCategory") ?: doc.getString("category") ?: "Other"
                val description = doc.getString("description") ?: ""
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                transactionDao.insertTransaction(
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

            // Restore Loans
            val loanSnapshot = loanCollection.get().await()
            for (doc in loanSnapshot.documents) {
                val name = doc.getString("personName") ?: continue
                val amount = doc.getDouble("amount") ?: 0.0
                val originalAmount = doc.getDouble("originalAmount") ?: amount
                val type = doc.getString("type") ?: "TAKEN"
                val hasInterest = doc.getBoolean("hasInterest") ?: false
                val monthlyRate = doc.getDouble("monthlyRate") ?: 0.0
                val startDate = doc.getLong("startDate") ?: System.currentTimeMillis()
                val isSettled = doc.getBoolean("isSettled") ?: false
                val note = doc.getString("note") ?: ""

                loanDao.insertLoan(
                    LoanRecord(
                        id = doc.getLong("id") ?: 0L,
                        personName = name,
                        amount = amount,
                        originalAmount = originalAmount,
                        type = type,
                        hasInterest = hasInterest,
                        monthlyRate = monthlyRate,
                        startDate = startDate,
                        isSettled = isSettled,
                        note = note
                    )
                )
            }

            // Restore Payments
            val paySnapshot = paymentCollection.get().await()
            for (doc in paySnapshot.documents) {
                val loanId = doc.getLong("loanId") ?: continue
                val amount = doc.getDouble("amount") ?: 0.0
                val paymentType = doc.getString("paymentType") ?: "INTEREST"
                val paymentMode = doc.getString("paymentMode") ?: "ONLINE"
                val paymentDate = doc.getLong("paymentDate") ?: System.currentTimeMillis()
                val note = doc.getString("note") ?: ""

                loanDao.insertInterestPayment(
                    InterestPayment(
                        id = doc.getLong("id") ?: 0L,
                        loanId = loanId,
                        amount = amount,
                        paymentType = paymentType,
                        paymentMode = paymentMode,
                        paymentDate = paymentDate,
                        note = note
                    )
                )
            }
            Log.d(TAG, "All cloud data synced successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore cloud data", e)
        }
    }
}