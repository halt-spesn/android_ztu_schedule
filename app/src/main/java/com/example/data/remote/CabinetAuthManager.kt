package com.example.data.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

class CabinetAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "CabinetAuthManager"
        const val BASE_URL = "https://cabinet.ztu.edu.ua"
        const val LOGIN_URL = "$BASE_URL/site/login"
        const val SCHEDULE_URL = "$BASE_URL/site/schedule"
        const val INDEX_URL = "$BASE_URL/site/index"

        private const val PREFS_NAME = "ztu_cabinet_auth_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_STUDENT_NAME = "student_name"
        private const val KEY_COOKIES = "cookies"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Persistent in-memory and SharedPreferences cookie store
    private val cookieJar = object : CookieJar {
        private val cookieMap = mutableMapOf<String, MutableList<Cookie>>()

        init {
            loadFromPrefs()
        }

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val existing = cookieMap.getOrPut(host) { mutableListOf() }
            for (cookie in cookies) {
                existing.removeAll { it.name == cookie.name }
                existing.add(cookie)
            }
            saveToPrefs()
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return cookieMap.values.flatten().filter { it.expiresAt > now && it.matches(url) }
        }

        @Synchronized
        fun clear() {
            cookieMap.clear()
            prefs.edit().remove(KEY_COOKIES).apply()
        }

        private fun saveToPrefs() {
            val set = mutableSetOf<String>()
            for ((_, cookies) in cookieMap) {
                for (c in cookies) {
                    set.add("${c.name}#${c.value}#${c.domain}#${c.path}#${c.expiresAt}#${c.secure}#${c.httpOnly}")
                }
            }
            prefs.edit().putStringSet(KEY_COOKIES, set).apply()
        }

        private fun loadFromPrefs() {
            val set = prefs.getStringSet(KEY_COOKIES, emptySet()) ?: emptySet()
            val now = System.currentTimeMillis()
            for (serialized in set) {
                val parts = serialized.split("#")
                if (parts.size >= 7) {
                    try {
                        val name = parts[0]
                        val value = parts[1]
                        val domain = parts[2]
                        val path = parts[3]
                        val expiresAt = parts[4].toLongOrNull() ?: (now + 86400000L)
                        val secure = parts[5].toBoolean()
                        val httpOnly = parts[6].toBoolean()

                        if (expiresAt > now) {
                            val builder = Cookie.Builder()
                                .name(name)
                                .value(value)
                                .domain(domain)
                                .path(path)
                                .expiresAt(expiresAt)
                            if (secure) builder.secure()
                            if (httpOnly) builder.httpOnly()

                            val cookie = builder.build()
                            cookieMap.getOrPut(domain) { mutableListOf() }.add(cookie)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error restoring cookie: $serialized", e)
                    }
                }
            }
        }
    }

    // Client with followRedirects(false) solely for POST login to detect HTTP 302
    private val loginHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    // Client with followRedirects(true) for GET requests (schedule, index, etc.)
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val loginMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile private var lastLoginSuccessMillis: Long = 0L

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""

    fun getStudentName(): String = prefs.getString(KEY_STUDENT_NAME, "") ?: ""

    suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        val cleanPassword = password.trim()

        if (cleanUsername.isEmpty() || cleanPassword.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Введіть логін та пароль"))
        }

        try {
            // 1. Fetch login page to extract CSRF token and initial session cookie
            val getRequest = Request.Builder()
                .url(LOGIN_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; ZTU Schedule Mobile App)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.8")
                .build()

            val getResponse = httpClient.newCall(getRequest).execute()
            val getHtml = getResponse.body?.string() ?: ""

            val doc = Jsoup.parse(getHtml)
            val csrfToken = doc.selectFirst("meta[name=csrf-token]")?.attr("content")
                ?: doc.selectFirst("input[name=_csrf-frontend]")?.attr("value")
                ?: ""

            if (csrfToken.isBlank()) {
                return@withContext Result.failure(IOException("Не вдалося отримати токен захисту з сервера кабінету"))
            }

            // 2. Submit credentials via POST
            val formBody = FormBody.Builder()
                .add("_csrf-frontend", csrfToken)
                .add("LoginForm[username]", cleanUsername)
                .add("LoginForm[password]", cleanPassword)
                .add("LoginForm[rememberMe]", "1")
                .build()

            val postRequest = Request.Builder()
                .url(LOGIN_URL)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; ZTU Schedule Mobile App)")
                .header("Referer", LOGIN_URL)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val postResponse = loginHttpClient.newCall(postRequest).execute()
            val statusCode = postResponse.code

            // Successful login in Yii2 produces a 302 Redirect to /site/index or /
            if (statusCode in 300..399 || postResponse.header("Location")?.contains("index") == true) {
                lastLoginSuccessMillis = System.currentTimeMillis()
                // Save session & credentials
                prefs.edit().apply {
                    putBoolean(KEY_IS_LOGGED_IN, true)
                    putString(KEY_USERNAME, cleanUsername)
                    putString(KEY_PASSWORD, cleanPassword)
                    apply()
                }

                // Try fetching index to get student's name
                try {
                    val indexReq = Request.Builder()
                        .url(INDEX_URL)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; ZTU Schedule Mobile App)")
                        .build()
                    val indexResp = httpClient.newCall(indexReq).execute()
                    val indexHtml = indexResp.body?.string() ?: ""
                    val indexDoc = Jsoup.parse(indexHtml)
                    val studentName = indexDoc.select(".navbar-nav .dropdown-toggle, .user-name, .profile-name")
                        .firstOrNull()?.text()?.trim() ?: cleanUsername
                    prefs.edit().putString(KEY_STUDENT_NAME, studentName).apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fetch student name", e)
                }

                return@withContext Result.success(cleanUsername)
            }

            // Status 200 means login stayed on page -> check error messages
            val postHtml = postResponse.body?.string() ?: ""
            val errDoc = Jsoup.parse(postHtml)
            val errorText = errDoc.select(".alert-danger, .help-block-error").text().trim()

            val message = if (errorText.isNotEmpty()) {
                errorText
            } else {
                "Неправильний логін або пароль від освітнього порталу ЖТУ"
            }

            return@withContext Result.failure(Exception(message))
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            return@withContext Result.failure(e)
        }
    }

    fun logout() {
        cookieJar.clear()
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_PASSWORD)
            remove(KEY_STUDENT_NAME)
            apply()
        }
    }

    private fun isSessionExpiredOrUnauthenticated(html: String): Boolean {
        if (html.isBlank()) return true
        if (html.contains("id=\"login-form\"") || html.contains("action=\"/site/login\"")) return true
        if (html.contains("Не вдалося визначити вашу навчальну групу")) return true
        if (html.contains("/site/login") && !html.contains("/site/logout") && !html.contains("Вийти")) return true
        return false
    }

    suspend fun fetchScheduleHtml(week: Int? = null, day: Int? = null): Result<String> = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            return@withContext Result.failure(IllegalStateException("Користувач не авторизований у кабінеті"))
        }

        val urlBuilder = SCHEDULE_URL.toHttpUrlOrNull()?.newBuilder() ?: return@withContext Result.failure(IOException("Invalid URL"))
        if (week != null) {
            urlBuilder.addQueryParameter("week", week.toString())
        }
        if (day != null) {
            urlBuilder.addQueryParameter("day", day.toString())
        }
        val url = urlBuilder.build().toString()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; ZTU Schedule Mobile App)")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.8")
            .build()

        try {
            var response = httpClient.newCall(request).execute()
            var html = response.body?.string() ?: ""

            // If session expired or redirected to unauthenticated content, silently re-login and retry
            if (response.code in 300..399 || isSessionExpiredOrUnauthenticated(html)) {
                Log.i(TAG, "Cabinet session expired or unauthenticated, attempting silent re-login...")
                val reLoginResult = silentReLogin()
                if (reLoginResult.isSuccess) {
                    val retryResp = httpClient.newCall(request).execute()
                    html = retryResp.body?.string() ?: ""
                    if (isSessionExpiredOrUnauthenticated(html)) {
                        return@withContext Result.failure(Exception("Сесію кабінету завершено. Будь ласка, увійдіть знову."))
                    }
                } else {
                    return@withContext Result.failure(Exception("Сесію кабінету завершено. Будь ласка, увійдіть знову."))
                }
            }

            saveLastCabinetHtml(html)
            return@withContext Result.success(html)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching schedule from cabinet", e)
            return@withContext Result.failure(e)
        }
    }

    fun saveLastCabinetHtml(html: String) {
        try {
            java.io.File(context.cacheDir, "last_cabinet_schedule.html").writeText(html)
        } catch (_: Exception) {}
    }

    private suspend fun silentReLogin(): Result<String> = loginMutex.withLock {
        // Reuse recent successful login if occurred within 15 seconds (prevents concurrent spam)
        val now = System.currentTimeMillis()
        if (now - lastLoginSuccessMillis < 15_000L) {
            return@withLock Result.success(getUsername())
        }
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val password = prefs.getString(KEY_PASSWORD, "") ?: ""
        if (username.isEmpty() || password.isEmpty()) {
            logout()
            return@withLock Result.failure(IllegalStateException("No credentials"))
        }
        val res = login(username, password)
        if (res.isSuccess) {
            lastLoginSuccessMillis = System.currentTimeMillis()
        }
        res
    }
}
