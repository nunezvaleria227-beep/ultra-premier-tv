package com.example.mitvplayer.data

data class Credentials(
    val server: String,
    val username: String,
    val password: String
)

data class AccountInfo(
    val username: String,
    val status: String,
    /** Fecha de vencimiento en segundos desde epoch; null = sin vencimiento. */
    val expiresAt: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val allowedFormats: List<String>
)

data class Category(
    val id: String,
    val name: String
)

data class LiveChannel(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String,
    val number: Int
)

data class Movie(
    val id: String,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val extension: String,
    val rating: String?
)

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val duration: String?,
    val cast: String?,
    val director: String?
)

data class SeriesItem(
    val id: String,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val plot: String?,
    val rating: String?
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val extension: String,
    val plot: String?,
    val image: String?,
    val durationSecs: Int?
)

data class SeriesDetail(
    val plot: String?,
    val cover: String?,
    val genre: String?,
    /** Temporadas ordenadas; cada lista de episodios también va ordenada. */
    val seasons: Map<Int, List<Episode>>
)

data class EpgEntry(
    val title: String,
    val description: String,
    /** Segundos desde epoch. */
    val start: Long,
    val end: Long
)
