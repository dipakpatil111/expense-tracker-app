package com.example.expensetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loans")
data class LoanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personName: String,
    val amount: Double,               // Mool-dhan / Current Remaining Balance
    val originalAmount: Double,       // Starting amount (e.g. ₹5,000)
    val type: String,                 // "TAKEN" (Maine Liye) ya "GIVEN" (Maine Diye)
    val hasInterest: Boolean = false, // Byaaj switch
    val monthlyRate: Double = 0.0,    // Monthly % rate (e.g. 2.0%)
    val startDate: Long = System.currentTimeMillis(),
    val isSettled: Boolean = false,   // Pura hisaab khatam hua ya nahi
    val note: String = ""
)

@Entity(tableName = "interest_payments")
data class InterestPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loanId: Long,
    val amount: Double,               // Paid amount
    val paymentType: String,          // "INTEREST" (Sirf Byaaj) ya "PRINCIPAL" (Mool-dhan wapsi)
    val paymentMode: String = "ONLINE", // "ONLINE" ya "CASH"
    val paymentDate: Long = System.currentTimeMillis(),
    val note: String = ""
)