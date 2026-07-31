package com.jetgo.tv.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jetgo.tv.BuildConfig
import com.jetgo.tv.data.local.AccessStore
import com.jetgo.tv.data.local.ConfigStore
import com.jetgo.tv.data.local.FavoritesStore
import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.data.model.ContentType
import com.jetgo.tv.data.model.ServerConfig
import com.jetgo.tv.data.model.SeriesDetail
import com.jetgo.tv.data.model.SeriesEpisode
import com.jetgo.tv.data.repository.StreamRepository
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.util.AccessCodeChecker
import com.jetgo.tv.util.AccessCodeResult
import com.jetgo.tv.util.UpdateChecker
import com.jetgo.tv.util.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isLoading: Boolean = false,
    val isConfigured: Boolean = false,
    val liveChannels: List<Channel> = emptyList(),
    val errorMessage: String? = null
)

data class AccessUiState(
    val isChecking: Boolean = true, // empieza en true: revisando si ya hay un código guardado
    val isGranted: Boolean = false,
    val errorMessage: String? = null
)

data class CategoryPickerUiState(
    val isLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null
)

data class CategoryContentUiState(
    val isLoading: Boolean = false,
    val items: List<ContentItem> = emptyList(),
    val errorMessage: String? = null
)

data class SearchUiState(
    val isLoadingCatalog: Boolean = false,
    val query: String = "",
    val results: List<ContentItem> = emptyList()
)

data class HomeCatalogState(
    val isLoading: Boolean = false,
    val movies: List<ContentItem> = emptyList(),
    val series: List<ContentItem> = emptyList(),
    val anime: List<ContentItem> = emptyList()
)

