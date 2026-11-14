package com.sene.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TEST_API = "https://sene-licensing-api-testing.1codex41.workers.dev"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SeneAdminApp() }
    }
}

private enum class Screen { LOGIN, RESET_REQUEST, RESET_CONFIRM, DASHBOARD }

private object Api {
    suspend fun post(path: String, body: JSONObject): Pair<Int, JSONObject> = withContext(Dispatchers.IO) {
        val connection = (URL(TEST_API + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to (try { JSONObject(text) } catch (_: Exception) { JSONObject() })
        } finally { connection.disconnect() }
    }

    suspend fun health(): Pair<Int, JSONObject> = withContext(Dispatchers.IO) {
        val connection = (URL(TEST_API + "/health").openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to (try { JSONObject(text) } catch (_: Exception) { JSONObject() })
        } finally { connection.disconnect() }
    }
}

@Composable
private fun SeneAdminApp() {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Conectada al entorno de pruebas") }
    var busy by remember { mutableStateOf(false) }
    var serverOnline by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF087FA5),
            secondary = Color(0xFF16A36A),
            background = Color(0xFFF3F7F8),
            surface = Color.White
        )
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF3F7F8))) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                BrandHeader()
                Spacer(Modifier.height(22.dp))
                when (screen) {
                    Screen.LOGIN -> {
                        Header("Acceso administrativo", "Inicia sesión para administrar el ecosistema SENE.")
                        Field("Correo administrativo", email, { email = it }, KeyboardType.Email)
                        PasswordField("Contraseña", password, { password = it })
                        PrimaryButton("Iniciar sesión", busy) {
                            busy = true; message = ""
                            scope.launch {
                                try {
                                    val (status, result) = Api.post("/v1/admin/login",
                                        JSONObject().put("email", email.trim()).put("password", password))
                                    if (status in 200..299 && result.has("accessToken")) {
                                        token = result.optString("accessToken")
                                        screen = Screen.DASHBOARD
                                        message = "Sesión iniciada correctamente."
                                        val (healthCode, health) = Api.health()
                                        serverOnline = healthCode in 200..299 && health.optString("status") == "ok"
                                    } else {
                                        message = when (result.optString("error")) {
                                            "ADMIN_PASSWORD_RESET_REQUIRED" -> "Tu contraseña requiere restablecimiento por correo."
                                            "ADMIN_AUTH_NOT_CONFIGURED" -> "El acceso administrativo aún no está configurado en el servidor de pruebas."
                                            else -> "No se pudo iniciar sesión. Verifica tus datos o inténtalo más tarde."
                                        }
                                    }
                                } catch (_: Exception) { message = "No se pudo conectar con el servidor de pruebas." }
                                busy = false
                            }
                        }
                        TextButton(onClick = { screen = Screen.RESET_REQUEST; message = "" }) {
                            Text("¿Olvidaste tu contraseña? Restablecer por correo")
                        }
                    }
                    Screen.RESET_REQUEST -> {
                        BackLine { screen = Screen.LOGIN }
                        Header("Restablecer contraseña", "Escribe tu correo y la nueva contraseña. Enviaremos un código para confirmar que tú solicitaste el cambio.")
                        Field("Correo administrativo", email, { email = it }, KeyboardType.Email)
                        PasswordField("Nueva contraseña (mínimo 12 caracteres)", password, { password = it })
                        PasswordField("Confirmar nueva contraseña", confirmPassword, { confirmPassword = it })
                        PrimaryButton("Enviar código al correo", busy) {
                            busy = true; message = ""
                            scope.launch {
                                try {
                                    val (status, result) = Api.post("/v1/admin/password/reset/request",
                                        JSONObject().put("email", email.trim()).put("newPassword", password).put("confirmPassword", confirmPassword))
                                    if (status == 202) { screen = Screen.RESET_CONFIRM; message = result.optString("message", "Revisa tu correo.") }
                                    else message = when (result.optString("error")) {
                                        "PASSWORD_RESET_EMAIL_NOT_CONFIGURED" -> "El envío de correo todavía no está configurado en el servidor de pruebas."
                                        "PASSWORD_RESET_RATE_LIMITED" -> "Se alcanzó el límite de solicitudes. Espera e inténtalo más tarde."
                                        else -> "No se pudo solicitar el cambio. Revisa que ambas contraseñas coincidan y tengan al menos 12 caracteres."
                                    }
                                } catch (_: Exception) { message = "No se pudo conectar con el servidor de pruebas." }
                                busy = false
                            }
                        }
                    }
                    Screen.RESET_CONFIRM -> {
                        BackLine { screen = Screen.RESET_REQUEST }
                        Header("Confirma tu identidad", "Introduce el código de ocho dígitos que recibiste por correo. Caduca en 15 minutos.")
                        Field("Código de confirmación", code, { code = it.filter(Char::isDigit).take(8) }, KeyboardType.Number)
                        PrimaryButton("Confirmar y cambiar contraseña", busy) {
                            busy = true; message = ""
                            scope.launch {
                                try {
                                    val (status, result) = Api.post("/v1/admin/password/reset/confirm",
                                        JSONObject().put("email", email.trim()).put("code", code))
                                    if (status in 200..299 && result.optBoolean("passwordReset")) {
                                        screen = Screen.LOGIN; password = ""; confirmPassword = ""; code = ""
                                        message = "Contraseña actualizada. Inicia sesión con la nueva contraseña."
                                    } else message = "El código no es válido, ya se utilizó o caducó."
                                } catch (_: Exception) { message = "No se pudo conectar con el servidor de pruebas." }
                                busy = false
                            }
                        }
                    }
                    Screen.DASHBOARD -> {
                        Header("Panel de administración", "Sesión autenticada en el entorno de pruebas.")
                        StatusCard("Servidor de pruebas", if (serverOnline) "Conectado" else "Sin verificar")
                        StatusCard("Sesión", "Autenticada")
                        StatusCard("Entorno", "PRUEBAS — no producción")
                        Text("La autenticación está conectada. Los módulos de empresas, licencias, pagos, equipos y auditoría se integrarán sobre los endpoints administrativos verificados.", color = Color(0xFF52636B), fontSize = 14.sp)
                        PrimaryButton("Actualizar estado del servidor", busy) {
                            busy = true
                            scope.launch {
                                try {
                                    val (status, data) = Api.health()
                                    serverOnline = status in 200..299 && data.optString("status") == "ok"
                                    message = if (serverOnline) "Servidor de pruebas disponible." else "Servidor no disponible."
                                } catch (_: Exception) { serverOnline = false; message = "No se pudo conectar." }
                                busy = false
                            }
                        }
                        OutlinedButton(onClick = { token = null; password = ""; screen = Screen.LOGIN; message = "Sesión cerrada en esta aplicación." }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
                    }
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(message, color = if (message.contains("correct") || message.contains("actualizada")) Color(0xFF137A4B) else Color(0xFF8A4B18), fontSize = 13.sp)
                }
                Spacer(Modifier.height(30.dp))
                Text("SENE ADMIN • Versión de pruebas", color = Color(0xFF84949B), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.size(54.dp).background(Brush.linearGradient(listOf(Color(0xFF087FA5), Color(0xFF16A36A))), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("SENE", fontSize = 29.sp, fontWeight = FontWeight.Black, color = Color(0xFF087FA5), letterSpacing = 2.sp)
            Text("ADMIN", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A36A), letterSpacing = 3.sp)
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color(0xFF18343F))
        Spacer(Modifier.height(5.dp))
        Text(subtitle, fontSize = 14.sp, color = Color(0xFF62757D))
    }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}

@Composable
private fun PasswordField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 5.dp), shape = RoundedCornerShape(14.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BackLine(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Icon(Icons.Default.ArrowBack, contentDescription = null)
        Spacer(Modifier.width(5.dp)); Text("Volver")
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color(0xFF52636B), fontWeight = FontWeight.Medium)
            Text(value, color = Color(0xFF087FA5), fontWeight = FontWeight.Bold)
        }
    }
}
