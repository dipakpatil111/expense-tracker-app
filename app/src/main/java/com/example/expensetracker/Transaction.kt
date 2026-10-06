package com.example.expensetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String,            // "CREDIT" ya "DEBIT"
    val mode: String,            // "UBER", "RAPIDO", "CASH", "ONLINE"
    val txCategory: String = "Other", // Name changed to avoid Kotlin conflict
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)