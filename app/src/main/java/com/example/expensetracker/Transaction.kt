package com.example.expensetracker

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * FIX: timestamp is no longer unique. Every row has its own `uid` (used as the cloud document id),
 * so two entries in the same millisecond can never overwrite each other.
 * Deletes are soft (isDeleted) so they sync correctly across devices.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["smsHash"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val amount: Double,
    val type: String,            // CREDIT | DEBIT
    val mode: String,            // CASH | ONLINE | UBER | RAPIDO
    val txCategory: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "MANUAL",   // MANUAL | SMS | RECURRING
    val smsHash: String? = null,     // de-duplicates auto-detected SMS
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