data class SeriesDetailUiState(
    val isLoading: Boolean = false,
    val detail: SeriesDetail? = null,
    val selectedSeason: Int = 1,
    val currentEpisodeId: String? = null,
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StreamRepository()
    private val configStore = ConfigStore(application)
    private val favoritesStore = FavoritesStore(application)
    private val accessStore = AccessStore(application)
    val playerManager = PlayerManager(application)

    private val _accessState = MutableStateFlow(AccessUiState())
    val accessState: StateFlow<AccessUiState> = _accessState.asStateFlow()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _categoryPickerState = MutableStateFlow(CategoryPickerUiState())
    val categoryPickerState: StateFlow<CategoryPickerUiState> = _categoryPickerState.asStateFlow()

    private val _categoryContentState = MutableStateFlow(CategoryContentUiState())
    val categoryContentState: StateFlow<CategoryContentUiState> = _categoryContentState.asStateFlow()

    private val _favorites = MutableStateFlow<List<ContentItem>>(emptyList())
    val favorites: StateFlow<List<ContentItem>> = _favorites.asStateFlow()

    private val _isFullscreenPlayer = MutableStateFlow(false)
    val isFullscreenPlayer: StateFlow<Boolean> = _isFullscreenPlayer.asStateFlow()

    fun enterFullscreenPlayer() { _isFullscreenPlayer.value = true }
    fun exitFullscreenPlayer() { _isFullscreenPlayer.value = false }

    fun disconnect() {
        viewModelScope.launch {
            configStore.clear()
            playerManager.exoPlayer.stop()
            currentConfig = null
            _uiState.value = HomeUiState()
        }
    }

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _homeCatalog = MutableStateFlow(HomeCatalogState())
    val homeCatalog: StateFlow<HomeCatalogState> = _homeCatalog.asStateFlow()
    private var homeCatalogLoaded = false

    private val _seriesDetailState = MutableStateFlow(SeriesDetailUiState())
    val seriesDetailState: StateFlow<SeriesDetailUiState> = _seriesDetailState.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private var currentConfig: ServerConfig? = null
    private var currentMode: String = "xtream" // "xtream" o "m3u"

    // Caché en memoria del catálogo completo para búsqueda instantánea tras la primera carga
    private var searchCatalog: List<ContentItem>? = null

    init {
        checkStoredAccess()

        viewModelScope.launch {
            configStore.mode.collect { currentMode = it }
        }
        viewModelScope.launch {
            configStore.config.collect { config ->
                currentConfig = config
                if (config != null && currentMode == "xtream") connectXtream(config)
            }
        }
        viewModelScope.launch {
            favoritesStore.favorites.collect { _favorites.value = it }
        }
        checkForUpdate()
    }

    /** Al abrir la app: si ya había un código guardado, lo re-valida contra Firestore
     *  (así una revocación hecha desde el panel de administración sí toma efecto), y
     *  refresca la conexión por si el administrador cambió el servidor de ese código. */
    private fun checkStoredAccess() {
        viewModelScope.launch {
            val projectId = getApplication<Application>().getString(com.jetgo.tv.R.string.firebase_project_id)
            val savedCode = accessStore.savedCode.first()

            if (savedCode.isNullOrBlank()) {
                _accessState.value = AccessUiState(isChecking = false, isGranted = false)
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                AccessCodeChecker.checkCode(projectId, savedCode)
            }

            if (result.valid) {
                applyAccessCodeResult(result)
                _accessState.value = AccessUiState(isChecking = false, isGranted = true)
            } else {
                accessStore.clear()
                _accessState.value = AccessUiState(isChecking = false, isGranted = false, errorMessage = null)
            }
        }
    }

    fun submitAccessCode(code: String) {
        _accessState.value = _accessState.value.copy(isChecking = true, errorMessage = null)
        viewModelScope.launch {
            val projectId = getApplication<Application>().getString(com.jetgo.tv.R.string.firebase_project_id)
            val result = withContext(Dispatchers.IO) {
                AccessCodeChecker.checkCode(projectId, code)
            }
            if (result.valid) {
                accessStore.saveCode(code.trim().uppercase())
                applyAccessCodeResult(result)
                _accessState.value = AccessUiState(isChecking = false, isGranted = true)
            } else {
                _accessState.value = AccessUiState(
                    isChecking = false,
                    isGranted = false,
                    errorMessage = "Código inválido o inactivo"
                )
            }
        }
    }

    /** Conecta automáticamente al servidor cargado por el administrador para ese código,
     *  sin que el cliente tenga que ver ni escribir host/usuario/contraseña. */
    private fun applyAccessCodeResult(result: AccessCodeResult) {
        if (result.mode == "m3u" && !result.m3uUrl.isNullOrBlank()) {
            connectM3u(result.m3uUrl)
        } else if (!result.host.isNullOrBlank() && !result.username.isNullOrBlank() && !result.password.isNullOrBlank()) {
            connectXtream(ServerConfig(result.host, result.username, result.password))
        }
        // Si el código es válido pero el administrador no cargó credenciales todavía,
        // simplemente no se auto-conecta nada (uiState.isConfigured queda en false).
    }

    /** Consulta en segundo plano si hay una versión más nueva publicada en GitHub Releases */
    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                UpdateChecker.checkForUpdate(BuildConfig.GITHUB_REPO, BuildConfig.VERSION_CODE)
            }
            _updateInfo.value = info
        }
    }


    fun connectXtream(config: ServerConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            configStore.saveXtream(config.host, config.username, config.password)
            currentConfig = config
            currentMode = "xtream"
            val ok = repository.login(config)
            if (!ok) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "No se pudo autenticar con el servidor")
                return@launch
            }
            val channels = repository.getLiveChannels(config, categoryId = null)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isConfigured = true,
                liveChannels = channels
            )
            channels.firstOrNull()?.let { playChannel(it) }
        }
    }

    fun connectM3u(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            configStore.saveM3u(url)
            currentMode = "m3u"
            val result = repository.loadFromM3u(url)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isConfigured = true,
                liveChannels = result.channels
            )
            result.channels.firstOrNull()?.let { playChannel(it) }
        }
    }

    fun playChannel(channel: Channel) {
        playerManager.playChannel(channel.streamUrl, channel.name)
    }

    // ---------------------------------------------------------------------
    // Selector de subcategorías (evita traer TODO el catálogo de golpe)
    // ---------------------------------------------------------------------

    /** Paso 1: lista las subcategorías disponibles para el tipo tocado (Vivo/Serie/Película/Anime/Especial) */
    fun loadCategoriesForType(type: ContentType) {
        _categoryPickerState.value = CategoryPickerUiState(isLoading = true)

        if (currentMode == "m3u") {
            // El modo M3U simple no tiene categorías separadas por API; solo hay una lista plana.
            _categoryPickerState.value = CategoryPickerUiState(
                categories = if (type == ContentType.LIVE) {
                    _uiState.value.liveChannels.map { it.categoryId }.distinct()
                        .map { Category(it, it, ContentType.LIVE) }
                } else emptyList()
            )
            return
        }

        val config = currentConfig ?: run {
            _categoryPickerState.value = CategoryPickerUiState(errorMessage = "Sin configuración de servidor")
            return
        }

        viewModelScope.launch {
            try {
                val categories = when (type) {
                    ContentType.LIVE -> repository.getLiveCategories(config)
                    ContentType.MOVIE -> repository.getVodCategories(config)
                    ContentType.SERIES -> repository.getSeriesCategories(config)
                    ContentType.ANIME -> repository.getVodCategoriesByKeyword(config, "anime")
                    ContentType.SPECIAL -> {
                        val especial = repository.getVodCategoriesByKeyword(config, "especial")
                        val special = repository.getVodCategoriesByKeyword(config, "special")
                        (especial + special).distinctBy { it.id }
                    }
                }
                _categoryPickerState.value = CategoryPickerUiState(categories = categories)
            } catch (e: Exception) {
                _categoryPickerState.value = CategoryPickerUiState(errorMessage = "Error al cargar categorías: ${e.message}")
            }
        }
    }

    /** Paso 2: dentro de la subcategoría elegida, trae solo el contenido de esa categoría */
    fun loadCategoryContent(type: ContentType, categoryId: String) {
        _categoryContentState.value = CategoryContentUiState(isLoading = true)

        if (currentMode == "m3u") {
            val items = _uiState.value.liveChannels
                .filter { it.categoryId == categoryId }
                .map { ContentItem(it.streamId, it.name, it.logoUrl, ContentType.LIVE, it.streamUrl) }
            _categoryContentState.value = CategoryContentUiState(items = items)
            return
        }

        val config = currentConfig ?: run {
            _categoryContentState.value = CategoryContentUiState(errorMessage = "Sin configuración de servidor")
            return
        }

        viewModelScope.launch {
            try {
                val items = when (type) {
                    ContentType.LIVE -> repository.getLiveChannels(config, categoryId).map {
                        ContentItem(it.streamId, it.name, it.logoUrl, ContentType.LIVE, it.streamUrl)
                    }
                    ContentType.MOVIE, ContentType.ANIME, ContentType.SPECIAL -> repository.getMovies(config, categoryId).map {
                        ContentItem(it.streamId, it.name, it.coverUrl, type, it.streamUrl)
                    }
                    ContentType.SERIES -> repository.getSeries(config, categoryId).map {
                        ContentItem(it.seriesId, it.name, it.coverUrl, ContentType.SERIES, null)
                    }
                }
                _categoryContentState.value = CategoryContentUiState(items = items)
            } catch (e: Exception) {
                _categoryContentState.value = CategoryContentUiState(errorMessage = "Error al cargar contenido: ${e.message}")
            }
        }
    }

    /** Se llama al tocar un ítem final. Resuelve el episodio si es una serie. */
    fun selectContentItem(item: ContentItem, onReady: () -> Unit) {
        if (item.streamUrl != null) {
            playerManager.playChannel(item.streamUrl, item.name)
            onReady()
            return
        }
        val config = currentConfig ?: return
        viewModelScope.launch {
            val url = repository.getFirstEpisodeUrl(config, item.id)
            if (url != null) {
                playerManager.playChannel(url, item.name)
            }
            onReady()
        }
    }

    // ---------------------------------------------------------------------
    // Favoritos
    // ---------------------------------------------------------------------

    fun toggleFavorite(item: ContentItem) {
        viewModelScope.launch { favoritesStore.toggleFavorite(item) }
    }

    fun isFavorite(item: ContentItem): Boolean =
        _favorites.value.any { it.id == item.id && it.type == item.type }

    fun playFavorite(item: ContentItem, onReady: () -> Unit) {
        selectContentItem(item, onReady)
    }

    // ---------------------------------------------------------------------
    // Búsqueda global (construye un catálogo en memoria una sola vez)
    // ---------------------------------------------------------------------

    fun onSearchQueryChanged(query: String) {
        _searchState.value = _searchState.value.copy(query = query)
        applySearchFilter()
    }

    /** Descarga todo el catálogo (canales, películas, series) una sola vez y lo cachea en memoria */
    fun ensureSearchCatalogLoaded() {
        if (searchCatalog != null || _searchState.value.isLoadingCatalog) return

        val liveAsItems = _uiState.value.liveChannels.map {
            ContentItem(it.streamId, it.name, it.logoUrl, ContentType.LIVE, it.streamUrl)
        }

        if (currentMode == "m3u") {
            searchCatalog = liveAsItems
            applySearchFilter()
            return
        }

        val config = currentConfig ?: run {
            searchCatalog = liveAsItems
            applySearchFilter()
            return
        }

        _searchState.value = _searchState.value.copy(isLoadingCatalog = true)
        viewModelScope.launch {
            val movies = try {
                repository.getMovies(config, categoryId = null).map {
                    ContentItem(it.streamId, it.name, it.coverUrl, ContentType.MOVIE, it.streamUrl)
                }
            } catch (e: Exception) { emptyList() }

            val series = try {
                repository.getSeries(config, categoryId = null).map {
                    ContentItem(it.seriesId, it.name, it.coverUrl, ContentType.SERIES, null)
                }
            } catch (e: Exception) { emptyList() }

            searchCatalog = liveAsItems + movies + series
            _searchState.value = _searchState.value.copy(isLoadingCatalog = false)
            applySearchFilter()
        }
    }

    private fun applySearchFilter() {
        val query = _searchState.value.query.trim()
        val catalog = searchCatalog ?: emptyList()
        val results = if (query.isBlank()) emptyList() else catalog.filter {
            it.name.contains(query, ignoreCase = true)
        }
        _searchState.value = _searchState.value.copy(results = results)
    }

    /** Carga (una sola vez) películas/series/anime destacados para la pantalla de Inicio */
    fun ensureHomeCatalogLoaded() {
        if (homeCatalogLoaded || _homeCatalog.value.isLoading) return

        if (currentMode == "m3u") {
            homeCatalogLoaded = true
            return
        }
        val config = currentConfig ?: return

        _homeCatalog.value = _homeCatalog.value.copy(isLoading = true)
        viewModelScope.launch {
            val movies = try {
                repository.getMovies(config, categoryId = null).map {
                    ContentItem(it.streamId, it.name, it.coverUrl, ContentType.MOVIE, it.streamUrl)
                }
            } catch (e: Exception) { emptyList() }

            val series = try {
                repository.getSeries(config, categoryId = null).map {
                    ContentItem(it.seriesId, it.name, it.coverUrl, ContentType.SERIES, null)
                }
            } catch (e: Exception) { emptyList() }

            val anime = try {
                repository.getMoviesByCategoryKeyword(config, "anime").map {
                    ContentItem(it.streamId, it.name, it.coverUrl, ContentType.ANIME, it.streamUrl)
                }
            } catch (e: Exception) { emptyList() }

            _homeCatalog.value = HomeCatalogState(isLoading = false, movies = movies, series = series, anime = anime)
            homeCatalogLoaded = true
        }
    }

    // ---------------------------------------------------------------------
    // Pantalla de detalle de serie: temporadas, capítulos, auto-avance
    // ---------------------------------------------------------------------

    /** Carga toda la ficha de la serie (sinopsis + todas las temporadas/capítulos) */
    fun loadSeriesDetail(item: ContentItem) {
        _seriesDetailState.value = SeriesDetailUiState(isLoading = true)
        val config = currentConfig ?: run {
            _seriesDetailState.value = SeriesDetailUiState(errorMessage = "Sin configuración de servidor")
            return
        }
        viewModelScope.launch {
            val detail = try {
                repository.getSeriesDetail(config, item.id, item.name, item.imageUrl)
            } catch (e: Exception) { null }

            if (detail == null) {
                _seriesDetailState.value = SeriesDetailUiState(errorMessage = "No se pudo cargar la serie")
                return@launch
            }

            val firstSeason = detail.episodesBySeason.keys.minOrNull() ?: 1
            _seriesDetailState.value = SeriesDetailUiState(detail = detail, selectedSeason = firstSeason)

            // Auto-avance: cuando termina un capítulo, reproduce el siguiente automáticamente
            playerManager.onPlaybackEnded = { playNextEpisode() }

            // Reproduce automáticamente el primer capítulo de la temporada al entrar
            detail.episodesBySeason[firstSeason]?.firstOrNull()?.let { playEpisode(it.id) }
        }
    }

    fun selectSeason(season: Int) {
        _seriesDetailState.value = _seriesDetailState.value.copy(selectedSeason = season)
    }

    fun playEpisode(episodeId: String) {
        val episode = currentEpisode(episodeId) ?: return
        playerManager.playChannel(episode.streamUrl, "${_seriesDetailState.value.detail?.name} · ${episode.title}")
        _seriesDetailState.value = _seriesDetailState.value.copy(
            currentEpisodeId = episodeId,
            selectedSeason = episode.season
        )
    }

    /** Se llama automáticamente cuando ExoPlayer termina un capítulo */
    private fun playNextEpisode() {
        val state = _seriesDetailState.value
        val detail = state.detail ?: return
        val current = state.currentEpisodeId?.let { currentEpisode(it) } ?: return
        val sameSeasonEpisodes = detail.episodesBySeason[current.season].orEmpty()
        val currentIndex = sameSeasonEpisodes.indexOfFirst { it.id == current.id }

        val next = if (currentIndex in sameSeasonEpisodes.indices && currentIndex < sameSeasonEpisodes.size - 1) {
            sameSeasonEpisodes[currentIndex + 1]
        } else {
            // Fin de temporada: pasa al primer capítulo de la siguiente temporada, si existe
            val nextSeason = detail.episodesBySeason.keys.filter { it > current.season }.minOrNull()
            nextSeason?.let { detail.episodesBySeason[it]?.firstOrNull() }
        }

        next?.let { playEpisode(it.id) }
    }

    private fun currentEpisode(episodeId: String): SeriesEpisode? {
        val detail = _seriesDetailState.value.detail ?: return null
        return detail.episodesBySeason.values.flatten().firstOrNull { it.id == episodeId }
    }

    /** Se llama al salir de la pantalla de detalle de serie, para no seguir auto-avanzando por error */
    fun clearSeriesDetail() {
        playerManager.onPlaybackEnded = null
        _seriesDetailState.value = SeriesDetailUiState()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
