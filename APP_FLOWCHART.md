# 🌤️ SunnyWeather 项目全览

> 基于 **MVVM + Kotlin 协程 + Retrofit + Room** 的天气 App 完整文档。

---

## 📦 技术栈速览

| 类别 | 选用方案 |
|------|----------|
| 语言 | **Kotlin** |
| UI | **XML 布局 + ViewBinding**（View 体系，非 Compose） |
| 架构 | **MVVM**：ViewModel + LiveData + Repository |
| 网络 | **Retrofit 2 + OkHttp**（Gson 转换器） |
| 异步 | **Kotlin 协程** |
| 数据库 | **Room**（天气缓存） |
| 持久化 | **SharedPreferences**（保存最后选择的城市） |
| 注解处理 | **KSP**（Room 编译器） |

---

## 🏗️ 包结构

```
com.sunnyweather.android/
│
├── logic/                         # 业务逻辑与数据层
│   ├── Repository.kt              # 数据仓库（单例）
│   │
│   ├── dao/                       # 数据访问对象
│   │   ├── PlaceDao.kt            # SharedPreferences 持久化最后选择的城市
│   │   ├── WeatherCacheDao.kt     # Room DAO —— 天气缓存增删查  ⭐ 新增
│   │   └── WeatherCacheDatabase.kt# Room 数据库定义 + 单例        ⭐ 新增
│   │
│   ├── model/                     # 数据模型
│   │   ├── PlaceResponse.kt      # 城市搜索 API 响应
│   │   ├── RealtimeResponse.kt   # 实时天气 API 响应
│   │   ├── DailyResponse.kt      # 7 日天气预报响应
│   │   ├── Weather.kt            # 合并天气模型（realtime + daily）
│   │   ├── WeatherCacheEntity.kt # Room 实体（cacheKey, weatherJson, timestamp）⭐ 新增
│   │   └── Sky.kt                # Skycon 字符串 → 中文、图标、背景
│   │
│   └── network/                   # 网络层
│       ├── ServiceCreator.kt      # Retrofit 单例构建器
│       ├── SunnyWeatherNetwork.kt # 网络层外观（suspend 封装）
│       ├── PlaceService.kt        # 城市搜索 Retrofit 接口
│       ├── WeatherService.kt      # 天气 Retrofit 接口
│       └── RateLimitingInterceptor.kt # QPS=1 限流拦截器
│
├── ui/                            # UI 层
│   ├── place/                     # 城市搜索
│   │   ├── PlaceFragment.kt      # 搜索界面
│   │   ├── PlaceViewModel.kt     # 搜索 ViewModel
│   │   └── PlaceAdapter.kt       # 城市列表适配器
│   │
│   └── weather/                   # 天气展示
│       ├── WeatherActivity.kt     # 天气主界面
│       └── WeatherViewModel.kt    # 天气 ViewModel
│
├── MainActivity.kt                # 入口 Activity（存根）
└── SunnyWeatherApplication.kt     # Application（Context + API TOKEN）
```

---

## 🚀 启动流程

```
📱 用户打开 App
     │
     ▼
┌─────────────────────────────────────┐
│ SunnyWeatherApplication.onCreate()  │
│ → 保存 Application Context 到静态变量 │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│ MainActivity.onCreate()             │
│ → 加载 activity_main.xml            │
│ → PlaceFragment 自动嵌入             │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│ PlaceFragment.onActivityCreated()    │
│                                      │
│ 问：上次有保存的城市吗？               │
│ viewModel.isPlacedSaved()            │
└─────────┬─────────────────┬──────────┘
           │                 │
        ✅ 有              ❌ 没有
           │                 │
           ▼                 ▼
 ┌──────────────────┐ ┌──────────────────────┐
 │ PlaceDao         │ │ 停留在搜索界面         │
 │ .getSavedPlace() │ │                       │
 │ 从 SharedPref    │ │ 初始化 RecyclerView   │
 │ 读取城市数据      │ │ 绑定搜索框监听器       │
 └────────┬─────────┘ │ 等待用户输入          │
          │           └──────────────────────┘
          ▼
 ┌──────────────────────┐
 │ 创建 Intent          │
 │ → WeatherActivity    │
 │ putExtra: lng/lat/name │
 │ startActivity()      │
 │ finish() 关闭当前页    │
 └──────────────────────┘
```

---

## 🔍 城市搜索流程

