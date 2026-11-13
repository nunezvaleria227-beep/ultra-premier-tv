package com.example.mitvplayer.data

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Lector de listas JSON enormes (decenas de miles de canales, películas o series) hecho a mano y rápido.
 *
 * Lee los bytes directamente, sin convertir todo el archivo a texto ni crear un mapa por cada elemento:
 * de cada objeto solo se guardan los campos pedidos y el resto se salta. Es varias veces más rápido y
 * gasta mucha menos memoria que el lector de Android, lo que en un Fire TV se nota al entrar a una sección.
 */
internal object JsonStream {

    /**
     * Lee un arreglo de objetos (o un objeto con objetos adentro, como mandan algunos paneles) y arma cada
     * elemento con [build], que recibe los valores de [keys] en el mismo orden (null si no venían o estaban vacíos).
     * Si la respuesta viene cortada se queda con lo que alcanzó a leer.
     */
    fun <T : Any> readArray(input: InputStream, keys: Array<String>, build: (Array<String?>) -> T?): List<T> {
        val scanner = JsonScanner(input)
        val wanted = Array(keys.size) { keys[it].toByteArray(Charsets.UTF_8) }
        val values = arrayOfNulls<String>(keys.size)
        val out = ArrayList<T>()
        try {
            scanner.skipBom()
            when (scanner.peekToken()) {
                '['.code -> {
                    scanner.skipByte()
                    while (true) {
                        val c = scanner.peekToken()
                        if (c == -1 || c == ']'.code) break
                        if (c == ','.code) {
                            scanner.skipByte()
                            continue
                        }
                        if (c == '{'.code) {
                            scanner.readObject(wanted, values)
                            build(values)?.let(out::add)
                        } else {
                            scanner.skipValue()
                        }
                    }
                }
                '{'.code -> {
                    // {"1": {...}, "2": {...}}
                    scanner.skipByte()
                    while (true) {
                        val c = scanner.peekToken()
                        if (c == -1 || c == '}'.code) break
                        if (c == ','.code) {
                            scanner.skipByte()
                            continue
                        }
                        if (c != '"'.code) {
                            scanner.skipValue()
                            continue
                        }
                        scanner.skipValue()
                        if (scanner.peekToken() == ':'.code) scanner.skipByte()
                        if (scanner.peekToken() == '{'.code) {
                            scanner.readObject(wanted, values)
                            build(values)?.let(out::add)
                        } else {
                            scanner.skipValue()
                        }
                    }
                }
                else -> return out
            }
        } catch (e: IOException) {
            if (out.isEmpty()) throw e
        } catch (e: RuntimeException) {
            if (out.isEmpty()) throw IOException("La respuesta del servidor no es válida")
        }
        return out
    }

    fun <T : Any> readArray(file: File, keys: Array<String>, build: (Array<String?>) -> T?): List<T> =
        file.inputStream().use { readArray(it, keys, build) }
}

/** Recorre los bytes de un JSON sin armar el árbol completo. */
internal class JsonScanner(private val input: InputStream) {
    private val buf = ByteArray(1 shl 16)
    private var pos = 0
    private var lim = 0
    private var str = ByteArray(256)

    private fun fill(): Boolean {
        val n = input.read(buf, 0, buf.size)
        pos = 0
        lim = if (n > 0) n else 0
        return n > 0
    }

    private fun nextByte(): Int {
        if (pos >= lim && !fill()) throw EOFException()
        return buf[pos++].toInt() and 0xFF
    }

    fun skipBom() {
        if (pos >= lim && !fill()) return
        if (lim - pos >= 3 && buf[pos] == 0xEF.toByte() && buf[pos + 1] == 0xBB.toByte() && buf[pos + 2] == 0xBF.toByte()) {
            pos += 3
        }
    }

    /** Siguiente byte que no sea espacio, sin consumirlo (-1 al final). */
    fun peekToken(): Int {
        while (true) {
            if (pos >= lim && !fill()) return -1
            val b = buf[pos].toInt() and 0xFF
            if (b == 0x20 || b == 0x0A || b == 0x0D || b == 0x09) {
                pos++
                continue
            }
            return b
        }
    }

