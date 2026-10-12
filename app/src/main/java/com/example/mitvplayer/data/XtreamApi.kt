package com.example.mitvplayer.data

import android.net.Uri
import android.util.Base64
import com.example.mitvplayer.Config
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class AuthException(message: String) : IOException(message)

/**
 * Cliente de la API estándar de Xtream Codes (player_api.php).
 *
 * Las respuestas de estos servidores son poco consistentes (números como texto,
 * arreglos vacíos que llegan como objetos, etc.), por eso se parsea con org.json
 * de forma tolerante en vez de usar un mapeo estricto.
 */
class XtreamApi(private val client: OkHttpClient) {

    // ---------------------------------------------------------------- servidor

    /** Limpia lo que el usuario escriba: agrega http:// y quita /player_api.php, /get.php, etc. */
    fun normalizeServer(raw: String): String {
        var text = raw.trim()
        if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
            text = "http://$text"
        }
        val url = text.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("La URL del servidor no es válida")

        var path = url.encodedPath
        for (ending in listOf("/player_api.php", "/get.php", "/panel_api.php", "/xmltv.php")) {
            val index = path.indexOf(ending)
            if (index >= 0) path = path.substring(0, index)
        }
        path = path.trimEnd('/')

        val defaultPort = (url.scheme == "http" && url.port == 80) || (url.scheme == "https" && url.port == 443)
        val port = if (defaultPort) "" else ":${url.port}"
        return "${url.scheme}://${url.host}$port$path"
    }

    // ------------------------------------------------------------------- login

    fun login(c: Credentials): AccountInfo {
        val root = parseObject(get(apiUrl(c, null)))
        val user = root.optJSONObject("user_info")
            ?: throw AuthException("Usuario o contraseña incorrectos")
        if (user.optInt("auth", 0) != 1) {
            throw AuthException("Usuario o contraseña incorrectos")
        }
        val status = user.str("status") ?: "Active"
        if (!status.equals("Active", ignoreCase = true)) {
            throw AuthException("La cuenta no está activa (estado: $status)")
        }
        val formats = user.optJSONArray("allowed_output_formats")?.let { array ->
            (0 until array.length()).map { array.optString(it) }
        } ?: emptyList()

        return AccountInfo(
            username = user.str("username") ?: c.username,
            status = status,
            expiresAt = user.str("exp_date")?.toLongOrNull(),
            maxConnections = user.str("max_connections")?.toIntOrNull(),
            activeConnections = user.str("active_cons")?.toIntOrNull(),
            allowedFormats = formats
        )
    }

    // -------------------------------------------------------------- categorías

    fun categories(c: Credentials, action: String): List<Category> =
        parseArray(get(apiUrl(c, action))).mapObjects { o ->
            val id = o.str("category_id") ?: return@mapObjects null
            Category(id = id, name = o.str("category_name") ?: id)
        }

    // ------------------------------------------------------------------ en vivo

    fun liveStreams(c: Credentials, categoryId: String): List<LiveChannel> =
        parseArray(get(apiUrl(c, "get_live_streams", mapOf("category_id" to categoryId)))).mapObjects { o ->
            val id = o.str("stream_id") ?: return@mapObjects null
            LiveChannel(
                id = id,
                name = o.str("name") ?: "Canal $id",
                icon = o.str("stream_icon"),
                categoryId = o.str("category_id") ?: categoryId,
                number = o.optInt("num", 0)
            )
        }

    fun shortEpg(c: Credentials, streamId: String, limit: Int = 2): List<EpgEntry> {
        val root = parseObject(
            get(apiUrl(c, "get_short_epg", mapOf("stream_id" to streamId, "limit" to limit.toString())))
        )
        val listings = root.optJSONArray("epg_listings") ?: return emptyList()
        return listings.mapObjects { o ->
            val start = o.str("start_timestamp")?.toLongOrNull() ?: return@mapObjects null
            val end = o.str("stop_timestamp")?.toLongOrNull() ?: return@mapObjects null
            EpgEntry(
                title = decodeBase64(o.str("title")),
                description = decodeBase64(o.str("description")),
                start = start,
                end = end
            )
        }
    }

    // ---------------------------------------------------------------- películas

    fun vodStreams(c: Credentials, categoryId: String): List<Movie> =
        parseArray(get(apiUrl(c, "get_vod_streams", mapOf("category_id" to categoryId)))).mapObjects { o ->
            val id = o.str("stream_id") ?: return@mapObjects null
            Movie(
                id = id,
                name = o.str("name") ?: "Película $id",
                poster = o.str("stream_icon"),
                categoryId = o.str("category_id") ?: categoryId,
                extension = o.str("container_extension") ?: "mp4",
                rating = o.str("rating")
            )
        }

    fun vodInfo(c: Credentials, vodId: String): MovieInfo {
        val root = parseObject(get(apiUrl(c, "get_vod_info", mapOf("vod_id" to vodId))))
        val info = root.optJSONObject("info") ?: JSONObject()
        return MovieInfo(
            plot = info.str("plot") ?: info.str("description"),
            genre = info.str("genre"),
            releaseDate = info.str("releasedate") ?: info.str("release_date"),
            duration = info.str("duration"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director")
        )
    }

    // ------------------------------------------------------------------- series

    fun seriesList(c: Credentials, categoryId: String): List<SeriesItem> =
        parseArray(get(apiUrl(c, "get_series", mapOf("category_id" to categoryId)))).mapObjects { o ->
            val id = o.str("series_id") ?: return@mapObjects null
            SeriesItem(
                id = id,
                name = o.str("name") ?: "Serie $id",
                cover = o.str("cover"),
                categoryId = o.str("category_id") ?: categoryId,
                plot = o.str("plot"),
                rating = o.str("rating")
            )
        }

    fun seriesInfo(c: Credentials, seriesId: String): SeriesDetail {
        val root = parseObject(get(apiUrl(c, "get_series_info", mapOf("series_id" to seriesId))))
        val info = root.optJSONObject("info")
        val episodes = mutableListOf<Episode>()

        fun addEpisode(o: JSONObject, seasonFallback: Int) {
            val id = o.str("id") ?: return
            val episodeInfo = o.optJSONObject("info")
            episodes += Episode(
                id = id,
                season = o.str("season")?.toIntOrNull() ?: seasonFallback,
                number = o.str("episode_num")?.toIntOrNull() ?: 0,
                title = o.str("title") ?: "Episodio",
                extension = o.str("container_extension") ?: "mp4",
                plot = episodeInfo?.str("plot"),
                image = episodeInfo?.str("movie_image"),
                durationSecs = episodeInfo?.str("duration_secs")?.toIntOrNull()
            )
        }

        // "episodes" puede venir como objeto {"1":[...], "2":[...]} o como arreglo de arreglos.
        when (val raw = root.opt("episodes")) {
            is JSONObject -> {
                raw.keys().forEach { key ->
                    val list = raw.optJSONArray(key) ?: return@forEach
                    for (i in 0 until list.length()) {
                        list.optJSONObject(i)?.let { addEpisode(it, key.toIntOrNull() ?: 0) }
                    }
                }
            }
            is JSONArray -> {
                for (s in 0 until raw.length()) {
                    val list = raw.optJSONArray(s) ?: continue
                    for (i in 0 until list.length()) {
                        list.optJSONObject(i)?.let { addEpisode(it, s + 1) }
                    }
                }
            }
        }

        val seasons = episodes
            .groupBy { it.season }
            .toSortedMap()
            .mapValues { (_, list) -> list.sortedBy { it.number } }

        return SeriesDetail(
            plot = info?.str("plot"),
            cover = info?.str("cover"),
            genre = info?.str("genre"),
            seasons = seasons
        )
    }

    // ----------------------------------------------------------- URLs de video

    fun liveUrl(c: Credentials, streamId: String, format: String): String =
        "${c.server}/live/${Uri.encode(c.username)}/${Uri.encode(c.password)}/$streamId.$format"

    fun movieUrl(c: Credentials, streamId: String, extension: String): String =
        "${c.server}/movie/${Uri.encode(c.username)}/${Uri.encode(c.password)}/$streamId.$extension"

    fun episodeUrl(c: Credentials, episodeId: String, extension: String): String =
        "${c.server}/series/${Uri.encode(c.username)}/${Uri.encode(c.password)}/$episodeId.$extension"

    // ------------------------------------------------------------------ interno

    private fun apiUrl(c: Credentials, action: String?, extra: Map<String, String> = emptyMap()): HttpUrl {
        val builder = (c.server + "/player_api.php").toHttpUrl().newBuilder()
            .addQueryParameter("username", c.username)
            .addQueryParameter("password", c.password)
        if (action != null) builder.addQueryParameter("action", action)
        extra.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private fun get(url: HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Config.USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw AuthException("Acceso denegado por el servidor (${response.code})")
            }
            if (!response.isSuccessful) {
                throw IOException("El servidor respondió con error ${response.code}")
            }
            return response.body?.string() ?: throw IOException("Respuesta vacía del servidor")
        }
    }

    private fun clean(body: String): String = body.trimStart('﻿', ' ', '\n', '\r', '\t').trimEnd()

    private fun parseObject(body: String): JSONObject =
        try {
            JSONObject(clean(body))
        } catch (e: JSONException) {
            throw IOException("El servidor no respondió como un portal Xtream Codes. Revisa la URL.")
        }

    private fun parseArray(body: String): JSONArray {
        val text = clean(body)
        if (!text.startsWith("[")) return JSONArray()
        return try {
            JSONArray(text)
        } catch (e: JSONException) {
            JSONArray()
        }
    }

    private fun decodeBase64(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return try {
            val decoded = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8).trim()
            if (decoded.contains('�')) value else decoded
        } catch (e: IllegalArgumentException) {
            value
        }
    }

    /** Lee un campo como texto aunque el servidor lo mande como número; devuelve null si está vacío. */
    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val text = opt(key)?.toString()?.trim()
        return if (text.isNullOrEmpty() || text == "null") null else text
    }

    private inline fun <T : Any> JSONArray.mapObjects(transform: (JSONObject) -> T?): List<T> {
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            transform(obj)?.let { out.add(it) }
        }
        return out
    }
}

/** Mensaje entendible para mostrar al usuario. */
fun Throwable.userMessage(): String = when (this) {
    is AuthException -> message ?: "Error de autenticación"
    is UnknownHostException -> "No se encontró el servidor. Revisa la URL y tu conexión a internet."
    is SocketTimeoutException -> "El servidor tardó demasiado en responder."
    is SSLException -> "Error de conexión segura con el servidor."
    is IllegalArgumentException -> message ?: "Dirección no válida"
    else -> message ?: "Ocurrió un error inesperado"
}
