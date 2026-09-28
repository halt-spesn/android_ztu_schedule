package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class ZtuScheduleApi(
    private val cabinetAuth: CabinetAuthManager? = null,
    private val client: OkHttpClient = cabinetAuth?.httpClient ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "ZtuScheduleApi"
        const val BASE_URL = "https://rozklad.ztu.edu.ua"
        const val DEFAULT_GROUP_ID = "612"
        const val LOGIN_URL = "$BASE_URL/schedule/users/login"

        fun isRozkladUnauthenticated(response: Response?, html: String): Boolean {
            if (response != null && response.request.url.encodedPath.contains("/schedule/users/login")) {
                return true
            }
            if (response != null && response.code in 300..399 && response.header("Location")?.contains("/schedule/users/login") == true) {
                return true
            }
            if (html.contains("form-signin") && (html.contains("name=\"login\"") || html.contains("name=\"password\""))) {
                return true
            }
            if (html.contains("Вхід у систему") || html.contains("Введіть логін та пароль")) {
                return true
            }
            return false
        }
    }

    suspend fun fetchScheduleHtml(groupId: String = DEFAULT_GROUP_ID): String = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/schedule/group?id=$groupId"
        fetchWithAuthRetry(url, "розкладу")
    }

    suspend fun fetchGroupListHtml(): String = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/schedule/group/list"
        fetchWithAuthRetry(url, "списку груп")
    }

    private suspend fun fetchWithAuthRetry(url: String, resourceName: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; ZTU Schedule Mobile App)")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.8")
            .build()

        var response = client.newCall(request).execute()
        var html = response.body?.string() ?: ""

        if (isRozkladUnauthenticated(response, html)) {
            Log.w(TAG, "Request to $url requires Rozklad authentication. Attempting silent login...")
            if (cabinetAuth != null && cabinetAuth.hasCredentials()) {
                val loginRes = cabinetAuth.loginToRozklad()
                if (loginRes.isSuccess) {
                    val retryResp = client.newCall(request).execute()
                    val retryHtml = retryResp.body?.string() ?: ""
                    if (retryResp.isSuccessful && !isRozkladUnauthenticated(retryResp, retryHtml)) {
                        return retryHtml
                    }
                    throw IOException("Не вдалося авторизуватися на сайті розкладу ЖТУ. Перевірте логін та пароль.")
                } else {
                    val errMsg = loginRes.exceptionOrNull()?.message ?: "Помилка входу"
                    throw IOException("Помилка авторизації на сайті розкладу: $errMsg")
                }
            } else {
                throw IOException("Для перегляду розкладу ЖТУ тепер потрібна авторизація. Будь ласка, увійдіть зі своїм логіном та паролем у меню кабінету.")
            }
        }

        if (!response.isSuccessful) {
            throw IOException("Помилка завантаження $resourceName: HTTP ${response.code}")
        }
        if (html.isBlank()) {
            throw IOException("Отримано порожню відповідь від сервера розкладу")
        }

        return html
    }
}
