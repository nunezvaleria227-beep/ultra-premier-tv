package com.example.mitvplayer.data

private val BRACKETS = Regex("""\[[^\]]*\]|\{[^}]*\}""")
private val PAREN_YEAR = Regex("""\(\s*((?:19|20)\d{2})\s*\)""")
private val PAREN = Regex("""\([^)]*\)""")
private val PREFIX = Regex(
    """^\s*(?:es|en|mx|lat|latam|us|uk|fr|de|it|pt|br|ar|co|cl|pe|ve|vip|cast|esp)\s*[|:\-–]+\s*""",
    RegexOption.IGNORE_CASE
)
private val TAGS = Regex(
    """\b(?:4k|uhd|fhd|hd|2160p?|1080p?|720p?|480p?|hdrip|web-?dl|webrip|bluray|brrip|dvdrip|x26[45]|h\.?26[45]|hevc|latino|castellano|dual|subtitulado)\b""",
    RegexOption.IGNORE_CASE
)
private val TRAILING_YEAR = Regex("""\s+((?:19|20)\d{2})\s*$""")
private val SPACES = Regex("""\s+""")

/** Nombre de película o serie tal como lo pondría una persona: sin etiquetas de calidad, idioma ni año. */
fun cleanTitle(raw: String): String {
    var text = raw
    text = text.replace(BRACKETS, " ")
    text = text.replace(PAREN, " ")
    text = text.replace(PREFIX, "")
    text = text.replace(TAGS, " ")
    text = text.replace(TRAILING_YEAR, "")
    text = text.replace(SPACES, " ").trim(' ', '-', '–', '|', ':', '.')
    return text.ifEmpty { raw.trim() }
}

/** Año del estreno si el nombre lo trae: "(2019)" o "… 2019" al final. */
fun yearOf(raw: String): Int? {
    PAREN_YEAR.find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    return TRAILING_YEAR.find(raw.replace(BRACKETS, " ").trim())?.groupValues?.get(1)?.toIntOrNull()
}