```
🖊️ 用户在搜索框输入 "北京"
     │
     ▼
┌───────────────────────────────────────┐
│ EditText.addTextChangedListener       │
│ → 监听输入内容变化                     │
└──────────────────┬────────────────────┘
                   │
                   ▼
          ┌────────────────┐
          │ 输入内容是否为空？ │
          └───────┬────────┘
                  │
        ┌────────┴────────┐
        │                 │
      ✅ 空              ❌ 不为空
        │                 │
        ▼                 ▼
┌─────────────────┐ ┌──────────────────────┐
│ 显示背景图        │ │ PlaceViewModel      │
│ bgImageView可见  │ │ .searchPlaces("北京") │
│ RecyclerView隐藏 │ │ → searchLiveData更新 │
│ 清空 placeList   │ └──────────┬───────────┘
└─────────────────┘            │
                               │ switchMap 自动触发
                               ▼
                     ┌──────────────────────┐
                     │ Repository           │
                     │ .searchPlaces("北京") │
                     │ → Dispatchers.IO     │
                     └──────────┬───────────┘
                                │
                                ▼
                     ┌──────────────────────┐
                     │ 网络请求              │
                     │ GET /v2/place        │
                     │ ?token=xxx&query=北京 │
                     │ &lang=zh_CN          │
                     └──────────┬───────────┘
                                │
                     ┌─────────┴──────────┐
                     │                    │
                  请求成功 ✅            请求失败 ❌
                     │                    │
                     ▼                    ▼
            ┌──────────────┐   ┌──────────────────┐
            │ status=ok?   │   │ Toast:            │
            └──┬────┬──────┘   │ "未能查询到任何地点"│
               │    │          └──────────────────┘
             ✅    ❌
               │    └──── Toast
               ▼
     ┌──────────────────────┐
     │ PlaceFragment        │
     │ 观察 placeLiveData   │
     │                      │
     │ placeList.clear()    │
     │ placeList.addAll()   │
     │ adapter刷新列表      │
     └──────────────────────┘
```

---

## 🎯 选择城市 → 查看天气

```
👆 用户点击城市列表中的一个城市
     │
     ▼
┌──────────────────────────────────────┐
│ PlaceAdapter.onClick(position)       │
│ → 获取 Place 数据（name/lng/lat）    │
└──────────────────┬───────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│ PlaceViewModel.savePlace(place)      │
│ → Repository → PlaceDao              │
│ → Gson.toJson → SharedPreferences    │
└──────────────────┬───────────────────┘
                   │
                   ▼
          ┌─────────────────┐
          │ 当前在哪个界面？  │
          └────────┬────────┘
                   │
     ┌─────────────┴─────────────┐
     │                           │
     ▼                           ▼
┌───────────────────┐ ┌───────────────────────────┐
│ WeatherActivity    │ │ MainActivity              │
│（从抽屉内选择城市）  │ │（首次启动/从首页进入）    │
│                   │ │                           │
│ 直接更新 ViewModel │ │ 创建 Intent               │
│ locationLng = lng │ │ .putExtra("location_lng") │
│ locationLat = lat │ │ .putExtra("location_lat") │
│ placeName = name  │ │ .putExtra("place_name")   │
│                   │ │ startActivity(WeatherAct) │
│ closeDrawers()    │ │ activity?.finish()        │
│ refreshWeather()  │ │                           │
└───────────────────┘ └───────────────────────────┘
```

---

## 🌡️ 天气刷新 + 缓存流程

