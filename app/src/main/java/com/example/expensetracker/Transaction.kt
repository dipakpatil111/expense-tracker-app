package com.example.expensetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String,       // "CREDIT" ya "DEBIT"
    val mode: String,       // "ONLINE", "CASH", "UBER", "RAPIDO"
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)