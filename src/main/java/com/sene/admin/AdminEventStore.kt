package com.sene.admin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Persistencia cifrada de eventos administrativos antes de confirmar su ACK. */
class AdminEventStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sene_admin_event_store", Context.MODE_PRIVATE)
    private val alias = "sene_admin_events_aes_v1"

    fun persist(batch: JSONArray): Boolean {
        val merged = LinkedHashMap<String, JSONObject>()
        val previous = read()
        for (i in 0 until previous.length()) {
            val item = previous.optJSONObject(i) ?: continue
            val id = item.optString("eventId")
            if (id.isNotBlank()) merged[id] = item
        }
        for (i in 0 until batch.length()) {
            val item = batch.optJSONObject(i) ?: continue
            val id = item.optString("eventId")
            if (id.isNotBlank()) merged[id] = item
        }
        val ordered = merged.values.sortedBy { it.optLong("cursor", 0L) }.takeLast(500)
        val output = JSONArray()
        ordered.forEach { output.put(it) }
        return encryptAndCommit(output.toString())
    }

    fun read(): JSONArray {
        val ivText = prefs.getString("events_iv", null) ?: return JSONArray()
        val encryptedText = prefs.getString("events_data", null) ?: return JSONArray()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP))
            )
            JSONArray(String(
                cipher.doFinal(Base64.decode(encryptedText, Base64.NO_WRAP)),
                Charsets.UTF_8
            ))
        } catch (_: Exception) {
            prefs.edit().remove("events_iv").remove("events_data").commit()
            JSONArray()
        }
    }

    fun getOrCreateAckId(deliveryId: String): String {
        require(deliveryId.isNotBlank() && deliveryId.length <= 120)
        val key = "pending_ack_" + deliveryId
        return prefs.getString(key, null) ?: java.util.UUID.randomUUID().toString().also {
            check(prefs.edit().putString(key, it).commit()) {
                "No se pudo guardar el ACK pendiente para reintentar"
            }
        }
    }

    fun clearAckId(deliveryId: String) {
        prefs.edit().remove("pending_ack_" + deliveryId).commit()
    }

    private fun encryptAndCommit(value: String): Boolean {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return prefs.edit()
            .putString("events_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("events_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
}