```
📌 refreshWeather(lng, lat) 被调用
     │
     ▼
┌──────────────────────────────────────────┐
│ WeatherViewModel                         │
│ locationLiveData.value = Location(lng,lat)│
│ → switchMap 自动触发 Repository 调用      │
└──────────────────┬───────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────┐
│ Repository.refreshWeather(lng, lat)      │
│ → liveData(Dispatchers.IO)              │
│ → coroutineScope { async { } }          │
└──────────────────┬───────────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
        ▼                     ▼
┌─────────────────┐ ┌──────────────────────┐
│ Async ①          │ │ Async ②               │
│ 实时天气请求      │ │ 每日预报请求           │
│ GET /realtime   │ │ GET /daily            │
└────────┬────────┘ └──────────┬───────────┘
         │                     │
         └──────────┬──────────┘
                    │
                    ▼
              ┌──────────┐
              │ 两个请求   │
              │ 都成功？   │
              └────┬─────┘
                   │
         ┌─────────┴──────────┐
         │                    │
     ✅ 成功                ❌ 失败
         │                    │
         ▼                    ▼
┌──────────────────┐ ┌──────────────────────┐
│ 合并 Weather 对象  │ │ 读 Room 缓存         │
│ realtime + daily  │ │ WeatherCacheDao     │
│                  │ │ .getWeatherCache()   │
│ 写 Room 缓存      │ └──────────┬───────────┘
│ Gson.toJson()    │            │
│ → weather_cache表 │   ┌────────┴────────┐
│                  │   │                 │
│ emit success     │ ✅ 有缓存          ❌ 无缓存
│ (新数据)          │   │                 │
└──────────────────┘   ▼                 ▼
                ┌──────────────┐ ┌────────────────┐
                │ emit success  │ │ emit failure   │
                │ (缓存数据)     │ │ → Toast: 获取失败│
                └──────────────┘ └────────────────┘
                    │
                    ▼
       ┌────────────────────────────────────┐
       │ WeatherActivity 收到 LiveData      │
       │                                    │
       │ showWeatherInfo(weather)           │
       │                                    │
       │ 1️⃣ 标题栏：城市名                   │
       │ 2️⃣ 当前天气：温度 / 天气状况 / AQI  │
       │ 3️⃣ 背景：根据 Skycon 切换           │
       │ 4️⃣ 7 日预报：日期｜图标｜天气｜温度  │
       │ 5️⃣ 生活指数：感冒/穿衣/紫外线/洗车  │
       │                                    │
       │ weatherLayout.visibility = VISIBLE │
       │ swipeRefresh.isRefreshing = false  │
       └────────────────────────────────────┘
```

### 缓存策略

| 项目 | 说明 |
|------|------|
| 📦 存储方式 | Room 数据库 `sunny_weather_cache.db` |
| 📄 表名 | `weather_cache` |
| 🔑 缓存 Key | `"{lng},{lat}"`（按城市经纬度区分） |
| 🗃️ 存储内容 | Gson 序列化的 Weather JSON 字符串 |
| ⏱ 过期策略 | 30分钟标记过期，但**不删除** |
| 🔄 覆盖策略 | 网络成功时自动覆盖写入 |
| 👁️ UI 感知 | **透明**——Activity/ViewModel 无需区分数据来源 |

---

## 📋 抽屉切换城市

```
📱 用户操作
     │
     ├─→ 向右滑动屏幕
     │
     └─→ 点击左上角 ☰ 按钮（navBtn.onClick）
               │
               ▼
     ┌────────────────────────┐
     │ drawerLayout           │
     │ .openDrawer(Gravity.START)│
     └───────────┬────────────┘
                 │
                 ▼
     ┌────────────────────────┐
     │ 左侧抽屉滑出            │
     │ → 内嵌 PlaceFragment   │
     │ → 用户输入搜索城市      │
     └───────────┬────────────┘
                 │
            用户选择城市
                 │
                 ▼
     ┌────────────────────────┐
     │ PlaceAdapter.onClick() │
     │                        │
     │ 更新 WeatherViewModel  │
     │ locationLng / lat      │
     │ placeName              │
     │                        │
     │ closeDrawers()         │
     │ refreshWeather()       │
     └────────────────────────┘

┌──────────────────────────────────────────────┐
│ 同时，天气页也支持下拉刷新：                    │
│                                              │
│ 用户在天气页向下滑动                           │
│ → SwipeRefreshLayout.onRefresh               │
│ → refreshWeather()（同一流程）                 │
└──────────────────────────────────────────────┘
```

---

## 📊 数据模型关系

### API 响应结构

