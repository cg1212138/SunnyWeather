package com.sunnyweather.android.logic.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sunnyweather.android.logic.model.WeatherCacheEntity

@Database(entities = [WeatherCacheEntity::class], version = 1, exportSchema = false)
abstract class WeatherCacheDatabase : RoomDatabase() {

    abstract fun weatherCacheDao(): WeatherCacheDao

    companion object {
        @Volatile
        private var INSTANCE: WeatherCacheDatabase? = null

        fun getInstance(context: Context): WeatherCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WeatherCacheDatabase::class.java,
                    "sunny_weather_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
