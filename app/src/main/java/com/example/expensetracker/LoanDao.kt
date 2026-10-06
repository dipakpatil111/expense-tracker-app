package com.example.expensetracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanRecord): Long

    @Update
    suspend fun updateLoan(loan: LoanRecord)

    @Delete
    suspend fun deleteLoan(loan: LoanRecord)

    @Query("SELECT * FROM loans ORDER BY isSettled ASC, startDate DESC")
    fun getAllLoans(): Flow<List<LoanRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterestPayment(payment: InterestPayment)

    @Query("SELECT * FROM interest_payments WHERE loanId = :loanId ORDER BY paymentDate DESC")
    fun getInterestPayments(loanId: Long): Flow<List<InterestPayment>>

    @Query("DELETE FROM interest_payments WHERE loanId = :loanId")
    suspend fun deletePaymentsForLoan(loanId: Long)
}