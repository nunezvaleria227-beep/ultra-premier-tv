package com.example.mitvplayer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Busca versiones nuevas en GitHub (la misma página de la que se descarga el APK),
 * las descarga y abre el instalador de Android. Se instala encima de la actual.
 */
object UpdateManager {
    private const val REPO = "nunezvaleria227-beep/ultra-premier-tv"
    private const val LATEST_PAGE = "https://github.com/$REPO/releases/latest"
    private const val APK_URL = "https://github.com/$REPO/releases/latest/download/UltraPremierTV.apk"

    /** Número de la versión instalada (el mismo que el último número de "1.0.N"). */
    fun currentVersion(context: Context): Long =
        try {
            PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        } catch (e: Exception) {
            0L
        }

    fun currentName(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

    /** Número de la última versión publicada, o null si no se pudo saber. */
    suspend fun latestVersion(): Int? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(LATEST_PAGE).header("User-Agent", Config.USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                // GitHub redirige a .../releases/tag/v1.0.N
                val location = response.header("Location")
                if (location == null) {
                    null
                } else {
                    Regex("/tag/v?(\\d+)\\.(\\d+)\\.(\\d+)").find(location)?.groupValues?.get(3)?.toIntOrNull()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Descarga el APK nuevo a la caché de la app. [onProgress] recibe el porcentaje (0–100). */
    suspend fun download(context: Context, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
        val target = File(dir, "UltraPremierTV.apk")
        val temp = File(dir, "descarga.part")
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val request = Request.Builder().url(APK_URL).header("User-Agent", Config.USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Respuesta vacía")
            val total = body.contentLength()
            body.byteStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != last) {
                                last = percent
                                onProgress(percent)
                            }
                        }
                        ensureActive()
                    }
                }
            }
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) throw IOException("No se pudo guardar el archivo")
        target
    }

    /**
     * Abre el instalador de Android. Devuelve false si todavía hace falta dar el permiso
     * "Instalar apps desconocidas" a Ultra Premier TV (se abre esa pantalla de ajustes).
     */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
                // Algunos equipos no tienen esa pantalla: el mensaje del aviso explica qué hacer.
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
