package com.example.expensetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loans")
data class LoanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personName: String,
    val amount: Double,
    val originalAmount: Double,
    val type: String,
    val hasInterest: Boolean = false,
    val monthlyRate: Double = 0.0,
    val startDate: Long = System.currentTimeMillis(),
    val isSettled: Boolean = false,
    val note: String = ""
)

@Entity(tableName = "interest_payments")
data class InterestPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loanId: Long,
    val amount: Double,
    val paymentType: String,
    val paymentMode: String = "ONLINE",
    val paymentDate: Long = System.currentTimeMillis(),
    val note: String = ""
)