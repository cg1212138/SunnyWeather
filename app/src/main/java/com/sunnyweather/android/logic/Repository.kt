package com.sunnyweather.android.logic

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sunnyweather.android.SunnyWeatherApplication
import com.sunnyweather.android.logic.dao.PlaceDao
import com.sunnyweather.android.logic.dao.WeatherCacheDatabase
import com.sunnyweather.android.logic.model.Place
import com.sunnyweather.android.logic.model.Weather
import com.sunnyweather.android.logic.model.WeatherCacheEntity
import com.sunnyweather.android.logic.network.SunnyWeatherNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.CoroutineContext

object Repository {

    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        .create()

    fun searchPlaces(query: String): LiveData<Result<List<Place>>> {
        return liveData(Dispatchers.IO) {
            val result = try {
                val placesResponse = SunnyWeatherNetwork.searchPlaces(query)
                if (placesResponse.status == "ok") {
                    Result.success(placesResponse.places)
                } else {
                    Result.failure(RuntimeException("response status is ${placesResponse.status}"))
                }
            } catch (e: Exception) {
                Result.failure<List<Place>>(e)
            }
            emit(result)
        }
    }

    fun refreshWeather(lng: String, lat: String): LiveData<Result<Weather>> {
        return liveData(Dispatchers.IO) {
            val result = try {
                coroutineScope {
                    val deferredRealtime = async {
                        SunnyWeatherNetwork.getRealtimeWeather(lng, lat)
                    }
                    val deferredDaily = async {
                        SunnyWeatherNetwork.getDailyWeather(lng, lat)
                    }
                    val realtimeResponse = deferredRealtime.await()
                    val dailyResponse = deferredDaily.await()
                    if (realtimeResponse.status == "ok" && dailyResponse.status == "ok") {
                        val weather = Weather(
                            realtimeResponse.result.realtime,
                            dailyResponse.result.daily
                        )
                        // 网络请求成功，写入 Room 缓存
                        val cacheEntity = WeatherCacheEntity(
                            cacheKey = "${lng},${lat}",
                            weatherJson = gson.toJson(weather),
                            timestamp = System.currentTimeMillis()
                        )
                        val db = WeatherCacheDatabase.getInstance(SunnyWeatherApplication.context)
                        db.weatherCacheDao().insertWeatherCache(cacheEntity)

                        Result.success(weather)
                    } else {
                        Result.failure(
                            RuntimeException(
                                "realtime response status is ${realtimeResponse.status}" +
                                        "daily response status is ${dailyResponse.status}"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // 网络请求失败，尝试从 Room 缓存读取
                try {
                    val db = WeatherCacheDatabase.getInstance(SunnyWeatherApplication.context)
                    val cached = db.weatherCacheDao().getWeatherCache("${lng},${lat}")
                    if (cached != null) {
                        val weather = gson.fromJson(cached.weatherJson, Weather::class.java)
                        Result.success(weather)
                    } else {
                        Result.failure<Weather>(e)
                    }
                } catch (cacheException: Exception) {
                    Result.failure<Weather>(e)
                }
            }
            emit(result)
        }
    }

    fun savePlace(place: Place) = PlaceDao.savePlace(place)

    fun getSavedPlace() = PlaceDao.getSavedPlace()

    fun isPlaceSaved() = PlaceDao.isPlaceSaved()
}
