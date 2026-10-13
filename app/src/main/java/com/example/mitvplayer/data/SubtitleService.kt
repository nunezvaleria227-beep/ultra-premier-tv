package com.example.mitvplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

/** Qué película o episodio se quiere subtitular. */
data class SubtitleQuery(
    /** "movie" o "episode". */
    val kind: String,
    /** Nombres a probar, del más fiable (título original) al menos. */
    val titles: List<String>,
    val year: Int? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** Nombre del archivo guardado, por ejemplo "movie_1234" o "episode_88". */
    val cacheKey: String
)

/** [fatal] = no tiene caso seguir probando (clave mala, sin descargas…). */
class SubtitleException(message: String, val fatal: Boolean = false) : Exception(message)

/** Subtítulos ya guardados en el equipo (así no se vuelven a descargar). */
object SubtitleFiles {
    private fun dir(context: Context): File = File(context.applicationContext.filesDir, "subs").also { it.mkdirs() }

    private fun safe(key: String): String = key.replace(Regex("[^A-Za-z0-9_-]"), "_")

    fun file(context: Context, cacheKey: String, lang: String): File = File(dir(context), "${safe(cacheKey)}.$lang.srt")

    /** Idioma → archivo, de lo que ya hay guardado para esa película o episodio. */
    fun saved(context: Context, cacheKey: String): Map<String, File> {
        val prefix = safe(cacheKey) + "."
        val out = LinkedHashMap<String, File>()
        dir(context).listFiles()?.forEach { f ->
            if (f.name.startsWith(prefix) && f.name.endsWith(".srt") && f.length() > 0) {
                val lang = f.name.removePrefix(prefix).removeSuffix(".srt")
                if (lang.isNotEmpty()) out[lang] = f
            }
        }
        return out
    }
}

/**
 * Busca y descarga subtítulos en OpenSubtitles (API oficial v1, con la clave gratuita de la persona).
 * Prefiere los hechos por personas; si no hay, usa los traducidos por IA que ofrece el propio servicio.
 */
object SubtitleService {
    private const val BASE = "https://api.opensubtitles.com/api/v1"
    private const val AGENT = "UltraPremierTV v1.0"
    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile private var token: String? = null
    @Volatile private var tokenKey: String? = null

    private class Hit(val fileId: Long, val score: Double, val human: Boolean)

    fun languageName(lang: String): String = if (lang == "es") "español" else if (lang == "en") "inglés" else lang

    /** Descarga (o reutiliza) el subtítulo de [lang] ("es" o "en"). Lanza [SubtitleException] con un mensaje claro si no se puede. */
    suspend fun fetch(context: Context, query: SubtitleQuery, lang: String): File = withContext(Dispatchers.IO) {
        val out = SubtitleFiles.file(context, query.cacheKey, lang)
        if (out.exists() && out.length() > 0) return@withContext out

        val credentials = SubtitleSettings.credentials(context)
            ?: throw SubtitleException("Falta la clave de OpenSubtitles. Ponla en Ajustes → Subtítulos en internet.", fatal = true)

        val hits = search(credentials, query, lang)
        if (hits.isEmpty()) throw SubtitleException("No hay subtítulos en ${languageName(lang)} para este título.")

        var failure: SubtitleException? = null
        for (hit in hits.take(3)) {
            try {
                val text = download(credentials, hit.fileId)
                if (!text.contains("-->")) throw SubtitleException("El archivo de subtítulos llegó dañado.")
                out.writeText(text, Charsets.UTF_8)
                return@withContext out
            } catch (e: SubtitleException) {
                failure = e
                // Sin descargas o con la clave mal no sirve probar otro archivo.
                if (e.fatal) throw e
            }
        }
        throw failure ?: SubtitleException("No se pudo descargar el subtítulo.")
    }

    // ---------------------------------------------------------------- búsqueda

    private fun search(c: SubtitleCredentials, q: SubtitleQuery, lang: String): List<Hit> {
        val base = sortedMapOf("languages" to lang, "type" to q.kind)
        if (q.kind == "episode") {
            q.season?.let { base["season_number"] = it.toString() }
            q.episode?.let { base["episode_number"] = it.toString() }
        } else {
            q.year?.let { base["year"] = it.toString() }
        }

        val attempts = ArrayList<Map<String, String>>()
        val imdb = q.imdbId?.filter { it.isDigit() }?.trimStart('0')?.ifEmpty { null }
        val tmdb = q.tmdbId?.filter { it.isDigit() }?.ifEmpty { null }
        if (q.kind == "episode") {
            if (imdb != null) attempts += base + ("parent_imdb_id" to imdb)
            if (tmdb != null) attempts += base + ("parent_tmdb_id" to tmdb)
        } else {
            if (imdb != null) attempts += base + ("imdb_id" to imdb)
            if (tmdb != null) attempts += base + ("tmdb_id" to tmdb)
        }
        for (title in q.titles.distinct().take(2)) {
            if (title.isNotBlank()) attempts += base + ("query" to title.lowercase())
        }

        var lastError: SubtitleException? = null
        for (params in attempts) {
            try {
                val hits = runSearch(c, params)
                if (hits.isNotEmpty()) return hits
            } catch (e: SubtitleException) {
                lastError = e
                if (e.fatal) throw e
            }
        }
        if (lastError != null) throw lastError
        return emptyList()
    }

