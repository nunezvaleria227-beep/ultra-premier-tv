package com.example.mitvplayer.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException

/**
 * Copia compacta en el equipo de cada lista ya leída (solo los campos que usa la app).
 *
 * Abrir la app con la lista guardada así es mucho más rápido que volver a leer el JSON del servidor
 * (que trae decenas de campos que no se usan) y ocupa una fracción del espacio.
 */
internal object CatalogStore {
    private const val MAGIC = 0x52_4D_43_53 // "RMCS"
    private const val VERSION = 2
    private const val KIND_LIVE = 1
    private const val KIND_MOVIES = 2
    private const val KIND_SERIES = 3

    fun writeLive(file: File, items: List<LiveChannel>) = write(file, KIND_LIVE, items.size) { out ->
        for (c in items) {
            out.str(c.id)
            out.str(c.name)
            out.str(c.icon)
            out.str(c.categoryId)
            out.writeInt(c.number)
            out.writeInt(c.archiveDays)
            out.writeLong(c.added)
        }
    }

    fun writeMovies(file: File, items: List<Movie>) = write(file, KIND_MOVIES, items.size) { out ->
        for (m in items) {
            out.str(m.id)
            out.str(m.name)
            out.str(m.poster)
            out.str(m.categoryId)
            out.str(m.extension)
            out.str(m.rating)
            out.writeLong(m.added)
        }
    }

    fun writeSeries(file: File, items: List<SeriesItem>) = write(file, KIND_SERIES, items.size) { out ->
        for (s in items) {
            out.str(s.id)
            out.str(s.name)
            out.str(s.cover)
            out.str(s.categoryId)
            out.str(s.rating)
            out.writeLong(s.added)
        }
    }

    fun readLive(file: File): List<LiveChannel>? = read(file, KIND_LIVE) { inp, n ->
        val pool = HashMap<String, String>()
        val out = ArrayList<LiveChannel>(n)
        repeat(n) {
            out.add(
                LiveChannel(
                    id = inp.str() ?: "",
                    name = inp.str() ?: "",
                    icon = inp.str(),
                    categoryId = inp.str()?.let { pool.getOrPut(it) { it } } ?: "",
                    number = inp.readInt(),
                    archiveDays = inp.readInt(),
                    added = inp.readLong()
                )
            )
        }
        out
    }

    fun readMovies(file: File): List<Movie>? = read(file, KIND_MOVIES) { inp, n ->
        val pool = HashMap<String, String>()
        val out = ArrayList<Movie>(n)
        repeat(n) {
            out.add(
                Movie(
                    id = inp.str() ?: "",
                    name = inp.str() ?: "",
                    poster = inp.str(),
                    categoryId = inp.str()?.let { pool.getOrPut(it) { it } } ?: "",
                    extension = inp.str()?.let { pool.getOrPut(it) { it } } ?: "mp4",
                    rating = inp.str()?.let { pool.getOrPut(it) { it } },
                    added = inp.readLong()
                )
            )
        }
        out
    }

    fun readSeries(file: File): List<SeriesItem>? = read(file, KIND_SERIES) { inp, n ->
        val pool = HashMap<String, String>()
        val out = ArrayList<SeriesItem>(n)
        repeat(n) {
            out.add(
                SeriesItem(
                    id = inp.str() ?: "",
                    name = inp.str() ?: "",
                    cover = inp.str(),
                    categoryId = inp.str()?.let { pool.getOrPut(it) { it } } ?: "",
                    plot = null,
                    rating = inp.str()?.let { pool.getOrPut(it) { it } },
                    added = inp.readLong()
                )
            )
        }
        out
    }

    // ------------------------------------------------------------------ interno

    private class Out(stream: java.io.OutputStream) : DataOutputStream(stream) {
        fun str(value: String?) {
            if (value == null) {
                writeInt(-1)
                return
            }
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }
    }

    private class In(stream: java.io.InputStream) : DataInputStream(stream) {
        private var buf = ByteArray(256)

        fun str(): String? {
            val n = readInt()
            if (n < 0) return null
            if (n > 1_000_000) throw IOException("Copia guardada dañada")
            if (n > buf.size) buf = ByteArray(maxOf(n, buf.size * 2))
            readFully(buf, 0, n)
            return String(buf, 0, n, Charsets.UTF_8)
        }
    }

    private fun write(file: File, kind: Int, count: Int, body: (Out) -> Unit) {
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            Out(BufferedOutputStream(temp.outputStream(), 1 shl 16)).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeInt(kind)
                out.writeInt(count)
                body(out)
                out.writeInt(MAGIC)
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            temp.delete()
        }
    }

    private fun <T> read(file: File, kind: Int, body: (In, Int) -> List<T>): List<T>? {
        if (!file.exists() || file.length() < 20) return null
        return try {
            In(BufferedInputStream(file.inputStream(), 1 shl 16)).use { inp ->
                if (inp.readInt() != MAGIC || inp.readInt() != VERSION || inp.readInt() != kind) return null
                val count = inp.readInt()
                if (count < 0 || count > 5_000_000) return null
                val items = body(inp, count)
                if (inp.readInt() != MAGIC) return null
                items
            }
        } catch (e: EOFException) {
            null
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }
}
