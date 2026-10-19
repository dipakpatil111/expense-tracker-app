package com.example.expensetracker

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * FIX: data now lives under users/{uid}/... and needs a Firebase login (see firestore.rules),
 * documents are keyed by the row `uid` (no more timestamp / autoincrement-id collisions),
 * sync is two-way with last-write-wins on `updatedAt`, and deletes are soft so they propagate.
 */
object FirebaseSync {
    private const val TAG = "FirebaseSync"
    private val auth get() = FirebaseAuth.getInstance()
    private val fs get() = FirebaseFirestore.getInstance()

    val currentEmail: String? get() = auth.currentUser?.email
    val isLoggedIn: Boolean get() = auth.currentUser != null

    private fun col(name: String): CollectionReference? {
        val uid = auth.currentUser?.uid ?: return null
        return fs.collection("users").document(uid).collection(name)
    }

    // ---------- auth ----------
    suspend fun signIn(email: String, pw: String): String? = try {
        auth.signInWithEmailAndPassword(email.trim(), pw).await(); null
    } catch (e: Exception) { e.message ?: "Sign-in failed" }

    suspend fun register(email: String, pw: String): String? = try {
        auth.createUserWithEmailAndPassword(email.trim(), pw).await(); null
    } catch (e: Exception) { e.message ?: "Registration failed" }

    fun signOut() = auth.signOut()

    // ---------- maps ----------
    private fun txMap(t: Transaction): Map<String, Any> = mapOf(
        "uid" to t.uid, "amount" to t.amount, "type" to t.type, "mode" to t.mode,
        "txCategory" to t.txCategory, "description" to t.description, "timestamp" to t.timestamp,
        "source" to t.source, "isDeleted" to t.isDeleted, "updatedAt" to t.updatedAt
    )

    private fun loanMap(l: LoanRecord): Map<String, Any> = mapOf(
        "uid" to l.uid, "personName" to l.personName, "amount" to l.amount, "originalAmount" to l.originalAmount,
        "type" to l.type, "hasInterest" to l.hasInterest, "monthlyRate" to l.monthlyRate, "startDate" to l.startDate,
        "isSettled" to l.isSettled, "note" to l.note, "isDeleted" to l.isDeleted, "updatedAt" to l.updatedAt
    )

    private fun payMap(p: InterestPayment): Map<String, Any> = mapOf(
        "uid" to p.uid, "loanUid" to p.loanUid, "amount" to p.amount, "paymentType" to p.paymentType,
        "paymentMode" to p.paymentMode, "paymentDate" to p.paymentDate, "note" to p.note,
        "isDeleted" to p.isDeleted, "updatedAt" to p.updatedAt
    )

    // ---------- single-row pushes (fire and forget; Firestore queues them while offline) ----------
    fun pushTransaction(t: Transaction) {
        col("transactions")?.document(t.uid)?.set(txMap(t))?.addOnFailureListener { Log.e(TAG, "tx push", it) }
    }

    fun pushLoan(l: LoanRecord) {
        col("loans")?.document(l.uid)?.set(loanMap(l))?.addOnFailureListener { Log.e(TAG, "loan push", it) }
    }

    fun pushPayment(p: InterestPayment) {
        col("interest_payments")?.document(p.uid)?.set(payMap(p))?.addOnFailureListener { Log.e(TAG, "pay push", it) }
    }

