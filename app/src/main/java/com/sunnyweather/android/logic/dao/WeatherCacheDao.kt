package com.sunnyweather.android.logic.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sunnyweather.android.logic.model.WeatherCacheEntity

@Dao
interface WeatherCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherCache(cache: WeatherCacheEntity)

    @Query("SELECT * FROM weather_cache WHERE cacheKey = :key")
    suspend fun getWeatherCache(key: String): WeatherCacheEntity?

    @Query("DELETE FROM weather_cache WHERE cacheKey = :key")
    suspend fun deleteWeatherCache(key: String): Int

    @Query("DELETE FROM weather_cache WHERE timestamp < :before")
    suspend fun deleteExpiredCache(before: Long): Int
}
