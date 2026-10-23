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

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND timestamp >= :from ORDER BY timestamp ASC")
    fun observeFrom(from: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun getAllActive(): List<Transaction>

    @Query("SELECT * FROM transactions")
    suspend fun getAllRaw(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): Transaction?

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE isDeleted = 0 AND type = 'DEBIT' AND txCategory != 'Transfer' AND timestamp BETWEEN :start AND :end")
    suspend fun sumDebits(start: Long, end: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE isDeleted = 0 AND type = 'DEBIT' AND txCategory != 'Transfer' AND txCategory = :cat AND timestamp BETWEEN :start AND :end")
    suspend fun sumDebitsForCategory(cat: String, start: Long, end: Long): Double
}
