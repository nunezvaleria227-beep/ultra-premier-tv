package com.example.mitvplayer.data

import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.IOException

/**
 * Lectura de listas JSON muy grandes (decenas de miles de elementos) sin cargarlas enteras en memoria.
 * Solo se conservan los campos pedidos de cada objeto; todo lo demás se salta.
 */
internal object JsonStream {

    fun <T : Any> readArray(
        file: File,
        wanted: Set<String>,
        build: (Map<String, String>) -> T?
    ): List<T> {
        val out = ArrayList<T>()
        val source = file.inputStream().reader(Charsets.UTF_8).buffered()
        source.use { buffered ->
            // Algunos servidores anteponen una marca BOM al JSON.
            buffered.mark(1)
            if (buffered.read() != 0xFEFF) buffered.reset()

            val reader = JsonReader(buffered)
            reader.isLenient = true
            try {
                if (reader.peek() != JsonToken.BEGIN_ARRAY) return out
                reader.beginArray()
                while (reader.hasNext()) {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        val built = build(readObject(reader, wanted))
                        if (built != null) out.add(built)
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endArray()
            } catch (e: IOException) {
                // Respuesta cortada a la mitad: se queda con lo que se alcanzó a leer.
                if (out.isEmpty()) throw e
            } catch (e: IllegalStateException) {
                if (out.isEmpty()) throw IOException("La respuesta del servidor no es válida")
            }
        }
        return out
    }

    private fun readObject(reader: JsonReader, wanted: Set<String>): Map<String, String> {
        val map = HashMap<String, String>(12)
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name !in wanted) {
                reader.skipValue()
                continue
            }
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> {
                    val value = reader.nextString().trim()
                    if (value.isNotEmpty() && value != "null") map[name] = value
                }
                JsonToken.BOOLEAN -> map[name] = reader.nextBoolean().toString()
                JsonToken.NULL -> reader.nextNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return map
    }
}
