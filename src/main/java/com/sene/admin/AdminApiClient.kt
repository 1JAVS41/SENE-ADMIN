package com.sene.admin

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AdminApiClient(context: Context, baseUrl: String) {
    private val app = context.applicationContext
    private val root = baseUrl.trim().trimEnd('/')
    private val sessions = AdminSessionStore(app)

    init {
        require(root.startsWith("https://")) { "El servidor administrativo debe usar HTTPS" }
    }

    fun login(email: String, password: String): JSONObject {
        val result = request("/v1/admin/login", "POST",
            JSONObject().put("email", email.trim()).put("password", password), null)
        val token = result.optString("accessToken")
        check(token.isNotBlank()) { "El servidor no devolvió una sesión administrativa" }
        sessions.save(token)
        return result
    }

    fun logout() {
        val token = sessions.read()
        if (!token.isNullOrBlank()) request("/v1/admin/logout", "POST", JSONObject(), token)
        sessions.clear()
    }

    fun pending(path: String): JSONObject {
        val token = sessions.read() ?: throw IllegalStateException("La sesión administrativa venció")
        return request(path, "GET", null, token)
    }

    fun authorize(path: String, requestId: String, paymentReference: String, deliveryPhone: String): JSONObject {
        val token = sessions.read() ?: throw IllegalStateException("La sesión administrativa venció")
        require(requestId.isNotBlank() && paymentReference.isNotBlank() && deliveryPhone.isNotBlank())
        return request(path, "POST", JSONObject()
            .put("requestId", requestId)
            .put("paymentReference", paymentReference.trim())
            .put("deliveryPhone", deliveryPhone.trim()), token)
    }

    private fun request(path: String, method: String, body: JSONObject?, token: String?): JSONObject {
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 20000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-SENE-Version", "1")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (body != null) connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Servidor SENE HTTP $status: $response")
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}
