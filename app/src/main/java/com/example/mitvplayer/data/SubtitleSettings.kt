package com.example.mitvplayer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Datos de la cuenta gratuita de OpenSubtitles (la clave es obligatoria; usuario y contraseña, opcionales). */
data class SubtitleCredentials(val apiKey: String, val username: String?, val password: String?)

/**
 * Ajustes de subtítulos: idioma preferido y cuenta de OpenSubtitles.
 * La clave y la contraseña se guardan cifradas con el Android Keystore (nunca en texto plano).
 */
object SubtitleSettings {
    private const val PLAIN_FILE = "subtitle_settings"
    private const val SECURE_FILE = "subtitle_secure"
    private const val KEY_LANG = "language"
    private const val KEY_API = "api_key"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"

    private fun plain(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)

    private fun secure(context: Context): SharedPreferences? {
        val app = context.applicationContext
        repeat(2) { attempt ->
            try {
                val masterKey = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                return EncryptedSharedPreferences.create(
                    app,
                    SECURE_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                if (attempt == 0) app.deleteSharedPreferences(SECURE_FILE)
            }
        }
        return null
    }

    /** "off", "es" o "en". */
    fun language(context: Context): String = plain(context).getString(KEY_LANG, "off") ?: "off"

    fun setLanguage(context: Context, value: String) {
        plain(context).edit().putString(KEY_LANG, value).apply()
    }

    fun credentials(context: Context): SubtitleCredentials? {
        val prefs = secure(context) ?: return null
        val key = prefs.getString(KEY_API, null)?.trim()
        if (key.isNullOrEmpty()) return null
        return SubtitleCredentials(
            apiKey = key,
            username = prefs.getString(KEY_USER, null)?.trim()?.ifEmpty { null },
            password = prefs.getString(KEY_PASS, null)?.ifEmpty { null }
        )
    }

    /** Devuelve false si el equipo no pudo guardar de forma cifrada. */
    fun saveCredentials(context: Context, apiKey: String, username: String, password: String): Boolean {
        val prefs = secure(context) ?: return false
        prefs.edit()
            .putString(KEY_API, apiKey.trim())
            .putString(KEY_USER, username.trim())
            .putString(KEY_PASS, password)
            .apply()
        return true
    }
}