    fun skipByte() {
        pos++
    }

    private fun put(n: Int, b: Int): Int {
        if (n == str.size) str = str.copyOf(n * 2)
        str[n] = b.toByte()
        return n + 1
    }

    private fun putCodePoint(start: Int, cp: Int): Int {
        var n = start
        when {
            cp < 0x80 -> n = put(n, cp)
            cp < 0x800 -> {
                n = put(n, 0xC0 or (cp shr 6))
                n = put(n, 0x80 or (cp and 0x3F))
            }
            cp < 0x10000 -> {
                n = put(n, 0xE0 or (cp shr 12))
                n = put(n, 0x80 or ((cp shr 6) and 0x3F))
                n = put(n, 0x80 or (cp and 0x3F))
            }
            else -> {
                n = put(n, 0xF0 or (cp shr 18))
                n = put(n, 0x80 or ((cp shr 12) and 0x3F))
                n = put(n, 0x80 or ((cp shr 6) and 0x3F))
                n = put(n, 0x80 or (cp and 0x3F))
            }
        }
        return n
    }

    private fun hex4(): Int {
        var v = 0
        repeat(4) {
            val b = nextByte()
            v = v shl 4 or when (b) {
                in '0'.code..'9'.code -> b - '0'.code
                in 'a'.code..'f'.code -> b - 'a'.code + 10
                in 'A'.code..'F'.code -> b - 'A'.code + 10
                else -> 0
            }
        }
        return v
    }

    /** Siguiente byte sin consumirlo (-1 al final); solo vuelve a leer del archivo si ya no queda nada. */
    private fun peekRaw(): Int {
        if (pos >= lim && !fill()) return -1
        return buf[pos].toInt() and 0xFF
    }

    /** Escribe en el texto el carácter de una secuencia de escape (lo que va después de la barra \\). */
    private fun escape(start: Int, e: Int): Int {
        when (e) {
            'n'.code -> return put(start, 0x0A)
            't'.code -> return put(start, 0x09)
            'r'.code -> return put(start, 0x0D)
            'b'.code -> return put(start, 0x08)
            'f'.code -> return put(start, 0x0C)
            'u'.code -> {
                var cp = hex4()
                if (cp in 0xD800..0xDBFF) {
                    // Emojis y otros: vienen como pareja \uD83D\uDE00.
                    if (peekRaw() == '\\'.code) {
                        pos++
                        val next = nextByte()
                        if (next != 'u'.code) return escape(putCodePoint(start, 0xFFFD), next)
                        val low = hex4()
                        cp = if (low in 0xDC00..0xDFFF) 0x10000 + ((cp - 0xD800) shl 10) + (low - 0xDC00) else 0xFFFD
                    } else {
                        cp = 0xFFFD
                    }
                } else if (cp in 0xDC00..0xDFFF) {
                    cp = 0xFFFD
                }
                return putCodePoint(start, cp)
            }
            else -> return put(start, e)
        }
    }

    /** Lee un texto (el siguiente byte debe ser la comilla de apertura). */
    fun readString(): String {
        pos++
        var n = 0
        while (true) {
            if (pos >= lim && !fill()) throw EOFException()
            val b = buf[pos++].toInt() and 0xFF
            if (b == '"'.code) break
            if (b == '\\'.code) {
                n = escape(n, nextByte())
                continue
            }
            if (n == str.size) str = str.copyOf(n * 2)
            str[n++] = b.toByte()
        }
        return String(str, 0, n, Charsets.UTF_8)
    }

