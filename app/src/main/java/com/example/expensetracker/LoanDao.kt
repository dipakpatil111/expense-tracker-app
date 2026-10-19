package com.example.expensetracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
