package com.example.expensetracker

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "loans", indices = [Index(value = ["uid"], unique = true)])
data class LoanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val personName: String,
    val amount: Double,              // current principal balance
    val originalAmount: Double,
    val type: String,                // TAKEN | GIVEN
    val hasInterest: Boolean = false,
    val monthlyRate: Double = 0.0,   // % per month
    val startDate: Long = System.currentTimeMillis(),
    val isSettled: Boolean = false,
    val note: String = "",
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "interest_payments",
    indices = [Index(value = ["uid"], unique = true), Index(value = ["loanId"])]
)
data class InterestPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val loanId: Long,
    val loanUid: String = "",
    val amount: Double,
    val paymentType: String,         // INTEREST | PRINCIPAL
    val paymentMode: String = "ONLINE",
    val paymentDate: Long = System.currentTimeMillis(),
    val note: String = "",
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recurring_rules", indices = [Index(value = ["uid"], unique = true)])
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val type: String,
    val mode: String,
    val txCategory: String,
    val dayOfMonth: Int,
    val isActive: Boolean = true,
    val lastRunMonth: String = ""    // yyyy-MM of the last month that was generated
)
