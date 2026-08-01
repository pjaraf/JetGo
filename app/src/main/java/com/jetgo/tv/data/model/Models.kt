package com.jetgo.tv.data.model

/** Tipos de contenido que se muestran como categorías en la fila inferior (Vivo, Serie, Película, Anime, Especial) */
enum class ContentType { LIVE, SERIES, MOVIE, ANIME, SPECIAL }

data class ServerConfig(
    val host: String,       // ej: http://midominio.com:8080
    val username: String,
    val password: String
)

data class Category(
    val id: String,
    val name: String,
    val type: ContentType
)

data class Channel(
    val streamId: String,
    val name: String,
    val logoUrl: String?,
    val categoryId: String,
    val streamUrl: String,
    val epgChannelId: String? = null,
    val number: Int? = null
)

/** Programa de la guía (EPG): título ya decodificado + horarios de inicio/fin */
data class EpgProgram(
    val title: String,
    val startMs: Long,
    val endMs: Long
)

data class SeriesItem(
    val seriesId: String,
    val name: String,
    val coverUrl: String?,
    val categoryId: String,
    val plot: String? = null,
    val rating: String? = null
)

data class MovieItem(
    val streamId: String,
    val name: String,
    val coverUrl: String?,
    val categoryId: String,
    val streamUrl: String,
    val rating: String? = null
)

/** Estado de reproducción mostrado en el overlay del reproductor (velocidad, en vivo, etc.) */
data class PlaybackStats(
    val isLive: Boolean = true,
    val bitrateKbps: Int = 0,
    val channelName: String = ""
)

/**
 * Modelo unificado para mostrar Channel/MovieItem/SeriesItem en una sola grid (ChannelListScreen).
 * [streamUrl] es null para series, ya que requiere resolver el episodio antes de reproducir.
 */
data class ContentItem(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val type: ContentType,
    val streamUrl: String?,
    val rating: String? = null
)

data class SeriesEpisode(
    val id: String,
    val title: String,
    val episodeNum: Int,
    val season: Int,
    val streamUrl: String
)

data class SeriesDetail(
    val seriesId: String,
    val name: String,
    val coverUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val episodesBySeason: Map<Int, List<SeriesEpisode>>
)

data class MovieDetail(
    val streamId: String,
    val name: String,
    val coverUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val country: String?,
    val releaseDate: String?,
    val rating: String?,
    val streamUrl: String
)
