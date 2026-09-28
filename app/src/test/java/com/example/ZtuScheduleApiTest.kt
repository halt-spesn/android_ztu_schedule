package com.example

import com.example.data.remote.ZtuScheduleApi
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZtuScheduleApiTest {

    @Test
    fun testIsRozkladUnauthenticated_withLoginPageHtml() {
        val loginHtml = """
            <!doctype html>
            <html>
            <head><title>Розклад | Вхід у систему</title></head>
            <body>
            <main class="form-signin">
                <form action="" method="post">
                    <input type="text" name="login" id="login">
                    <input type="password" name="password" id="password">
                    <button type="submit">Увійти</button>
                </form>
            </main>
            </body>
            </html>
        """.trimIndent()

        assertTrue(ZtuScheduleApi.isRozkladUnauthenticated(null, loginHtml))
    }

    @Test
    fun testIsRozkladUnauthenticated_withRedirectResponse() {
        val req = Request.Builder().url("https://rozklad.ztu.edu.ua/schedule/users/login").build()
        val response = Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()

        assertTrue(ZtuScheduleApi.isRozkladUnauthenticated(response, "<html></html>"))
    }

    @Test
    fun testIsRozkladUnauthenticated_withValidScheduleHtml() {
        val scheduleHtml = """
            <!doctype html>
            <html>
            <head><title>Розклад групи КІ-26-1</title></head>
            <body>
            <div class="schedule">
                <table class="schedule">
                    <tr><th>Понеділок</th></tr>
                </table>
            </div>
            </body>
            </html>
        """.trimIndent()

        val req = Request.Builder().url("https://rozklad.ztu.edu.ua/schedule/group?id=612").build()
        val response = Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(scheduleHtml.toResponseBody())
            .build()

        assertFalse(ZtuScheduleApi.isRozkladUnauthenticated(response, scheduleHtml))
    }
}
