package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class ZtuScheduleApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    companion object {
        const val BASE_URL = "https://rozklad.ztu.edu.ua"
        const val DEFAULT_GROUP_ID = "612"
    }

    suspend fun fetchScheduleHtml(groupId: String = DEFAULT_GROUP_ID): String = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/schedule/group?id=$groupId"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; ZTU Schedule Mobile App)")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Помилка завантаження розкладу: HTTP ${response.code}")
        }
        response.body?.string() ?: throw IOException("Отримано порожню відповідь від сервера")
    }

    suspend fun fetchGroupListHtml(): String = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/schedule/group/list"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; ZTU Schedule Mobile App)")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.8")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Помилка завантаження списку груп: HTTP ${response.code}")
        }
        response.body?.string() ?: throw IOException("Порожня відповідь")
    }
}
