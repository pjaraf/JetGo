package com.jetgo.tv.data.repository

import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.data.model.ContentType
import com.jetgo.tv.data.model.EpgProgram
import com.jetgo.tv.data.model.MovieDetail
import com.jetgo.tv.data.model.MovieItem
import com.jetgo.tv.data.model.SeriesDetail
import com.jetgo.tv.data.model.SeriesEpisode
import com.jetgo.tv.data.model.SeriesItem
import com.jetgo.tv.data.model.ServerConfig
import com.jetgo.tv.data.remote.M3uParser
import com.jetgo.tv.data.remote.UserInfoDto
import com.jetgo.tv.data.remote.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StreamRepository {

    /** Verifica credenciales contra el servidor Xtream Codes. Devuelve la info de la cuenta si es válida. */
    suspend fun login(config: ServerConfig): UserInfoDto? = withContext(Dispatchers.IO) {
        val api = XtreamApi.create(config.host.ensureTrailingSlash())
        val resp = api.login(config.username, config.password)
        val requestUrl = resp.raw().request.url.toString()
            .replace(Regex("password=[^&]*"), "password=***")

        if (!resp.isSuccessful) {
            throw IllegalStateException("HTTP ${resp.code()} en: $requestUrl")
        }
        val userInfo = resp.body()?.userInfo
        if (userInfo?.auth != 1) {
            throw IllegalStateException(
                "auth=${userInfo?.auth}, status=${userInfo?.status ?: "sin dato"} — URL: $requestUrl"
            )
        }
        userInfo
    }

    suspend fun getLiveCategories(config: ServerConfig): List<Category> = withContext(Dispatchers.IO) {
        val api = XtreamApi.create(config.host.ensureTrailingSlash())
        val resp = api.getLiveCategories(config.username, config.password)
        resp.body()?.map { Category(it.categoryId, it.categoryName, ContentType.LIVE) } ?: emptyList()
    }

    suspend fun getLiveChannels(config: ServerConfig, categoryId: String?): List<Channel> = withContext(Dispatchers.IO) {
        val api = XtreamApi.create(config.host.ensureTrailingSlash())
        val resp = api.getLiveStreams(config.username, config.password, categoryId = categoryId)
        resp.body()?.map {
            Channel(
                streamId = it.streamId.toString(),
                name = it.name,
                logoUrl = it.streamIcon,
                categoryId = it.categoryId,
                streamUrl = XtreamApi.liveStreamUrl(config.host, config.username, config.password, it.streamId),
                epgChannelId = it.epgChannelId,
                number = it.num
            )
        } ?: emptyList()
    }

    /** Programa actual y siguiente de un canal en vivo (si tu servidor entrega guía EPG) */
    suspend fun getShortEpg(config: ServerConfig, streamId: String): List<EpgProgram> = withContext(Dispatchers.IO) {
        try {
            val api = XtreamApi.create(config.host.ensureTrailingSlash())
            val resp = api.getShortEpg(config.username, config.password, streamId = streamId, limit = 2)
            resp.body()?.epgListings?.mapNotNull { listing ->
                val startMs = (listing.startTimestamp ?: return@mapNotNull null) * 1000
                val endMs = (listing.stopTimestamp ?: return@mapNotNull null) * 1000
                EpgProgram(title = decodeEpgText(listing.title), startMs = startMs, endMs = endMs)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeEpgText(raw: String?): String {
        if (raw.isNullOrBlank()) return "Sin información"
        return try {
            String(android.util.Base64.decode(raw, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            raw
        }
    }

    suspend fun getVodCategories(config: ServerConfig): List<Category> = withContext(Dispatchers.IO) {
        val api = XtreamApi.create(config.host.ensureTrailingSlash())
        val resp = api.getVodCategories(config.username, config.password)
        resp.body()?.map { Category(it.categoryId, it.categoryName, ContentType.MOVIE) } ?: emptyList()
    }

    suspend fun getMovies(config: ServerConfig, categoryId: String?): List<MovieItem> = withContext(Dispatchers.IO) {
        val api = XtreamApi.create(config.host.ensureTrailingSlash())
        val resp = api.getVodStreams(config.username, config.password, categoryId = categoryId)
        resp.body()?.map {
            MovieItem(
                streamId = it.streamId.toString(),
                name = it.name,
                coverUrl = it.streamIcon,
                categoryId = it.categoryId,
                streamUrl = XtreamApi.vodStreamUrl(
                    config.host, config.username, config.password, it.streamId,
                    it.containerExtension?.takeIf { ext -> ext.isNotBlank() } ?: "mp4"
                ),
                rating = it.rating
            )
        } ?: emptyList()
    }

    suspend fun getSeriesCategories(config: ServerConfig): List<Category> = withContext(Dispatchers.IO) {
        val api = XtreamApi.create(config.host.ensureTrailingSlash())
        val resp = api.getSeriesCategories(config.username, config.password)
        resp.body()?.map { Category(it.categoryId, it.categoryName, ContentType.SERIES) } ?: emptyList()
    }

    suspend fun getSeries(config: ServerConfig, categoryId: String?): List<SeriesItem> = withContext(Dispatchers.IO) {
        val api = XtreamApi.create(config.host.ensureTrailingSlash())
        val resp = api.getSeries(config.username, config.password, categoryId = categoryId)
        resp.body()?.map {
            SeriesItem(
                seriesId = it.seriesId.toString(),
                name = it.name,
                coverUrl = it.cover,
                categoryId = it.categoryId,
                plot = it.plot,
                rating = it.rating
            )
        } ?: emptyList()
    }

    /**
     * Muchos paneles Xtream no tienen un "tipo" nativo para Anime/Especial: son categorías VOD
     * con ese nombre. Este helper busca categorías VOD cuyo nombre contenga [keyword].
     */
    suspend fun getVodCategoriesByKeyword(config: ServerConfig, keyword: String): List<Category> =
        withContext(Dispatchers.IO) {
            getVodCategories(config).filter { it.name.contains(keyword, ignoreCase = true) }
        }

    /**
     * Muchos paneles Xtream no tienen un "tipo" nativo para Anime/Especial: son categorías VOD
     * con ese nombre. Este helper busca categorías VOD cuyo nombre contenga [keyword] y agrupa
     * todas sus películas/OVAs en una sola lista.
     */
    suspend fun getMoviesByCategoryKeyword(config: ServerConfig, keyword: String): List<MovieItem> =
        withContext(Dispatchers.IO) {
            val matchingCategories = getVodCategories(config).filter {
                it.name.contains(keyword, ignoreCase = true)
            }
            matchingCategories.flatMap { getMovies(config, it.id) }
        }

    /** Resuelve la URL reproducible del primer episodio (temporada más baja, episodio 1) de una serie */
    suspend fun getFirstEpisodeUrl(config: ServerConfig, seriesId: String): String? = withContext(Dispatchers.IO) {
        val api = XtreamApi.create(config.host.ensureTrailingSlash())
        val resp = api.getSeriesInfo(config.username, config.password, seriesId = seriesId)
        val episodesBySeason = resp.body()?.episodes ?: return@withContext null
        val firstSeasonKey = episodesBySeason.keys.minByOrNull { it.toIntOrNull() ?: Int.MAX_VALUE } ?: return@withContext null
        val firstEpisode = episodesBySeason[firstSeasonKey]
            ?.minByOrNull { it.episodeNum ?: Int.MAX_VALUE } ?: return@withContext null
        val episodeIdInt = firstEpisode.id.toIntOrNull() ?: return@withContext null
        XtreamApi.seriesStreamUrl(
            config.host, config.username, config.password,
            episodeIdInt, firstEpisode.containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"
        )
    }

    /** Trae toda la info de una serie: sinopsis, elenco, y TODOS los capítulos de TODAS las temporadas */
    suspend fun getSeriesDetail(config: ServerConfig, seriesId: String, fallbackName: String, fallbackCover: String?): SeriesDetail? =
        withContext(Dispatchers.IO) {
            val api = XtreamApi.create(config.host.ensureTrailingSlash())
            val resp = api.getSeriesInfo(config.username, config.password, seriesId = seriesId)
            val body = resp.body() ?: return@withContext null

            val episodesBySeason = body.episodes?.mapNotNull { (seasonKey, episodes) ->
                val seasonNum = seasonKey.toIntOrNull() ?: return@mapNotNull null
                val mapped = episodes.mapNotNull { ep ->
                    val epIdInt = ep.id.toIntOrNull() ?: return@mapNotNull null
                    SeriesEpisode(
                        id = ep.id,
                        title = ep.title?.takeIf { it.isNotBlank() } ?: "Episodio ${ep.episodeNum ?: 0}",
                        episodeNum = ep.episodeNum ?: 0,
                        season = seasonNum,
                        streamUrl = XtreamApi.seriesStreamUrl(
                            config.host, config.username, config.password,
                            epIdInt, ep.containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"
                        )
                    )
                }.sortedBy { it.episodeNum }
                seasonNum to mapped
            }?.toMap() ?: emptyMap()

            SeriesDetail(
                seriesId = seriesId,
                name = body.info?.name?.takeIf { it.isNotBlank() } ?: fallbackName,
                coverUrl = body.info?.cover?.takeIf { it.isNotBlank() } ?: fallbackCover,
                plot = body.info?.plot,
                cast = body.info?.cast,
                director = body.info?.director,
                genre = body.info?.genre,
                releaseDate = body.info?.releaseDate,
                rating = body.info?.rating,
                episodesBySeason = episodesBySeason
            )
        }

    /** Trae toda la info de una película: sinopsis, elenco, país, etc. */
    suspend fun getMovieDetail(
        config: ServerConfig,
        streamId: String,
        fallbackName: String,
        fallbackCover: String?,
        fallbackStreamUrl: String
    ): MovieDetail? = withContext(Dispatchers.IO) {
        val api = XtreamApi.create(config.host.ensureTrailingSlash())
        val resp = api.getVodInfo(config.username, config.password, vodId = streamId)
        val body = resp.body() ?: return@withContext null
        val info = body.info

        val streamIdInt = body.movieData?.streamId ?: streamId.toIntOrNull()
        val realExtension = body.movieData?.containerExtension?.takeIf { it.isNotBlank() }
        val streamUrl = if (streamIdInt != null) {
            XtreamApi.vodStreamUrl(
                config.host, config.username, config.password,
                streamIdInt, realExtension ?: "mp4"
            )
        } else fallbackStreamUrl

        // Si el servidor SÍ informó la extensión real, no hace falta adivinar nada más.
        // Si NO la informó (quedó vacía), se preparan otras extensiones comunes para
        // probarlas automáticamente en caso de que "mp4" no sea la correcta.
        val alternateUrls = if (realExtension == null && streamIdInt != null) {
            listOf("mkv", "ts", "avi", "m3u8").map { ext ->
                XtreamApi.vodStreamUrl(config.host, config.username, config.password, streamIdInt, ext)
            }
        } else emptyList()

        MovieDetail(
            streamId = streamId,
            name = info?.name?.takeIf { it.isNotBlank() } ?: fallbackName,
            coverUrl = (info?.coverBig ?: info?.movieImage)?.takeIf { it.isNotBlank() } ?: fallbackCover,
            plot = info?.plot,
            cast = info?.cast,
            director = info?.director,
            genre = info?.genre,
            country = info?.country,
            releaseDate = info?.releaseDate,
            rating = info?.rating,
            streamUrl = streamUrl,
            alternateStreamUrls = alternateUrls
        )
    }

    /** Modo alternativo: lista M3U simple en vez de API Xtream */
    suspend fun loadFromM3u(url: String): M3uParser.ParseResult = withContext(Dispatchers.IO) {
        M3uParser.fetchAndParse(url)
    }

    private fun String.ensureTrailingSlash() = if (endsWith("/")) this else "$this/"
}
