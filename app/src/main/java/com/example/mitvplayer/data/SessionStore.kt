package com.example.mitvplayer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda la sesión. Usuario, contraseña y URL se cifran con una llave del Android Keystore.
 * Si el Keystore del dispositivo falla, simplemente no se recuerda la sesión
 * (nunca se guardan las credenciales en texto plano).
 */
class SessionStore(private val context: Context) {

    private val secure: SharedPreferences? = createSecurePrefs()
    private val settings: SharedPreferences =
        context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    var liveFormat: String?
        get() = settings.getString(KEY_LIVE_FORMAT, null)
        set(value) {
            settings.edit().putString(KEY_LIVE_FORMAT, value).apply()
        }

    fun load(): Credentials? {
        val prefs = secure ?: return null
        val server = prefs.getString(KEY_SERVER, null)
        val user = prefs.getString(KEY_USER, null)
        val pass = prefs.getString(KEY_PASS, null)
        if (server.isNullOrBlank() || user.isNullOrBlank() || pass == null) return null
        return Credentials(server, user, pass)
    }

    fun save(credentials: Credentials) {
        secure?.edit()
            ?.putString(KEY_SERVER, credentials.server)
            ?.putString(KEY_USER, credentials.username)
            ?.putString(KEY_PASS, credentials.password)
            ?.apply()
    }

    fun clear() {
        secure?.edit()?.clear()?.apply()
    }

    private fun createSecurePrefs(): SharedPreferences? {
        repeat(2) { attempt ->
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    SECURE_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // En algunos equipos el archivo cifrado queda corrupto: se borra y se reintenta una vez.
                if (attempt == 0) context.deleteSharedPreferences(SECURE_FILE)
            }
        }
        return null
    }

    private companion object {
        const val SECURE_FILE = "session_secure"
        const val SETTINGS_FILE = "settings"
        const val KEY_SERVER = "server"
        const val KEY_USER = "username"
        const val KEY_PASS = "password"
        const val KEY_LIVE_FORMAT = "live_format"
    }
}
