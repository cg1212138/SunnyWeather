package com.sunnyweather.android.logic.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey
    val cacheKey: String,
    val weatherJson: String,
    val timestamp: Long
)