    // ---------- full two-way sync ----------
    suspend fun syncAll(txDao: TransactionDao, loanDao: LoanDao): String {
        val txCol = col("transactions") ?: return "Not signed in"
        val loanCol = col("loans") ?: return "Not signed in"
        val payCol = col("interest_payments") ?: return "Not signed in"
        return try {
            var pulled = 0
            var pushed = 0

            // transactions
            val cloudTx = HashMap<String, Long>()
            for (d in txCol.get().await().documents) {
                val uid = d.getString("uid") ?: d.id
                val upd = d.getLong("updatedAt") ?: 0L
                cloudTx[uid] = upd
                val amount = d.getDouble("amount") ?: continue
                val cloud = Transaction(
                    uid = uid, amount = amount, type = d.getString("type") ?: "DEBIT",
                    mode = d.getString("mode") ?: "CASH",
                    txCategory = d.getString("txCategory") ?: d.getString("category") ?: "Other",
                    description = d.getString("description") ?: "",
                    timestamp = d.getLong("timestamp") ?: upd, source = d.getString("source") ?: "MANUAL",
                    isDeleted = d.getBoolean("isDeleted") ?: false, updatedAt = upd
                )
                val local = txDao.getByUid(uid)
                if (local == null) { txDao.insertIgnore(cloud); pulled++ }
                else if (upd > local.updatedAt) { txDao.update(cloud.copy(id = local.id, smsHash = local.smsHash)); pulled++ }
            }
            for (t in txDao.getAllRaw()) {
                val cu = cloudTx[t.uid]
                if (cu == null || t.updatedAt > cu) { txCol.document(t.uid).set(txMap(t)); pushed++ }
            }

            // loans
            val cloudLoans = HashMap<String, Long>()
            for (d in loanCol.get().await().documents) {
                val uid = d.getString("uid") ?: d.id
                val upd = d.getLong("updatedAt") ?: 0L
                cloudLoans[uid] = upd
                val name = d.getString("personName") ?: continue
                val amount = d.getDouble("amount") ?: 0.0
                val cloud = LoanRecord(
                    uid = uid, personName = name, amount = amount, originalAmount = d.getDouble("originalAmount") ?: amount,
                    type = d.getString("type") ?: "TAKEN", hasInterest = d.getBoolean("hasInterest") ?: false,
                    monthlyRate = d.getDouble("monthlyRate") ?: 0.0, startDate = d.getLong("startDate") ?: upd,
                    isSettled = d.getBoolean("isSettled") ?: false, note = d.getString("note") ?: "",
                    isDeleted = d.getBoolean("isDeleted") ?: false, updatedAt = upd
                )
                val local = loanDao.getLoanByUid(uid)
                if (local == null) { loanDao.insertLoan(cloud); pulled++ }
                else if (upd > local.updatedAt) { loanDao.updateLoan(cloud.copy(id = local.id)); pulled++ }
            }
            for (l in loanDao.getAllLoansRaw()) {
                val cu = cloudLoans[l.uid]
                if (cu == null || l.updatedAt > cu) { loanCol.document(l.uid).set(loanMap(l)); pushed++ }
            }

            // payments (need local loan ids, so loans first)
            val loanIdByUid = loanDao.getAllLoansRaw().associate { it.uid to it.id }
            val cloudPay = HashMap<String, Long>()
            for (d in payCol.get().await().documents) {
                val uid = d.getString("uid") ?: d.id
                val upd = d.getLong("updatedAt") ?: 0L
                cloudPay[uid] = upd
                val loanUid = d.getString("loanUid") ?: continue
                val loanId = loanIdByUid[loanUid] ?: continue
                val cloud = InterestPayment(
                    uid = uid, loanId = loanId, loanUid = loanUid, amount = d.getDouble("amount") ?: 0.0,
                    paymentType = d.getString("paymentType") ?: "INTEREST", paymentMode = d.getString("paymentMode") ?: "ONLINE",
                    paymentDate = d.getLong("paymentDate") ?: upd, note = d.getString("note") ?: "",
                    isDeleted = d.getBoolean("isDeleted") ?: false, updatedAt = upd
                )
                val local = loanDao.getPaymentByUid(uid)
                if (local == null) { loanDao.insertPayment(cloud); pulled++ }
                else if (upd > local.updatedAt) { loanDao.updatePayment(cloud.copy(id = local.id)); pulled++ }
            }
            for (p in loanDao.getAllPaymentsRaw()) {
                val cu = cloudPay[p.uid]
                if (p.loanUid.isNotBlank() && (cu == null || p.updatedAt > cu)) { payCol.document(p.uid).set(payMap(p)); pushed++ }
            }
            "Synced ✓  ↓$pulled  ↑$pushed"
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            "Sync failed: ${e.message}"
        }
    }

