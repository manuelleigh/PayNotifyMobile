package com.paynotifymobile

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "queued_notifications",
    indices = [
        Index(value = ["externalRef"], unique = true),
        Index(value = ["status"]),
        Index(value = ["nextAttemptAt"])
    ]
)
data class QueuedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val appPackage: String,
    val title: String,
    val text: String,
    val receivedAt: String,
    val deviceId: String,
    val externalRef: String,

    val createdAt: Long = System.currentTimeMillis(),

    val status: Int = Status.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,

    // Para controlar reintentos sin martillar
    val nextAttemptAt: Long = System.currentTimeMillis()
) {
    object Status {
        const val PENDING = 0
        const val AUTH_ERROR = 1
    }
}
