package com.jetgo.tv.data.repository

import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.data.model.ContentType
import com.jetgo.tv.data.model.MovieItem
import com.jetgo.tv.data.model.SeriesItem
import com.jetgo.tv.data.model.ServerConfig
import com.jetgo.tv.data.remote.M3uParser
import com.jetgo.tv.data.remote.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StreamRepository {

    /** Verifica credenciales contra el servidor Xtream Codes */
    suspend fun login(config: ServerConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val api = XtreamApi.create(config.host.ensureTrailingSlash())
            val resp = api.login(config.username, config.password)
            resp.isSuccessful && resp.body()?.userInfo?.auth == 1
        } catch (e: Exception) {
            false
        }
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
                epgChannelId = it.epgChannelId
            )
        } ?: emptyList()
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
                    it.containerExtension ?: "mp4"
                )
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
                plot = it.plot
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
            episodeIdInt, firstEpisode.containerExtension ?: "mp4"
        )
    }

    /** Modo alternativo: lista M3U simple en vez de API Xtream */
    suspend fun loadFromM3u(url: String): M3uParser.ParseResult = withContext(Dispatchers.IO) {
        M3uParser.fetchAndParse(url)
    }

    private fun String.ensureTrailingSlash() = if (endsWith("/")) this else "$this/"
}
