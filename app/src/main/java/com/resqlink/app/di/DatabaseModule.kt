package com.resqlink.app.di

import android.content.Context
import androidx.room.Room
import com.resqlink.app.data.local.AppDatabase
import com.resqlink.app.data.local.dao.ContactDao
import com.resqlink.app.data.local.dao.EmergencyPacketDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "resqlink_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideEmergencyPacketDao(database: AppDatabase): EmergencyPacketDao {
        return database.emergencyPacketDao()
    }

    @Provides
    fun provideContactDao(database: AppDatabase): ContactDao {
        return database.contactDao()
    }
}
