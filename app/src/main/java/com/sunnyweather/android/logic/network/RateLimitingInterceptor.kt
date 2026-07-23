// network/RateLimitingInterceptor.kt
package com.sunnyweather.android.logic.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RateLimitingInterceptor : Interceptor {
    
    private val lock = Any()
    private var lastRequestTime: Long = 0L
    private val minIntervalMs = 1100L  // QPS=1，留100ms余量
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        // ① 串行化控制：确保任意时刻只有一个请求通过
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < minIntervalMs) {
                val waitTime = minIntervalMs - elapsed
                Thread.sleep(waitTime)
            }
            lastRequestTime = System.currentTimeMillis()
        }
        
        // ② 发起请求
        val request = chain.request()
        var response = chain.proceed(request)
        
        // ③ 处理429：带退避的重试
        var retryCount = 0
        val maxRetries = 3
        while (response.code() == 429 && retryCount < maxRetries) {
            response.close()
            
            // 尊重服务器的Retry-After头，否则使用指数退避
            val retryAfterMs = response.header("Retry-After")?.toLongOrNull()?.let { it * 1000 }
                ?: (2000L * (1 shl retryCount))  // 2s, 4s, 8s
            
            Thread.sleep(retryAfterMs)
            
            // 重试前重新获取时间锁
            synchronized(lock) {
                lastRequestTime = System.currentTimeMillis()
            }
            
            response = chain.proceed(request)
            retryCount++
        }
        
        return response
    }
}