    /** Número, true, false o null tal cual (null si es null). */
    fun readScalar(): String? {
        var n = 0
        while (true) {
            if (pos >= lim && !fill()) break
            val b = buf[pos].toInt() and 0xFF
            if (b == ','.code || b == '}'.code || b == ']'.code || b <= 0x20) break
            if (n == str.size) str = str.copyOf(n * 2)
            str[n++] = b.toByte()
            pos++
        }
        if (n == 4 && str[0] == 'n'.code.toByte() && str[1] == 'u'.code.toByte() && str[2] == 'l'.code.toByte() && str[3] == 'l'.code.toByte()) {
            return null
        }
        return String(str, 0, n, Charsets.ISO_8859_1)
    }

    private fun skipString() {
        pos++
        while (true) {
            if (pos >= lim && !fill()) throw EOFException()
            val b = buf[pos++]
            if (b == '"'.code.toByte()) return
            if (b == '\\'.code.toByte()) {
                if (pos >= lim && !fill()) throw EOFException()
                pos++
            }
        }
    }

    private fun skipContainer() {
        var depth = 0
        while (true) {
            if (pos >= lim && !fill()) throw EOFException()
            when (buf[pos].toInt()) {
                '"'.code -> {
                    skipString()
                    continue
                }
                '{'.code, '['.code -> depth++
                '}'.code, ']'.code -> {
                    depth--
                    if (depth == 0) {
                        pos++
                        return
                    }
                }
            }
            pos++
        }
    }

    fun skipValue() {
        when (peekToken()) {
            -1 -> return
            '"'.code -> skipString()
            '{'.code, '['.code -> skipContainer()
            else -> readScalar()
        }
    }

    /** Nombre de un campo: devuelve cuál de [keys] es, o -1. */
    private fun readKey(keys: Array<ByteArray>): Int {
        pos++
        var n = 0
        var escaped = false
        while (true) {
            if (pos >= lim && !fill()) throw EOFException()
            val b = buf[pos++]
            if (b == '"'.code.toByte()) break
            if (b == '\\'.code.toByte()) {
                escaped = true
                if (pos >= lim && !fill()) throw EOFException()
                pos++
                continue
            }
            if (n == str.size) str = str.copyOf(n * 2)
            str[n++] = b
        }
        if (escaped) return -1
        outer@ for (i in keys.indices) {
            val k = keys[i]
            if (k.size != n) continue
            for (j in 0 until n) if (k[j] != str[j]) continue@outer
            return i
        }
        return -1
    }

    /** Lee un objeto y deja en [values] los campos pedidos (vacíos y "null" quedan como null). */
    fun readObject(keys: Array<ByteArray>, values: Array<String?>) {
        java.util.Arrays.fill(values, null)
        pos++
        while (true) {
            val c = peekToken()
            if (c == -1) throw EOFException()
            if (c == '}'.code) {
                pos++
                return
            }
            if (c == ','.code) {
                pos++
                continue
            }
            if (c != '"'.code) {
                skipValue()
                continue
            }
            val k = readKey(keys)
            if (peekToken() == ':'.code) pos++
            if (k < 0) {
                skipValue()
                continue
            }
            values[k] = when (peekToken()) {
                '"'.code -> clean(readString())
                '['.code -> firstOfArray()
                '{'.code -> {
                    skipValue()
                    null
                }
                -1 -> throw EOFException()
                else -> readScalar()?.let { clean(it) }
            }
        }
    }

    /** Para campos que algunos paneles mandan como lista (por ejemplo "category_ids": [5]): el primero. */
    private fun firstOfArray(): String? {
        pos++
        var first: String? = null
        while (true) {
            val c = peekToken()
            if (c == -1) throw EOFException()
            if (c == ']'.code) {
                pos++
                return first
            }
            if (c == ','.code) {
                pos++
                continue
            }
            val v = when (c) {
                '"'.code -> clean(readString())
                '{'.code, '['.code -> {
                    skipValue()
                    null
                }
                else -> readScalar()?.let { clean(it) }
            }
            if (first == null) first = v
        }
    }

    private fun clean(value: String): String? {
        val trimmed = value.trim()
        return if (trimmed.isEmpty() || trimmed == "null") null else trimmed
    }
}
