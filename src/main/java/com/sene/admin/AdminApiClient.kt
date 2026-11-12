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
        require(root == TEST_SERVER) {
            "Esta versión de SENE ADMIN solo puede conectarse al Worker de pruebas."
        }
    }

    companion object {
        private const val TEST_SERVER = "https://sene-licensing-api-testing.1codex41.workers.dev"
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

    fun reissue(path: String, idField: String, idValue: String, deliveryPhone: String): JSONObject {
        val token = sessions.read() ?: throw IllegalStateException("La sesión administrativa venció")
        require(idField in setOf("licenseId", "requestId") && idValue.isNotBlank() && deliveryPhone.isNotBlank())
        return request(path, "POST", JSONObject()
            .put(idField, idValue)
            .put("deliveryPhone", deliveryPhone.trim()), token)
    }

    fun adminEvents(): JSONObject {
        val token = sessions.read() ?: throw IllegalStateException("La sesión administrativa venció")
        return request("/v1/admin/events", "GET", null, token)
    }

    fun acknowledgeAdminEvents(batch: JSONObject, ackId: String): JSONObject {
        val token = sessions.read() ?: throw IllegalStateException("La sesión administrativa venció")
        val items = batch.optJSONArray("items") ?: org.json.JSONArray()
        val expectedHash = batch.optString("payloadSha256")
        val localHash = sha256(canonicalJson(items))
        check(expectedHash == localHash) { "La huella del lote de eventos no coincide" }
        val body = JSONObject()
            .put("ackId", ackId)
            .put("fromCursor", batch.optLong("fromCursor"))
            .put("throughCursor", batch.optLong("throughCursor"))
            .put("deliveryId", batch.optString("deliveryId"))
            .put("payloadSha256", expectedHash)
        return request("/v1/admin/events/ack", "POST", body, token)
    }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
            JSONObject.quote(it) + ":" + canonicalJson(value.get(it))
        }
        is org.json.JSONArray -> (0 until value.length()).joinToString(",", "[", "]") {
            canonicalJson(value.get(it))
        }
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

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
            if (status !in 200..299) {
                val errorCode = runCatching { JSONObject(response).optString("error") }.getOrDefault("")
                val message = when (errorCode) {
                    "ADMIN_AUTH_NOT_CONFIGURED" ->
                        "El acceso de SENE ADMIN aún no está habilitado en el servidor de pruebas. Falta configurar las credenciales administrativas del entorno."
                    "ADMIN_UNAUTHORIZED" ->
                        "El servidor rechazó el acceso administrativo. Revisa el correo y la contraseña."
                    else -> "Servidor SENE HTTP $status: $response"
                }
                throw IOException(message)
            }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}
