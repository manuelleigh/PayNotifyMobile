package com.paynotifymobile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface QueuedNotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: QueuedNotificationEntity): Long

    @Query("""
        SELECT * FROM queued_notifications
        WHERE status = :status
          AND nextAttemptAt <= :now
        ORDER BY createdAt ASC
        LIMIT :limit
    """)
    suspend fun getPendingBatch(
        status: Int = QueuedNotificationEntity.Status.PENDING,
        now: Long,
        limit: Int
    ): List<QueuedNotificationEntity>

    @Query("DELETE FROM queued_notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        UPDATE queued_notifications
        SET attempts = :attempts,
            lastError = :lastError,
            nextAttemptAt = :nextAttemptAt
        WHERE id = :id
    """)
    suspend fun updateRetry(
        id: Long,
        attempts: Int,
        lastError: String?,
        nextAttemptAt: Long
    )

    @Query("""
        UPDATE queued_notifications
        SET status = :status,
            lastError = :lastError
        WHERE id = :id
    """)
    suspend fun markStatus(
        id: Long,
        status: Int,
        lastError: String?
    )

    @Query("SELECT COUNT(*) FROM queued_notifications WHERE status = :status")
    suspend fun countByStatus(status: Int): Int
}