```
┌─ PlaceResponse ───────────────────────────────┐
│                                                │
│  status : String                               │
│  places : List<Place>                          │
│           │                                    │
│           ├─ name      : String  ← 城市名      │
│           ├─ address   : String  ← 详细地址    │
│           └─ location  : Location              │
│                          ├─ lng : String  ← 经度 │
│                          └─ lat : String  ← 纬度 │
│                                                │
└────────────────────────────────────────────────┘

┌─ RealtimeResponse ─────────────────────────────┐
│                                                 │
│  status : String                                │
│  result : Result                                │
│           └─ realtime : Realtime                │
│                        ├─ skycon        : String│ ← "CLEAR_DAY"
│                        ├─ temperature   : Float │ ← 23.5
│                        └─ airQuality    : AirQuality
│                            └─ aqi.chn    : Float│ ← AQI 指数
│                                                 │
└─────────────────────────────────────────────────┘

┌─ DailyResponse ────────────────────────────────┐
│                                                  │
│  status : String                                  │
│  result : Result                                  │
│           └─ daily : Daily                        │
│                      ├─ temperature[] : min/max   │ ← 7天温度
│                      ├─ skycon[]      : value+date│ ← 7天天气
│                      └─ lifeIndex : LifeIndex     │
│                           ├─ coldRisk     : []    │ ← 感冒
│                           ├─ dressing     : []    │ ← 穿衣
│                           ├─ ultraviolet  : []    │ ← 紫外线
│                           └─ carWashing   : []    │ ← 洗车
│                                                  │
└──────────────────────────────────────────────────┘
```

### 合并模型

```
┌─ Weather ──────────────────────────────────────┐
│                                                  │
│  realtime : RealtimeResponse.Realtime  ← 实时数据│
│  daily    : DailyResponse.Daily       ← 预报数据  │
│                                                  │
└──────────────────────────────────────────────────┘
```

### Room 缓存实体

```
┌─ WeatherCacheEntity ───────────────────────────┐
│                                                  │
│  @PrimaryKey                                      │
│  cacheKey     : String   ← "{lng},{lat}"          │
│                                                  │
│  weatherJson  : String   ← Gson.toJson(Weather)   │
│                                                  │
│  timestamp    : Long     ← System.currentTimeMillis()│
│                                                  │
└──────────────────────────────────────────────────┘
```

### Skycon → UI 映射（Sky.kt）

| Skycon 编码 | 中文 | Drawable 图标 | 背景资源 |
|------------|------|---------------|---------|
| `CLEAR_DAY` | ☀️ 晴 | `ic_clear_day` | `bg_clear_day` |
| `CLEAR_NIGHT` | 🌙 晴（夜间） | `ic_clear_night` | `bg_clear_night` |
| `PARTLY_CLOUDY_DAY` | ⛅ 多云 | `ic_partly_cloud_day` | `bg_partly_cloudy_day` |
| `PARTLY_CLOUDY_NIGHT` | ☁️ 多云（夜间） | `ic_partly_cloud_night` | `bg_partly_cloudy_night` |
| `CLOUDY` | ☁️ 阴 | `ic_cloudy` | `bg_cloudy` |
| `WIND` | 💨 大风 | `ic_cloudy` | `bg_wind` |
| `LIGHT_RAIN` | 🌦️ 小雨 | `ic_light_rain` | `bg_rain` |
| `MODERATE_RAIN` | 🌧️ 中雨 | `ic_moderate_rain` | `bg_rain` |
| `HEAVY_RAIN` | 🌧️ 大雨 | `ic_heavy_rain` | `bg_rain` |
| `STORM_RAIN` | 🌧️ 暴雨 | `ic_storm_rain` | `bg_rain` |
| `THUNDER_SHOWER` | ⛈️ 雷阵雨 | `ic_thunder_shower` | `bg_rain` |
| `SLEET` | 🌨️ 雨夹雪 | `ic_sleet` | `bg_rain` |
| `LIGHT_SNOW` | 🌨️ 小雪 | `ic_light_snow` | `bg_snow` |
| `MODERATE_SNOW` | ❄️ 中雪 | `ic_moderate_snow` | `bg_snow` |
| `HEAVY_SNOW` | ❄️ 大雪 | `ic_heavy_snow` | `bg_snow` |
| `STORM_SNOW` | ❄️ 暴雪 | `ic_heavy_snow` | `bg_snow` |
| `HAIL` | 🧊 冰雹 | `ic_hail` | `bg_snow` |
| `LIGHT_HAZE` | 🌫️ 轻度雾霾 | `ic_light_haze` | `bg_fog` |
| `MODERATE_HAZE` | 🌫️ 中度雾霾 | `ic_moderate_haze` | `bg_fog` |
| `HEAVY_HAZE` | 🌫️ 重度雾霾 | `ic_heavy_haze` | `bg_fog` |
| `FOG` | 🌁 雾 | `ic_fog` | `bg_fog` |
| `DUST` | 🏜️ 浮尘 | `ic_fog` | `bg_fog` |

---

## 📍 数据流全景

