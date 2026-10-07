package com.example.expensetracker

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["timestamp"], unique = true)] // Timestamp kabhi repeat nahi hoga
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String,          // CREDIT ya DEBIT
    val mode: String,          // CASH, ONLINE, UBER, RAPIDO
    val txCategory: String,    // Petrol, Food, Other, etc.
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)