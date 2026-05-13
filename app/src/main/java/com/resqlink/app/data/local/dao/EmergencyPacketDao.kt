package com.resqlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.resqlink.app.data.local.entity.EmergencyPacketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyPacketDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(packet: EmergencyPacketEntity): Long

    @Update
    suspend fun update(packet: EmergencyPacketEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM emergency_packets WHERE messageId = :messageId)")
    suspend fun exists(messageId: String): Boolean

    @Query("SELECT * FROM emergency_packets WHERE messageId = :messageId LIMIT 1")
    suspend fun getById(messageId: String): EmergencyPacketEntity?

    @Query("SELECT * FROM emergency_packets WHERE isDelivered = 0 ORDER BY timestamp DESC")
    suspend fun getUndeliveredPackets(): List<EmergencyPacketEntity>

    @Query("SELECT * FROM emergency_packets WHERE isRelayed = 0 AND isOwnMessage = 0 AND ttl > hopCount ORDER BY timestamp ASC")
    suspend fun getPacketsToRelay(): List<EmergencyPacketEntity>

    @Query("SELECT * FROM emergency_packets WHERE receiverId = :userId ORDER BY timestamp DESC")
    fun getReceivedPackets(userId: String): Flow<List<EmergencyPacketEntity>>

    @Query("SELECT * FROM emergency_packets WHERE senderId = :userId ORDER BY timestamp DESC")
    fun getSentPackets(userId: String): Flow<List<EmergencyPacketEntity>>

    @Query("SELECT * FROM emergency_packets WHERE isOwnMessage = 0 AND isRelayed = 0 ORDER BY timestamp DESC")
    fun getAllPackets(): Flow<List<EmergencyPacketEntity>>

    @Query("UPDATE emergency_packets SET isDelivered = 1 WHERE messageId = :messageId")
    suspend fun markDelivered(messageId: String)

    @Query("UPDATE emergency_packets SET isRelayed = 1 WHERE messageId = :messageId")
    suspend fun markRelayed(messageId: String)

    @Query("DELETE FROM emergency_packets WHERE timestamp < :expiryTime")
    suspend fun deleteExpiredPackets(expiryTime: Long)

    @Query("DELETE FROM emergency_packets WHERE messageId = :messageId")
    suspend fun deleteById(messageId: String)

    @Query("DELETE FROM emergency_packets WHERE isOwnMessage = 0 AND isRelayed = 0")
    suspend fun deleteAllAlerts()
}
