# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作提供指引。

## 技术栈

- **语言**：Kotlin
- **UI**：XML 布局（View 体系），未使用 Jetpack Compose
- **架构**：MVVM（ViewModel + LiveData + Repository）
- **网络**：Retrofit 2 + OkHttp（Gson 转换器）
- **异步**：Kotlin 协程
- **数据库**：Room（天气缓存）
- **持久化**：SharedPreferences（通过 Gson 序列化保存最后选择的城市）
- **依赖注入**：手动（无 Hilt/Koin —— 通过 Kotlin `object` 实现单例）
- **注解处理**：KSP（Room 编译器）

## 构建与运行

```bash
# 构建 debug APK
./gradlew assembleDebug

# 构建 release APK
./gradlew assembleRelease

# 安装到连接的设备/模拟器
./gradlew installDebug

# 运行测试
./gradlew test                    # 单元测试
./gradlew connectedAndroidTest    # 插桩测试
```

## 项目架构

### 包结构

```
com.sunnyweather.android/
├── logic/                          # 业务逻辑与数据层
│   ├── Repository.kt               # 数据仓库（单例）
│   ├── dao/
│   │   ├── PlaceDao.kt             # SharedPreferences 持久化最后选择的城市
│   │   ├── WeatherCacheDao.kt      # Room DAO —— 天气缓存增删查
│   │   └── WeatherCacheDatabase.kt # Room 数据库定义 + 单例
│   ├── model/
│   │   ├── PlaceResponse.kt        # 城市搜索 API 响应
│   │   ├── RealtimeResponse.kt     # 实时天气 API 响应
│   │   ├── DailyResponse.kt        # 7 日天气预报响应
│   │   ├── Weather.kt              # 合并天气模型（realtime + daily）
│   │   ├── WeatherCacheEntity.kt   # Room 实体（cacheKey, weatherJson, timestamp）
│   │   └── Sky.kt                  # Skycon 字符串 → 中文、图标、背景
│   └── network/
│       ├── ServiceCreator.kt       # Retrofit 单例构建器
│       ├── SunnyWeatherNetwork.kt  # 网络层外观（suspend 封装）
│       ├── PlaceService.kt         # 城市搜索 Retrofit 接口
│       ├── WeatherService.kt       # 天气 Retrofit 接口
│       └── RateLimitingInterceptor.kt # QPS=1 限流拦截器
├── ui/
│   ├── place/
│   │   ├── PlaceFragment.kt        # 搜索 UI
│   │   ├── PlaceViewModel.kt       # ViewModel
│   │   └── PlaceAdapter.kt         # RecyclerView 适配器
│   └── weather/
│       ├── WeatherActivity.kt      # 天气主界面
│       └── WeatherViewModel.kt     # ViewModel
├── MainActivity.kt                 # 入口 Activity（存根）
└── SunnyWeatherApplication.kt      # Application（Context + API TOKEN）
```

### 数据流

```
UI（Fragment/Activity）
  → ViewModel（LiveData, switchMap）
    → Repository（object，基于协程）
      → SunnyWeatherNetwork（suspend 函数）
        → ServiceCreator（Retrofit 单例）
          → PlaceService / WeatherService（Retrofit 接口）
      → WeatherCacheDatabase（Room，网络失败时读缓存兜底）
      → PlaceDao（SharedPreferences，城市持久化）
```

- ViewModel 使用 `MutableLiveData` 上的 `switchMap` 响应式触发 API 调用
- Repository 直接在 `liveData(Dispatchers.IO){}` 中执行网络请求和缓存读写
- 天气 API 调用在 `coroutineScope { async { ... } }` 中并行执行
- 网络成功时自动写入 Room 缓存；网络失败时从 Room 读取缓存兜底
- `SunnyWeatherNetwork.await()` 是手动实现的协程适配器

### 缓存策略

- **存储**：Room 数据库（`sunny_weather_cache.db`），`weather_cache` 表
- **Key**：`"{lng},{lat}"`（按城市经纬度区分）
- **有效期**：30 分钟标记过期，但不删除；网络失败时仍可使用
- **覆盖**：网络请求成功时自动覆盖写入

### API 详情

- **Base URL**：`https://api.caiyunapp.com/`
- **Token**：硬编码在 `SunnyWeatherApplication.TOKEN` 中
- **限流**：`RateLimitingInterceptor` 强制 1 QPS（同步锁 + 1.1s 最小间隔 + 429 指数退避）
- 城市搜索：`GET v2/place?token=TOKEN&lang=zh_CN&query=...`
- 实时天气：`GET v2.6/TOKEN/lng,lat/realtime`
- 每日预报：`GET v2.6/TOKEN/lng,lat/daily`

### 关键模式

- **ViewBinding**：所有布局均启用（`buildFeatures.viewBinding = true`）
- **LiveData + 协程**：Repository 使用 `liveData(Dispatchers.IO) {}` 搭配 `try/catch` → `Result<T>`
- **城市持久化**：上次选择的城市以 JSON 格式（通过 Gson）保存到 SharedPreferences
- **天气状况映射**：`Sky.kt` 将 API 的 skycon 字符串映射为中文描述、drawable 图标和背景资源
- **Room 缓存**：Weather 对象经 Gson 序列化为 JSON 存入 Room，读取时反序列化回对象
- **无 Navigation Component**：Activity 跳转使用手动 Intent

### 最低要求

- **minSdk**：24（Android 7.0）
- **targetSdk**：36（Android 16）
- **Java**：11
