package com.paynotifymobile

import android.content.Context

class QueueRepository(ctx: Context) {
    private val dao = PayNotifyDatabase.get(ctx).queuedDao()

    suspend fun enqueue(entity: QueuedNotificationEntity): Long {
        return dao.insertIgnore(entity) // IGNORE si externalRef ya existe
    }

    suspend fun getPendingBatch(now: Long, limit: Int): List<QueuedNotificationEntity> {
        return dao.getPendingBatch(now = now, limit = limit)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun updateRetry(id: Long, attempts: Int, lastError: String?, nextAttemptAt: Long) {
        dao.updateRetry(id, attempts, lastError, nextAttemptAt)
    }

    suspend fun markAuthError(id: Long, lastError: String?) {
        dao.markStatus(id, QueuedNotificationEntity.Status.AUTH_ERROR, lastError)
    }
}