```
┌─────────────────────────────────────────────────┐
│                   用户交互                         │
│  （输入文字 / 点击城市 / 下拉刷新 / 滑动抽屉）      │
└───────────────────┬─────────────────────────────┘
                    │ 观察 LiveData
┌───────────────────▼─────────────────────────────┐
│                   UI 层                           │
│                                                  │
│  ┌────────────────────┐ ┌──────────────────────┐ │
│  │  PlaceFragment     │ │  WeatherActivity     │ │
│  │  城市搜索 + 列表    │ │  天气展示            │ │
│  │  观察 placeLiveData│ │  观察weatherLiveData │ │
│  └────────┬───────────┘ └──────────┬───────────┘ │
└───────────┼─────────────────────────┼───────────┘
            │                         │
┌───────────┼─────────────────────────┼───────────┐
│           ▼                         ▼            │
│  ┌────────────────────┐ ┌──────────────────────┐ │
│  │  PlaceViewModel    │ │  WeatherViewModel    │ │
│  │  searchLiveData    │ │  locationLiveData    │ │
│  │  placeLiveData     │ │  weatherLiveData     │ │
│  │  switchMap 触发     │ │  switchMap 触发      │ │
│  └────────┬───────────┘ └──────────┬───────────┘ │
│           │                        │              │
│           ▼                        ▼              │
│  ┌──────────────────────────────────────────┐     │
│  │               Repository                   │     │
│  │          （单例 object）                    │     │
│  │                                            │     │
│  │  ┌──────────────────────────────────────┐  │     │
│  │  │  refreshWeather(lng, lat)            │  │     │
│  │  │  → liveData(Dispatchers.IO)         │  │     │
│  │  │  → coroutineScope { async{} }       │  │     │
│  │  │  → 成功：写 Room 缓存                │  │     │
│  │  │  → 失败：读 Room 缓存兜底             │  │     │
│  │  └──────────────────────────────────────┘  │     │
│  │                                            │     │
│  │  ┌──────────────────────────────────────┐  │     │
│  │  │  searchPlaces(query)                 │  │     │
│  │  │  → Dispatchers.IO                    │  │     │
│  │  └──────────────────────────────────────┘  │     │
│  │                                            │     │
│  │  savePlace / getSavedPlace / isPlaceSaved  │     │
│  └──────────┬──────────────────────┬──────────┘     │
└─────────────┼──────────────────────┼────────────────┘
              │                      │
    ┌─────────▼──────────┐  ┌───────▼─────────────┐
    │      网络层          │  │      持久化层          │
    │                     │  │                       │
    │  ServiceCreator     │  │  ┌─────────────────┐  │
    │  (Retrofit 单例)    │  │  │ PlaceDao        │  │
    │  base URL:          │  │  │ SharedPref+Gson │  │
    │  api.caiyunapp.com   │  │  └─────────────────┘  │
    │                     │  │                       │
    │  PlaceService       │  │  ┌─────────────────┐  │
    │  GET /v2/place      │  │  │ WeatherCacheDB  │  │
    │                     │  │  │ Room            │  │
    │  WeatherService     │  │  │ weather_cache   │  │
    │  GET /realtime      │  │  │ 表              │  │
    │  GET /daily         │  │  └─────────────────┘  │
    │                     │  │                       │
    │  RateLimiting        │  │                       │
    │  Interceptor(1QPS)  │  │                       │
    └─────────────────────┘  └───────────────────────┘
```

---

## 🗺️ 快速导航

| 想了解什么？ | 跳转 |
|:---|:---|
| App 启动后发生了什么 | [🚀 启动流程](#-启动流程) |
| 搜索城市如何工作 | [🔍 城市搜索流程](#-城市搜索流程) |
| 选择城市后如何跳转 | [🎯 选择城市 → 查看天气](#-选择城市--查看天气) |
| 天气数据怎么来的（含缓存） | [🌡️ 天气刷新 + 缓存流程](#-天气刷新--缓存流程) |
| 抽屉怎么切换城市 | [📋 抽屉切换城市](#-抽屉切换城市) |
| API 返回的数据结构 | [📊 数据模型关系](#-数据模型关系) |
| 所有 Kotlin 文件及其职责 | [🏗️ 包结构](#-包结构) |
| 数据在各层怎么流动 | [📍 数据流全景](#-数据流全景) |
| 构建命令和技术细节 | [CLAUDE.md](CLAUDE.md) |
