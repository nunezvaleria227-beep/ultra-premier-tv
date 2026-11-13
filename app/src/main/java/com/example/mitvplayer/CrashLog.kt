package com.example.mitvplayer

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Si la app se cierra por un error, guarda qué pasó para mostrarlo la próxima vez que se abra.
 * Así el cliente puede mandar una foto del mensaje y se sabe exactamente qué arreglar.
 */
object CrashLog {
    private const val FILE = "ultimo_cierre.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(app.filesDir, FILE).writeText(describe(app, thread, error))
            } catch (ignored: Throwable) {
                // Si no se puede guardar, se sigue con el cierre normal.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Texto corto del último cierre, o null si la app no se cerró sola. */
    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE)
        return if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    private fun describe(context: Context, thread: Thread, error: Throwable): String {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (ignored: Exception) {
            "?"
        }
        val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())
        val root = generateSequence(error) { it.cause }.last()
        val lines = mutableListOf<String>()
        lines += "Versión $version · $time · hilo ${thread.name}"
        lines += "${error.javaClass.simpleName}: ${error.message.orEmpty().take(300)}"
        if (root !== error) lines += "Causa: ${root.javaClass.simpleName}: ${root.message.orEmpty().take(300)}"
        // Las líneas del código propio de la app son las que dicen dónde falló.
        val own = (error.stackTrace + root.stackTrace)
            .filter { it.className.startsWith("com.example.mitvplayer") }
            .distinct()
            .take(6)
        val frames = own.ifEmpty { error.stackTrace.take(6) }
        frames.forEach { lines += "  en ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        return lines.joinToString("\n")
    }
}
