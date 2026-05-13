package com.resqlink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.resqlink.app.data.local.dao.ContactDao
import com.resqlink.app.data.local.dao.EmergencyPacketDao
import com.resqlink.app.data.local.entity.ContactEntity
import com.resqlink.app.data.local.entity.EmergencyPacketEntity

@Database(
    entities = [EmergencyPacketEntity::class, ContactEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emergencyPacketDao(): EmergencyPacketDao
    abstract fun contactDao(): ContactDao
}
