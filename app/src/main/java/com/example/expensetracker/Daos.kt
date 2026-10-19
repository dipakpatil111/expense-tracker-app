package com.example.expensetracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tx: Transaction): Long

    @Update
    suspend fun update(tx: Transaction)

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun observeRange(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun getAllActive(): List<Transaction>

    @Query("SELECT * FROM transactions")
    suspend fun getAllRaw(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): Transaction?

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE isDeleted = 0 AND type = 'DEBIT' AND timestamp BETWEEN :start AND :end")
    suspend fun sumDebits(start: Long, end: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE isDeleted = 0 AND type = 'DEBIT' AND txCategory = :cat AND timestamp BETWEEN :start AND :end")
    suspend fun sumDebitsForCategory(cat: String, start: Long, end: Long): Double
}

@Dao
interface LoanDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLoan(loan: LoanRecord): Long

    @Update
    suspend fun updateLoan(loan: LoanRecord)

    @Query("SELECT * FROM loans WHERE isDeleted = 0 ORDER BY isSettled ASC, startDate DESC")
    fun observeLoans(): Flow<List<LoanRecord>>

    @Query("SELECT * FROM loans")
    suspend fun getAllLoansRaw(): List<LoanRecord>

    @Query("SELECT * FROM loans WHERE uid = :uid LIMIT 1")
    suspend fun getLoanByUid(uid: String): LoanRecord?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPayment(p: InterestPayment): Long

    @Update
    suspend fun updatePayment(p: InterestPayment)

    @Query("SELECT * FROM interest_payments WHERE isDeleted = 0 ORDER BY paymentDate DESC")
    fun observeAllPayments(): Flow<List<InterestPayment>>

    @Query("SELECT * FROM interest_payments")
    suspend fun getAllPaymentsRaw(): List<InterestPayment>

    @Query("SELECT * FROM interest_payments WHERE uid = :uid LIMIT 1")
    suspend fun getPaymentByUid(uid: String): InterestPayment?

    @Query("SELECT * FROM interest_payments WHERE loanId = :loanId AND isDeleted = 0")
    suspend fun getPaymentsForLoan(loanId: Long): List<InterestPayment>
}

@Dao
interface RecurringDao {
    @Insert
    suspend fun insert(rule: RecurringRule): Long

    @Update
    suspend fun update(rule: RecurringRule)

    @Delete
    suspend fun delete(rule: RecurringRule)

    @Query("SELECT * FROM recurring_rules ORDER BY dayOfMonth")
    fun observeAll(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules WHERE isActive = 1")
    suspend fun getActive(): List<RecurringRule>

    @Query("SELECT * FROM recurring_rules")
    suspend fun getAll(): List<RecurringRule>
}
