package com.sene.admin

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val navy = Color.rgb(8, 22, 34)
    private val panel = Color.rgb(17, 42, 59)
    private val blue = Color.rgb(24, 151, 224)
    private val green = Color.rgb(40, 190, 132)
    private val muted = Color.rgb(174, 194, 207)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.systemUiVisibility = 0
        renderHome()
    }

    private fun renderHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(root) }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), dp(20))
            setBackgroundColor(panel)
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo_sene_pv)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Logotipo SENE"
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(180), dp(110)))
        header.addView(label("SENE ADMIN", 25f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        header.addView(label("CENTRO DE ADMINISTRACIÓN", 12f, green, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(header, matchWrap())

        val status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.rgb(27, 51, 66))
        }
        status.addView(label("ESTADO DEL SISTEMA", 12f, green, true))
        status.addView(label(
            "La consola visual está creada. El acceso administrativo seguro y las operaciones del servidor todavía no están habilitados.",
            14f, Color.WHITE, false
        ).apply { setPadding(0, dp(7), 0, 0) })
        val statusParams = matchWrap().apply { topMargin = dp(16) }
        root.addView(status, statusParams)

        root.addView(label("MÓDULOS DE ADMINISTRACIÓN", 14f, muted, true).apply {
            setPadding(dp(2), dp(22), 0, dp(10))
        })
        val modules = listOf(
            "EMPRESAS Y LICENCIAS" to "Altas, reservas de nombre, slots y vigencias.",
            "PAGOS Y FOLIOS" to "Validación de transferencias, referencias y comprobantes.",
            "RENOVACIONES" to "Solicitudes, pagos y autorizaciones de mensualidad.",
            "REPOSICIONES Y RECUPERACIONES" to "Equipos perdidos, códigos y recuperación de licencias.",
            "NOTIFICACIONES Y AUDITORÍA" to "Eventos administrativos, historial y trazabilidad."
        )
        modules.forEach { (title, description) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
                setBackgroundColor(panel)
                isClickable = true
                isFocusable = true
                setOnClickListener { showPendingModule(title, description) }
            }
            card.addView(label(title, 15f, Color.WHITE, true))
            card.addView(label(description, 13f, muted, false).apply {
                setPadding(0, dp(5), 0, 0)
            })
            card.addView(label("PENDIENTE DE CONEXIÓN SEGURA", 10f, blue, true).apply {
                setPadding(0, dp(10), 0, 0)
            })
            root.addView(card, matchWrap().apply { topMargin = dp(9) })
        }
        root.addView(label(
            "SEGURIDAD: esta versión no contiene tokens administrativos ni realiza cambios en licencias o pagos.",
            11f, muted, false
        ).apply { setPadding(dp(4), dp(22), dp(4), 0) })
        setContentView(scroll)
    }

    private fun showPendingModule(title: String, description: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("$description\n\nEste módulo se habilitará cuando esté integrado el inicio de sesión administrativo firmado y el servicio correspondiente. No se ha enviado ninguna operación.")
            .setPositiveButton("ENTENDIDO", null)
            .show()
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
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