    /**
     * One-time helper for data stored by the OLD app version in the open root collections.
     * Skips anything that already exists locally (same time+amount / same person+date+amount).
     * Run it BEFORE you deploy the new locked-down Firestore rules.
     */
    suspend fun importLegacy(txDao: TransactionDao, loanDao: LoanDao): String {
        return try {
            var n = 0
            val txKeys = txDao.getAllRaw().map { "${it.timestamp}|${it.amount}" }.toHashSet()
            for (d in fs.collection("transactions").get().await().documents) {
                val amount = d.getDouble("amount") ?: continue
                val ts = d.getLong("timestamp") ?: continue
                if (!txKeys.add("$ts|$amount")) continue
                txDao.insertIgnore(
                    Transaction(
                        uid = "legacy_tx_$ts", amount = amount, type = d.getString("type") ?: "DEBIT",
                        mode = d.getString("mode") ?: "CASH",
                        txCategory = d.getString("txCategory") ?: d.getString("category") ?: "Other",
                        description = d.getString("description") ?: "", timestamp = ts, updatedAt = ts
                    )
                ); n++
            }
            val loanKeys = loanDao.getAllLoansRaw().map { "${it.personName}|${it.startDate}|${it.originalAmount}" }.toHashSet()
            val legacyToLocal = HashMap<Long, Long>()
            for (d in fs.collection("loans").get().await().documents) {
                val legacyId = d.getLong("id") ?: continue
                val name = d.getString("personName") ?: continue
                val amount = d.getDouble("amount") ?: 0.0
                val orig = d.getDouble("originalAmount") ?: amount
                val start = d.getLong("startDate") ?: continue
                val existing = loanDao.getAllLoansRaw().firstOrNull { it.personName == name && it.startDate == start && it.originalAmount == orig }
                if (existing != null) { legacyToLocal[legacyId] = existing.id; continue }
                loanKeys.add("$name|$start|$orig")
                val id = loanDao.insertLoan(
                    LoanRecord(
                        uid = "legacy_loan_$legacyId", personName = name, amount = amount, originalAmount = orig,
                        type = d.getString("type") ?: "TAKEN", hasInterest = d.getBoolean("hasInterest") ?: false,
                        monthlyRate = d.getDouble("monthlyRate") ?: 0.0, startDate = start,
                        isSettled = d.getBoolean("isSettled") ?: false, note = d.getString("note") ?: "", updatedAt = start
                    )
                )
                if (id > 0) { legacyToLocal[legacyId] = id; n++ }
            }
            val payKeys = loanDao.getAllPaymentsRaw().map { "${it.loanId}|${it.paymentDate}|${it.amount}" }.toHashSet()
            val loansById = loanDao.getAllLoansRaw().associateBy { it.id }
            for (d in fs.collection("interest_payments").get().await().documents) {
                val legacyLoan = d.getLong("loanId") ?: continue
                val localLoanId = legacyToLocal[legacyLoan] ?: continue
                val date = d.getLong("paymentDate") ?: continue
                val amount = d.getDouble("amount") ?: 0.0
                if (!payKeys.add("$localLoanId|$date|$amount")) continue
                loanDao.insertPayment(
                    InterestPayment(
                        uid = "legacy_pay_${d.id}", loanId = localLoanId, loanUid = loansById[localLoanId]?.uid ?: "",
                        amount = amount, paymentType = d.getString("paymentType") ?: "INTEREST",
                        paymentMode = d.getString("paymentMode") ?: "ONLINE", paymentDate = date,
                        note = d.getString("note") ?: "", updatedAt = date
                    )
                ); n++
            }
            "Imported $n old records"
        } catch (e: Exception) {
            "Import failed: ${e.message}"
        }
    }
}
