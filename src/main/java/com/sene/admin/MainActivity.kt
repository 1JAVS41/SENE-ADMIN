package com.sene.admin

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {
    private val navy = Color.rgb(8, 22, 34)
    private val panel = Color.rgb(17, 42, 59)
    private val blue = Color.rgb(24, 151, 224)
    private val green = Color.rgb(40, 190, 132)
    private val muted = Color.rgb(174, 194, 207)
    private val sessions by lazy { AdminSessionStore(this) }
    private val serverPrefs by lazy { getSharedPreferences("sene_admin_config", MODE_PRIVATE) }
    private val defaultServer = "https://sene-licensing-api-testing.1codex41.workers.dev"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        if (sessions.read().isNullOrBlank()) renderLogin() else renderHome()
    }

    private fun renderLogin() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(26), dp(22), dp(26))
            setBackgroundColor(navy)
        }
        root.addView(logo(), LinearLayout.LayoutParams(dp(190), dp(120)))
        root.addView(label("SENE ADMIN", 26f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        })
        root.addView(label("ACCESO ADMINISTRATIVO SEGURO", 12f, green, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(22))
        })
        val server = field("URL HTTPS del servidor").apply {
            setText(serverPrefs.getString("server_url", defaultServer))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val email = field("Correo administrativo").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = field("Contraseña administrativa").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(server, matchWrap())
        root.addView(email, matchWrap().apply { topMargin = dp(10) })
        root.addView(password, matchWrap().apply { topMargin = dp(10) })
        val enter = button("INICIAR SESIÓN")
        root.addView(enter, matchWrap().apply { topMargin = dp(18) })
        enter.setOnClickListener {
            val url = server.text.toString().trim().trimEnd('/')
            val user = email.text.toString().trim()
            val pass = password.text.toString()
            if (url != defaultServer || user.isBlank() || pass.length < 10) {
                Toast.makeText(this, "Esta APK solo admite el Worker de pruebas. Revisa la URL, el correo y la contraseña.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            enter.isEnabled = false
            Thread {
                try {
                    AdminApiClient(this, url).login(user, pass)
                    serverPrefs.edit().putString("server_url", url).commit()
                    runOnUiThread {
                        password.setText("")
                        renderHome()
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        enter.isEnabled = true
                        Toast.makeText(this, adminLoginError(error), Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
        root.addView(label(
            "La contraseña se envía únicamente por HTTPS. El token se cifra con Android Keystore y no está incluido en la APK.",
            12f, muted, false
        ).apply { setPadding(0, dp(18), 0, 0) })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun adminLoginError(error: Exception): String {
        val raw = error.message.orEmpty()
        val code = try {
            val jsonText = raw.substringAfter(": ", raw)
            org.json.JSONObject(jsonText).optString("error")
        } catch (_: Exception) { "" }
        return when (code) {
            "ADMIN_AUTH_NOT_CONFIGURED" ->
                "El acceso de SENE ADMIN aún no está habilitado en el servidor de pruebas. Falta configurar las credenciales administrativas seguras."
            "INVALID_CREDENTIALS" ->
                "El correo o la contraseña administrativa no son correctos."
            "ADMIN_LOGIN_RATE_LIMITED" ->
                "Se alcanzó el límite de intentos. Espera antes de volver a iniciar sesión."
            else -> raw.ifBlank { "No se pudo iniciar sesión. Comprueba la conexión y vuelve a intentarlo." }
        }
    }

    private fun renderHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(navy)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundColor(panel)
        }
        header.addView(logo(), LinearLayout.LayoutParams(dp(170), dp(100)))
        header.addView(label("SENE ADMIN", 24f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        header.addView(label("SESIÓN ADMINISTRATIVA ACTIVA", 11f, green, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        })
        root.addView(header, matchWrap())
        root.addView(label("PANEL DE CONTROL", 14f, muted, true).apply {
            setPadding(dp(2), dp(20), 0, dp(8))
        })
        val modules = listOf(
            arrayOf("EMPRESAS Y LICENCIAS", "Estado de licencias, vigencias y equipos activos.", "/v1/operator/licenses", ""),
            arrayOf("REGISTROS PENDIENTES", "Altas de empresas y folios de activación.", "/v1/operator/registrations/pending", "/v1/operator/registrations/authorize"),
            arrayOf("RENOVACIONES", "Solicitudes de mensualidad pendientes.", "/v1/operator/renewals/pending", "/v1/operator/renewals/authorize"),
            arrayOf("REPOSICIONES DE EQUIPO", "Reposiciones pendientes de validación.", "/v1/operator/replacements/pending", "/v1/operator/replacements/authorize"),
            arrayOf("PAGOS DE RECUPERACIÓN PATRÓN", "Pagos para recuperar o reponer el equipo PATRÓN.", "/v1/operator/patron-recovery-payments/pending", "/v1/operator/patron-recovery-payments/authorize"),
            arrayOf("RECUPERACIONES PATRÓN", "Solicitudes de recuperación, códigos y estados.", "/v1/operator/patron-recovery/pending", ""),
            arrayOf("AUDITORÍA", "Eventos administrativos y trazabilidad de operaciones.", "/v1/operator/audit", ""),
            arrayOf("NOTIFICACIONES ADMIN", "Bandeja durable de eventos administrativos con ACK.", "ADMIN_EVENTS", "")
        )
        modules.forEach { module ->
            val title = module[0]
            val description = module[1]
            val path = module[2]
            val authorizePath = module[3]
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
                setBackgroundColor(panel)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (path == "ADMIN_EVENTS") refreshAdminEvents()
                    else loadPending(title, path, authorizePath)
                }
            }
            card.addView(label(title, 15f, Color.WHITE, true))
            card.addView(label(description, 13f, muted, false).apply { setPadding(0, dp(5), 0, 0) })
            card.addView(label("CONSULTAR PENDIENTES  ›", 11f, blue, true).apply { setPadding(0, dp(10), 0, 0) })
            root.addView(card, matchWrap().apply { topMargin = dp(9) })
        }
        val logout = button("CERRAR SESIÓN")
        root.addView(logout, matchWrap().apply { topMargin = dp(18) })
        logout.setOnClickListener {
            logout.isEnabled = false
            Thread {
                try {
                    AdminApiClient(this, serverPrefs.getString("server_url", defaultServer) ?: defaultServer).logout()
                } catch (_: Exception) {
                    sessions.clear()
                }
                runOnUiThread { renderLogin() }
            }.start()
        }
        root.addView(label(
            "Las autorizaciones se registran en la auditoría del servidor. La administración avanzada de todos los parámetros y eventos sigue pendiente de integración.",
            11f, muted, false
        ).apply { setPadding(dp(4), dp(18), dp(4), 0) })
        setContentView(scroll)
        // Una consulta al abrir el panel; no hay sondeo continuo en segundo plano.
        refreshAdminEvents()
    }

    private fun refreshAdminEvents() {
        val url = serverPrefs.getString("server_url", defaultServer) ?: defaultServer
        val store = AdminEventStore(this)
        Toast.makeText(this, "Sincronizando notificaciones administrativas…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val api = AdminApiClient(this, url)
                val batch = api.adminEvents()
                val items = batch.optJSONArray("items") ?: org.json.JSONArray()
                if (items.length() > 0) {
                    // Guardar el lote cifrado completo antes de confirmar el cursor.
                    check(store.persist(items)) { "No se pudo guardar localmente la bandeja administrativa." }
                    val deliveryId = batch.optString("deliveryId")
                    val ackId = store.getOrCreateAckId(deliveryId)
                    val ack = api.acknowledgeAdminEvents(batch, ackId)
                    check(ack.optBoolean("acknowledged", false)) { "El servidor no confirmó el ACK administrativo." }
                    store.clearAckId(deliveryId)
                }
                val saved = store.read()
                runOnUiThread { showAdminEventList(saved) }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(this, error.message ?: "No se pudo sincronizar la bandeja administrativa.", Toast.LENGTH_LONG).show()
                    if (error.message?.contains("401") == true) {
                        sessions.clear()
                        renderLogin()
                    }
                }
            }
        }.start()
    }

    private fun showAdminEventList(events: org.json.JSONArray) {
        if (events.length() == 0) {
            AlertDialog.Builder(this).setTitle("NOTIFICACIONES ADMIN")
                .setMessage("No hay eventos administrativos recibidos.")
                .setPositiveButton("CERRAR", null).show()
            return
        }
        val rows = (0 until events.length()).mapNotNull { events.optJSONObject(it) }
        val labels = rows.map { event ->
            val payload = event.optJSONObject("payload") ?: JSONObject()
            val business = payload.optString("businessName")
                .ifBlank { payload.optString("licenseId") }
                .ifBlank { event.optString("licenseId") }
            val type = when (event.optString("eventType")) {
                "registration_requested" -> "Nueva alta"
                "registration_status_changed" -> "Estado de alta actualizado"
                "payment_status_changed" -> "Estado de pago actualizado"
                "renewal_requested" -> "Renovación solicitada"
                "renewal_status_changed" -> "Renovación actualizada"
                "replacement_payment_requested" -> "Pago de reposición solicitado"
                "replacement_payment_status_changed" -> "Pago de reposición actualizado"
                "replacement_status_changed" -> "Reposición decidida"
                "patron_recovery_payment_requested" -> "Recuperación PATRÓN solicitada"
                "patron_recovery_status_changed" -> "Recuperación PATRÓN actualizada"
                "license_status_changed" -> "Estado de licencia actualizado"
                "license_period_changed" -> "Vigencia de licencia actualizada"
                "audit_recorded" -> "Registro de auditoría"
                "official_support_configuration_changed" -> "Configuración AYUDA actualizada"
                else -> event.optString("eventType")
            }
            val folio = payload.optString("folio")
                .ifBlank { payload.optString("requestId") }
                .ifBlank { payload.optString("paymentRequestId") }
            val summary = if (business.isBlank()) type else "$type • $business"
            if (folio.isBlank()) summary else "$summary • Folio: $folio"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("NOTIFICACIONES ADMIN (${rows.size})")
            .setItems(labels) { _, index -> showItemDetails("EVENTO ADMINISTRATIVO", rows[index]) }
            .setPositiveButton("CERRAR", null).show()
    }

    private fun loadPending(title: String, path: String, authorizePath: String) {
        val url = serverPrefs.getString("server_url", defaultServer) ?: defaultServer
        Toast.makeText(this, "Consultando $title…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val result = AdminApiClient(this, url).pending(path)
                val items = result.optJSONArray("items") ?: org.json.JSONArray()
                runOnUiThread {
                    if (items.length() == 0) {
                        AlertDialog.Builder(this).setTitle(title)
                            .setMessage("No hay solicitudes pendientes.")
                            .setPositiveButton("CERRAR", null).show()
                    } else {
                        val rows = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
                        val labels = rows.map { item ->
                            val name = item.optString("business_name")
                                .ifBlank { item.optString("businessName") }
                                .ifBlank { item.optString("license_id") }
                                .ifBlank { item.optString("id") }
                            val folio = item.optString("folio")
                                .ifBlank { item.optString("payment_request_id") }
                                .ifBlank { item.optString("requestId") }
                                .ifBlank { item.optString("id") }
                            "$name  •  ${item.optString("status", "pendiente")}  •  Folio: $folio"
                        }.toTypedArray()
                        AlertDialog.Builder(this).setTitle(title)
                            .setItems(labels) { _, index ->
                                val item = rows[index]
                                val requestId = item.optString("id")
                                val isPaidPendingLicense = path == "/v1/operator/licenses" &&
                                    item.optString("status") == "pending" &&
                                    item.optLong("setup_paid_at") > 0L &&
                                    item.optLong("first_rent_paid_at") > 0L &&
                                    item.optInt("active_patron_devices") == 0
                                if (isPaidPendingLicense) {
                                    showReissueDialog("REEMITIR ACTIVACIÓN", "/v1/operator/licenses/reissue-activation-code", "licenseId", requestId)
                                } else if (authorizePath == "/v1/operator/renewals/authorize" &&
                                    item.optString("status") == "authorized") {
                                    showReissueDialog("REEMITIR RENOVACIÓN", "/v1/operator/renewals/reissue-code", "requestId", requestId)
                                } else if (authorizePath.isBlank()) showItemDetails(title, item)
                                else showPaymentValidation(title, item, authorizePath)
                            }
                            .setNegativeButton("CERRAR", null).show()
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(this, error.message ?: "No se pudo consultar el servidor.", Toast.LENGTH_LONG).show()
                    if (error.message?.contains("401") == true) {
                        sessions.clear()
                        renderLogin()
                    }
                }
            }
        }.start()
    }

    private fun showItemDetails(title: String, item: JSONObject) {
        val message = buildString {
            append(item.toString(2))
            val details = item.optString("details_json")
            if (details.isNotBlank()) {
                try {
                    append("\n\nDETALLES\n").append(JSONObject(details).toString(2))
                } catch (_: Exception) {
                    append("\n\nDETALLES\n").append(details)
                }
            }
        }
        AlertDialog.Builder(this).setTitle(title)
            .setMessage(message)
            .setPositiveButton("CERRAR", null)
            .show()
    }

    private fun showReissueDialog(title: String, path: String, idField: String, idValue: String) {
        if (idValue.isBlank()) return
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val phone = field("Número oficial AYUDA (+52...)")
        form.addView(phone, matchWrap())
        form.addView(label(
            "Se invalidará el código anterior y se generará uno nuevo de un solo uso. El teléfono debe estar registrado como AYUDA oficial.",
            12f, muted, false
        ).apply { setPadding(0, dp(10), 0, 0) })
        val dialog = AlertDialog.Builder(this).setTitle(title)
            .setView(form)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("GENERAR CÓDIGO", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (phone.text.isNullOrBlank()) {
                    Toast.makeText(this, "Captura el teléfono oficial AYUDA.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val action = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                action.isEnabled = false
                Thread {
                    try {
                        val result = AdminApiClient(
                            this, serverPrefs.getString("server_url", defaultServer) ?: defaultServer
                        ).reissue(path, idField, idValue, phone.text.toString())
                        val code = result.optString("activationCode")
                            .ifBlank { result.optString("authorizationCode") }
                            .ifBlank { result.optString("recoveryCode") }
                        runOnUiThread {
                            dialog.dismiss()
                            AlertDialog.Builder(this).setTitle("CÓDIGO NUEVO")
                                .setMessage(buildString {
                                    append("Código de un solo uso:\n").append(code)
                                    append("\n\n").append(result.optString("note"))
                                    append("\n\nEntrégalo exclusivamente por el canal AYUDA oficial.")
                                })
                                .setPositiveButton("CERRAR", null).show()
                        }
                    } catch (error: Exception) {
                        runOnUiThread {
                            action.isEnabled = true
                            Toast.makeText(this, error.message ?: "No se pudo reemitir el código.", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }
        dialog.show()
    }

    private fun showPaymentValidation(title: String, item: JSONObject, authorizePath: String) {
        val requestId = item.optString("id")
        if (requestId.isBlank()) {
            AlertDialog.Builder(this).setTitle(title).setMessage("La solicitud no contiene un identificador válido.")
                .setPositiveButton("CERRAR", null).show()
            return
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val folio = item.optString("folio")
            .ifBlank { item.optString("payment_request_id") }
            .ifBlank { item.optString("requestId") }
            .ifBlank { requestId }
        val reference = field("Concepto/referencia exacta del banco").apply { setText(folio) }
        val phone = field("Número oficial AYUDA (+52...)")
        form.addView(label("FOLIO ÚNICO DE LA SOLICITUD", 12f, blue, true))
        form.addView(label(folio, 15f, Color.WHITE, true).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }, matchWrap())
        form.addView(reference, matchWrap().apply { topMargin = dp(9) })
        form.addView(phone, matchWrap().apply { topMargin = dp(9) })
        form.addView(label(
            "Comprueba el movimiento bancario real y confirma que el concepto/referencia sea exactamente igual al folio mostrado. El servidor rechazará teléfonos que no estén registrados como AYUDA oficial.",
            12f, muted, false
        ).apply { setPadding(0, dp(10), 0, 0) })
        val dialog = AlertDialog.Builder(this).setTitle("VALIDAR PAGO")
            .setMessage(
                "Empresa: " + item.optString("business_name", item.optString("businessName", "—")) +
                    "\nFolio único: " + folio
            )
            .setView(form)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("CONFIRMAR PAGO", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (reference.text.isNullOrBlank() || phone.text.isNullOrBlank()) {
                    Toast.makeText(this, "Captura la referencia y el teléfono oficial.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!paymentReferenceMatchesFolio(reference.text.toString(), folio)) {
                    Toast.makeText(this, "La referencia debe coincidir exactamente con el folio de la solicitud.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.isEnabled = false
                Thread {
                    try {
                        val result = AdminApiClient(
                            this, serverPrefs.getString("server_url", defaultServer) ?: defaultServer
                        ).authorize(authorizePath, requestId, reference.text.toString(), phone.text.toString())
                        val code = result.optString("activationCode")
                            .ifBlank { result.optString("authorizationCode") }
                            .ifBlank { result.optString("recoveryCode") }
                        runOnUiThread {
                            dialog.dismiss()
                            val message = buildString {
                                append("Pago validado y solicitud autorizada.")
                                if (code.isNotBlank()) append("\n\nCódigo de un solo uso:\n").append(code)
                                append("\n\n").append(result.optString("note"))
                                append("\n\nEntrega el código exclusivamente por el canal AYUDA oficial.")
                            }
                            AlertDialog.Builder(this).setTitle("OPERACIÓN CONFIRMADA")
                                .setMessage(message)
                                .setPositiveButton("CERRAR", null).show()
                        }
                    } catch (error: Exception) {
                        runOnUiThread {
                            button.isEnabled = true
                            Toast.makeText(this, error.message ?: "No se pudo autorizar el pago.", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }
        dialog.show()
    }

    private fun logo() = ImageView(this).apply {
        setImageResource(R.drawable.logo_sene_pv)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Logotipo SENE"
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setSingleLine(true)
        setTextColor(Color.WHITE)
        setHintTextColor(muted)
        setPadding(dp(13), dp(11), dp(13), dp(11))
        setBackgroundColor(panel)
    }

    private fun button(text: String) = Button(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.WHITE)
        setBackgroundColor(blue)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
