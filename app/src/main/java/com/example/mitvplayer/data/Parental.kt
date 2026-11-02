package com.example.mitvplayer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Control parental.
 *
 * Con un PIN puesto, las categorías para adultos (las que en su nombre dicen XXX, +18, adultos, erótico…)
 * se esconden en todas partes: listas, buscador, guía y recientes. Se ven de nuevo al escribir el PIN
 * y vuelven a esconderse al pulsar "Bloquear ahora", al cerrar sesión o al cerrar la app.
 *
 * El PIN no se guarda tal cual: solo su huella con una sal al azar.
 */
class ParentalControl(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("parental", Context.MODE_PRIVATE)

    /** Hay un PIN puesto (el control parental está activado). */
    var hasPin by mutableStateOf(prefs.getString(KEY_HASH, null) != null)
        private set

    /** El PIN ya se escribió en esta sesión: se muestra todo. */
    var unlocked by mutableStateOf(false)
        private set

    private var failures = 0
    private var blockedUntil = 0L

    /** Verdadero mientras el contenido para adultos debe estar escondido. */
    val locked: Boolean get() = hasPin && !unlocked

    /** Segundos que faltan para poder intentar otra vez (0 = ya se puede). */
    fun waitSeconds(): Int {
        val left = blockedUntil - System.currentTimeMillis()
        return if (left <= 0) 0 else ((left + 999) / 1000).toInt()
    }

    /** Pone o cambia el PIN (4 dígitos). Con un PIN nuevo el contenido para adultos queda escondido de inmediato. */
    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        prefs.edit().putString(KEY_SALT, salt).putString(KEY_HASH, hash(salt, pin)).apply()
        hasPin = true
        failures = 0
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val saved = prefs.getString(KEY_HASH, null) ?: return false
        if (waitSeconds() > 0) return false
        val ok = MessageDigest.isEqual(hash(salt, pin).toByteArray(), saved.toByteArray())
        if (ok) {
            failures = 0
        } else {
            failures++
            if (failures >= MAX_FAILURES) {
                blockedUntil = System.currentTimeMillis() + LOCK_MS
                failures = 0
            }
        }
        return ok
    }

    /** Escribe el PIN para ver todo. Devuelve si era correcto. */
    fun unlock(pin: String): Boolean {
        val ok = verify(pin)
        if (ok) unlocked = true
        return ok
    }

    fun lock() {
        unlocked = false
    }

    /** Quita el PIN (hay que haberlo comprobado antes). */
    fun removePin() {
        prefs.edit().remove(KEY_SALT).remove(KEY_HASH).apply()
        hasPin = false
        unlocked = false
    }

    /** ¿Hay que esconder una categoría con este nombre ahora mismo? */
    fun blocks(categoryName: String): Boolean = locked && isAdultName(categoryName)

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val MAX_FAILURES = 5
        private const val LOCK_MS = 60_000L

        private val ADULT = Regex(
            "(\\bxxx\\b|\\badult(?!\\s*swim)\\w*|\\bporn\\w*|\\berotic\\w*|\\bhentai\\b|\\bplayboy\\b|" +
                "\\bsexo\\b|\\bsexy\\b|\\bsex\\b|\\bxvideos\\b|\\bonlyfans\\b|\\bpara mayores\\b|\\bmayores de 18\\b|" +
                "(^|\\s|\\[|\\()\\+\\s?18|(^|[^0-9])18\\s?\\+)"
        )

        /** Nombre de categoría de contenido para adultos (sin acentos ni mayúsculas, como en la búsqueda). */
        fun isAdultName(name: String): Boolean = ADULT.containsMatchIn(searchKey(name))
    }
}
