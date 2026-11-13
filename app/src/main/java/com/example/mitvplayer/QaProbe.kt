package com.example.mitvplayer

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Mediciones para la prueba automática en el emulador (solo se encienden en la compilación de prueba,
 * con Config.QA_BUILD = true). En la app de los clientes no hace nada.
 *
 *  - NAV: cuánto tarda en dibujarse cada pantalla que se abre.
 *  - LAG: cuando el hilo principal se queda trabado (la pantalla no responde), cuánto tiempo y en qué código.
 */
object QaProbe {
    private const val TAG = "QA"

    @Volatile
    var enabled = false
        private set

    fun start() {
        if (enabled) return
        enabled = true
        startWatchdog()
        log("INICIO pid=${android.os.Process.myPid()}")
    }

    fun log(message: String) {
        if (enabled) Log.i(TAG, "t=${SystemClock.elapsedRealtime()} $message")
    }

    // ------------------------------------------------------------------ pantallas

    @Volatile
    private var openedAt = 0L

    @Volatile
    private var openedName = ""

    fun opened(name: String) {
        if (!enabled) return
        openedAt = SystemClock.uptimeMillis()
        openedName = name
        log("ABRIR $name")
    }

    /** Primer cuadro dibujado de una pantalla. */
    fun shown(name: String) {
        if (!enabled) return
        val since = if (openedName == name && openedAt > 0) SystemClock.uptimeMillis() - openedAt else -1
        log("NAV $name dibujada${if (since >= 0) " en $since ms" else ""}")
    }

    /** Una sección ya tiene su contenido en pantalla. */
    fun ready(name: String, detail: String) {
        if (!enabled) return
        val since = if (openedAt > 0) SystemClock.uptimeMillis() - openedAt else -1
        log("LISTO $name ($detail)${if (since >= 0) " a $since ms de abrir $openedName" else ""}")
    }

    // ------------------------------------------------------------ hilo principal

    private fun startWatchdog() {
        val looper = Looper.getMainLooper()
        val main = Handler(looper)
        val mainThread = looper.thread
        val beat = AtomicLong(SystemClock.uptimeMillis())
        val pending = AtomicBoolean(false)
        val ping = Runnable {
            beat.set(SystemClock.uptimeMillis())
            pending.set(false)
        }
        val ownPrefix = QaProbe::class.java.name.substringBeforeLast('.')
        Thread({
            var stallFrom = 0L
            val samples = HashMap<String, Int>()
            var count = 0
            while (true) {
                if (pending.compareAndSet(false, true)) main.post(ping)
                try {
                    Thread.sleep(16)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                val now = SystemClock.uptimeMillis()
                val last = beat.get()
                if (now - last > 64) {
                    if (stallFrom == 0L) stallFrom = last
                    val key = summarize(mainThread.stackTrace, ownPrefix)
                    samples[key] = (samples[key] ?: 0) + 1
                    count++
                } else if (stallFrom != 0L) {
                    val duration = last - stallFrom
                    if (duration >= 100) {
                        val top = samples.entries.sortedByDescending { it.value }.take(3)
                            .joinToString(" || ") { "${it.value * 100 / count.coerceAtLeast(1)}% ${it.key}" }
                        log("LAG $duration ms :: $top")
                    }
                    stallFrom = 0L
                    samples.clear()
                    count = 0
                }
            }
        }, "qa-watchdog").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /** Las líneas que más dicen: lo que se ejecutaba arriba y la primera línea del código propio. */
    private fun summarize(stack: Array<StackTraceElement>, ownPrefix: String): String {
        if (stack.isEmpty()) return "?"
        fun fmt(e: StackTraceElement) = "${e.className.substringAfterLast('.')}.${e.methodName}:${e.lineNumber}"
        val top = stack.take(2).joinToString(" < ") { fmt(it) }
        val own = stack.filter { it.className.startsWith(ownPrefix) }.take(3).joinToString(" < ") { fmt(it) }
        return if (own.isEmpty()) top else "$top ... $own"
    }
}