    private fun runSearch(c: SubtitleCredentials, params: Map<String, String>): List<Hit> {
        val url = "$BASE/subtitles".toHttpUrl().newBuilder().apply {
            params.toSortedMap().forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder().url(url).headers(c).get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            checkStatus(response.code, text)
            return parseHits(JSONObject(text.ifEmpty { "{}" }))
        }
    }

    private fun parseHits(json: JSONObject): List<Hit> {
        val data = json.optJSONArray("data") ?: return emptyList()
        val hits = ArrayList<Hit>()
        for (i in 0 until data.length()) {
            val attrs = data.optJSONObject(i)?.optJSONObject("attributes") ?: continue
            val fileId = attrs.optJSONArray("files")?.optJSONObject(0)?.optLong("file_id", 0L) ?: 0L
            if (fileId <= 0L) continue
            val human = !attrs.optBoolean("ai_translated", false) && !attrs.optBoolean("machine_translated", false)
            val score = attrs.optDouble("download_count", 0.0) +
                attrs.optDouble("ratings", 0.0) * 1000 +
                (if (attrs.optBoolean("from_trusted", false)) 100_000.0 else 0.0)
            hits += Hit(fileId, score, human)
        }
        return hits.sortedWith(compareByDescending<Hit> { it.human }.thenByDescending { it.score })
    }

    // ---------------------------------------------------------------- descarga

    private fun download(c: SubtitleCredentials, fileId: Long): String {
        val link = requestLink(c, fileId, allowRetry = true)
        val request = Request.Builder().url(link).header("User-Agent", AGENT).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw SubtitleException("No se pudo bajar el archivo de subtítulos (HTTP ${response.code}).")
            val bytes = response.body?.bytes() ?: throw SubtitleException("El archivo de subtítulos llegó vacío.")
            return decode(bytes)
        }
    }

    private fun requestLink(c: SubtitleCredentials, fileId: Long, allowRetry: Boolean): String {
        val auth = loginToken(c)
        val body = JSONObject().put("file_id", fileId).put("sub_format", "srt").toString().toRequestBody(JSON)
        val builder = Request.Builder().url("$BASE/download").headers(c).post(body)
        if (auth != null) builder.header("Authorization", "Bearer $auth")
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 && allowRetry && auth != null) {
                token = null
                return requestLink(c, fileId, allowRetry = false)
            }
            checkStatus(response.code, text)
            val link = JSONObject(text.ifEmpty { "{}" }).optString("link")
            if (link.isEmpty()) throw SubtitleException("OpenSubtitles no dio el enlace de descarga.")
            return link
        }
    }

    private fun loginToken(c: SubtitleCredentials): String? {
        val user = c.username
        val pass = c.password
        if (user.isNullOrEmpty() || pass.isNullOrEmpty()) return null
        val cacheId = "${c.apiKey}|$user"
        token?.let { if (tokenKey == cacheId) return it }
        val body = JSONObject().put("username", user).put("password", pass).toString().toRequestBody(JSON)
        val request = Request.Builder().url("$BASE/login").headers(c).post(body).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 400) {
                throw SubtitleException("Usuario o contraseña de OpenSubtitles incorrectos (revisa Ajustes → Subtítulos en internet).", fatal = true)
            }
            checkStatus(response.code, text)
            val value = JSONObject(text.ifEmpty { "{}" }).optString("token")
            if (value.isEmpty()) throw SubtitleException("OpenSubtitles no aceptó el inicio de sesión.")
            token = value
            tokenKey = cacheId
            return value
        }
    }

    // ------------------------------------------------------------------ comunes

    private fun Request.Builder.headers(c: SubtitleCredentials): Request.Builder =
        header("Api-Key", c.apiKey)
            .header("User-Agent", AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

    private fun checkStatus(code: Int, text: String) {
        when {
            code in 200..299 -> Unit
            code == 401 || code == 403 -> throw SubtitleException("OpenSubtitles rechazó la clave (error $code). Revisa la clave en Ajustes → Subtítulos en internet.", fatal = true)
            code == 406 -> throw SubtitleException("Ya usaste todas las descargas de hoy en OpenSubtitles. Se renuevan cada 24 horas (con usuario y contraseña tienes más).", fatal = true)
            code == 429 -> throw SubtitleException("OpenSubtitles pide esperar unos segundos. Inténtalo de nuevo.")
            else -> {
                val message = try { JSONObject(text).optString("message") } catch (e: Exception) { "" }
                throw SubtitleException("OpenSubtitles respondió con error $code. $message".trim())
            }
        }
    }

    /** UTF-8 si lo es; si no, Windows-1252 (así vienen casi todos los subtítulos viejos en español). */
    private fun decode(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}
