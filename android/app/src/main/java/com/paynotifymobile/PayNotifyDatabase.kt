package com.paynotifymobile

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [QueuedNotificationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PayNotifyDatabase : RoomDatabase() {

    abstract fun queuedDao(): QueuedNotificationDao

    companion object {
        @Volatile private var INSTANCE: PayNotifyDatabase? = null

        fun get(ctx: Context): PayNotifyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    PayNotifyDatabase::class.java,
                    "paynotify.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
