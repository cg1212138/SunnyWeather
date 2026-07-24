# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作提供指引。

## 技术栈

- **语言**：Kotlin
- **UI**：XML 布局（View 体系），未使用 Jetpack Compose
- **架构**：MVVM（ViewModel + LiveData + Repository）
- **网络**：Retrofit 2 + OkHttp（Gson 转换器）
- **异步**：Kotlin 协程
- **持久化**：SharedPreferences（通过 Gson 序列化）
- **依赖注入**：手动（无 Hilt/Koin —— 通过 Kotlin `object` 实现单例）

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
│   ├── Repository.kt               # 单一 Repository 对象 —— 协调 API 与本地数据
│   ├── dao/PlaceDao.kt             # 基于 SharedPreferences 持久化最后选择的城市
│   ├── model/                      # API 响应数据类 +天气状况映射
│   │   ├── PlaceResponse.kt        # 城市搜索响应
│   │   ├── RealtimeResponse.kt     # 实时天气响应
│   │   ├── DailyResponse.kt        # 7 日天气预报响应
│   │   ├── Weather.kt              # 合并的天气模型
│   │ └── Sky.kt                  # Skycon 字符串 → 中文描述、图标、背景
│   └── network/                    # Retrofit 服务与 OkHttp 配置
│       ├── ServiceCreator.kt       # Retrofit 单例构建器（base URL: api.caiyunapp.com）
│       ├── SunnyWeatherNetwork.kt  # 网络层外观 —— 将 Call 封装为 suspend 函数
│       ├── PlaceService.kt         # Retrofit 接口：城市搜索
│       ├── WeatherService.kt       # Retrofit 接口：实时天气 + 每日预报
│ └── RateLimitingInterceptor.kt  # OkHttp 拦截器：QPS=1，含 429 重试与退避
├── ui/
│   ├── place/                      # 城市搜索界面
│   │   ├── PlaceFragment.kt        # 带自动补全的搜索 UI
│   │   ├── PlaceViewModel.kt       # ViewModel —— switchMap 响应搜索查询 LiveData
│   │   └── PlaceAdapter.kt         # RecyclerView 适配器，点击时保存城市并导航
│ └── weather/                    # 天气展示界面
│       ├── WeatherActivity.kt      # 天气主界面 —— 当前天气、7 日预报、生活指数
│ └── WeatherViewModel.kt     # ViewModel —— switchMap 响应位置 LiveData
├── MainActivity.kt                 # 启动 Activity（存根，由 PlaceFragment 替代）
└── SunnyWeatherApplication.kt      # Application 类 —— 持有应用上下文和 API TOKEN
```

### 数据流

```
UI（Fragment/Activity）
  → ViewModel（LiveData, switchMap）
    → Repository（object，基于协程）
      → SunnyWeatherNetwork（suspend 函数）
        → ServiceCreator（Retrofit 单例）
          → PlaceService / WeatherService（Retrofit 接口）
```

- ViewModel 使用 `MutableLiveData` 上的 `switchMap` 响应式触发 API 调用
- Repository 使用自定义 `fire()` 辅助方法封装 `liveData{}` 块，运行在 `Dispatchers.IO`
- 天气 API 调用在 `Repository.refreshWeather()` 中通过 `coroutineScope { async { ... } }` 并行执行
- `SunnyWeatherNetwork.await()` 是手动实现的协程适配器（未使用官方的 Retrofit 适配器）

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
- **天气状况映射**：`Sky.kt` 将 API 的 skycon 字符串（如 `"CLEAR_DAY"`）映射为中文描述、drawable 图标和背景资源
- **无 Navigation Component**：Activity 跳转使用手动 Intent（PlaceFragment → WeatherActivity）

### 最低要求

- **minSdk**：24（Android 7.0）
- **targetSdk**：36（Android 16）
- **Java**：11
