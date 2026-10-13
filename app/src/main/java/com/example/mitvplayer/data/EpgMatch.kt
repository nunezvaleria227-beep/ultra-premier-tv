package com.example.mitvplayer.data

private val BRACKETS = Regex("""\[[^\]]*\]|\([^)]*\)|\{[^}]*\}""")
private val QUALITY = Regex("""\b(uhd|fhd|hd|sd|4k|8k|hevc|h\.?26[45]|raw|backup|\d{3,4}p|\d{2,3}fps)\b""")
private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")

/**
 * Nombre "base" de un canal para encontrar sus versiones hermanas ("MX | ESPN HD", "MX | ESPN FHD" y "MX | ESPN 4K"
 * dan lo mismo). Se conserva el país/prefijo a propósito: así un canal nunca toma la guía de otro país.
 * Devuelve null si el nombre es demasiado corto para comparar con seguridad.
 */
fun epgBaseKey(name: String): String? {
    val cleaned = searchKey(name)
        .replace(BRACKETS, " ")
        .replace(QUALITY, " ")
        .replace(NON_ALNUM, " ")
        .trim()
    return cleaned.takeIf { it.length >= 3 }
}
