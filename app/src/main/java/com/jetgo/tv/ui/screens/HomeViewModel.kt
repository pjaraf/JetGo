package com.jetgo.tv.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jetgo.tv.BuildConfig
import com.jetgo.tv.data.local.ConfigStore
import com.jetgo.tv.data.local.FavoritesStore
import com.jetgo.tv.data.local.LastPlayingStore
import com.jetgo.tv.data.local.ParentalControlStore
import com.jetgo.tv.data.local.PlaybackPositionStore
import com.jetgo.tv.data.local.WatchHistoryEntry
import com.jetgo.tv.data.local.WatchHistoryStore
import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.data.model.ContentType
import com.jetgo.tv.data.model.ServerConfig
import com.jetgo.tv.data.model.MovieDetail
import com.jetgo.tv.data.model.SeriesDetail
import com.jetgo.tv.data.model.SeriesEpisode
import com.jetgo.tv.data.repository.StreamRepository
import com.jetgo.tv.player.PlayerManager
import com.jetgo.tv.util.AdultContentFilter
import com.jetgo.tv.util.AccessCodeChecker
import com.jetgo.tv.util.UpdateChecker
import com.jetgo.tv.util.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    val errorMessage: String? = null,
    val debugDetail: String? = null
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

data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val detail: MovieDetail? = null,
    val errorMessage: String? = null,
    val debugDetail: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StreamRepository()
    private val configStore = ConfigStore(application)
    private val favoritesStore = FavoritesStore(application)
    val playerManager = PlayerManager(application)

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
            playerManager.stopAll()
            currentConfig = null
            _uiState.value = HomeUiState()
        }
    }

    data class SettingsInfo(
        val clientName: String? = null,
        val accessCode: String? = null,
        val deviceCount: Int = 0,
        val maxDevices: Int = 3,
        val expirationDate: String? = null
    )

    private val _settingsInfo = MutableStateFlow(SettingsInfo())
    val settingsInfo: StateFlow<SettingsInfo> = _settingsInfo.asStateFlow()

    /** Borra archivos temporales/caché (imágenes, progreso e historial) SIN cerrar la sesión */
    fun clearCache(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                coil.Coil.imageLoader(context).memoryCache?.clear()
                coil.Coil.imageLoader(context).diskCache?.clear()
                watchHistoryStore.clear()
                _continueWatching.value = emptyList()
            } catch (e: Exception) { /* ignorar */ }
            onDone()
        }
    }

    /** Cierra la sesión por completo: pide el usuario y contraseña de nuevo la próxima vez */
    fun logout() {
        viewModelScope.launch {
            playerManager.stopAll()
            configStore.clear()
            currentConfig = null
            _uiState.value = HomeUiState()
            _settingsInfo.value = SettingsInfo()
        }
    }

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _homeCatalog = MutableStateFlow(HomeCatalogState())
    val homeCatalog: StateFlow<HomeCatalogState> = _homeCatalog.asStateFlow()
    private var homeCatalogLoaded = false

    private val _seriesDetailState = MutableStateFlow(SeriesDetailUiState())
    val seriesDetailState: StateFlow<SeriesDetailUiState> = _seriesDetailState.asStateFlow()

    private val _movieDetailState = MutableStateFlow(MovieDetailUiState())
    val movieDetailState: StateFlow<MovieDetailUiState> = _movieDetailState.asStateFlow()

    private val positionStore = PlaybackPositionStore(application)
    private val lastPlayingStore = LastPlayingStore(application)

    /** Si el sistema mató la app mientras reproducía algo hace poco, acá queda el ítem para
     *  retomarlo automáticamente apenas la app termina de conectar de nuevo. */
    private val _pendingAutoResume = MutableStateFlow<ContentItem?>(null)
    val pendingAutoResume: StateFlow<ContentItem?> = _pendingAutoResume.asStateFlow()
    fun consumeAutoResume() {
        _pendingAutoResume.value = null
    }

    /** Se pone en true cuando hay que cerrar la app por completo: una demo venció, el código
     *  fue revocado, o el panel mandó la señal de "cerrar ahora" (por ejemplo al renovar). */
    private val _forceCloseApp = MutableStateFlow(false)
    val forceCloseApp: StateFlow<Boolean> = _forceCloseApp.asStateFlow()

    private fun checkForAutoResume() {
        // Desactivado a pedido: si la app se cierra, al volver a abrirla debe partir siempre
        // desde cero, sin ofrecer retomar automáticamente lo que se estaba viendo antes.
        viewModelScope.launch {
            try { lastPlayingStore.clear() } catch (e: Exception) { /* ignorar */ }
        }
    }

    private val watchHistoryStore = WatchHistoryStore(application)
    private val posterCacheStore = com.jetgo.tv.data.local.PosterCacheStore(application)
    private val tmdbApi = com.jetgo.tv.data.remote.TmdbApi.create()

    private val parentalControlStore = ParentalControlStore(application)
    data class ParentalState(val enabled: Boolean = false, val hasPin: Boolean = false)
    private val _parentalState = MutableStateFlow(ParentalState())
    val parentalState: StateFlow<ParentalState> = _parentalState.asStateFlow()

    fun refreshParentalState() {
        viewModelScope.launch {
            val enabled = try { parentalControlStore.isEnabled() } catch (e: Exception) { false }
            val pin = try { parentalControlStore.getPin() } catch (e: Exception) { null }
            _parentalState.value = ParentalState(enabled, !pin.isNullOrBlank())
        }
    }

    /** Activa el control parental con un PIN nuevo de 4 dígitos */
    fun enableParentalControl(pin: String) {
        viewModelScope.launch {
            parentalControlStore.setEnabled(true, pin)
            refreshParentalState()
            homeCatalogLoaded = false
            ensureHomeCatalogLoaded()
        }
    }

    /** Desactiva el control parental (ya validado el PIN antes de llamar esto) */
    fun disableParentalControl() {
        viewModelScope.launch {
            parentalControlStore.setEnabled(false)
            refreshParentalState()
            homeCatalogLoaded = false
            ensureHomeCatalogLoaded()
        }
    }

    suspend fun checkParentalPin(pin: String): Boolean {
        val saved = try { parentalControlStore.getPin() } catch (e: Exception) { null }
        return saved != null && saved == pin
    }

    private val _continueWatching = MutableStateFlow<List<ContentItem>>(emptyList())
    val continueWatching: StateFlow<List<ContentItem>> = _continueWatching.asStateFlow()

    /** Categoría de "Vivo" elegida por última vez en el teléfono — vive acá (no en la pantalla)
     *  para que no se pierda al cambiar de pestaña o volver a entrar a la app. */
    private val _phoneLiveCategoryId = MutableStateFlow<String?>(null)
    val phoneLiveCategoryId: StateFlow<String?> = _phoneLiveCategoryId.asStateFlow()
    fun setPhoneLiveCategoryId(categoryId: String) {
        _phoneLiveCategoryId.value = categoryId
    }

    private val _showNextEpisodeMessage = MutableStateFlow(false)
    val showNextEpisodeMessage: StateFlow<Boolean> = _showNextEpisodeMessage.asStateFlow()

    fun refreshContinueWatching() {
        viewModelScope.launch {
            val entries = try { watchHistoryStore.getAll() } catch (e: Exception) { emptyList() }
            _continueWatching.value = entries.map {
                ContentItem(
                    id = it.id,
                    name = it.name,
                    imageUrl = it.imageUrl,
                    type = if (it.type == "SERIES") ContentType.SERIES else ContentType.MOVIE,
                    streamUrl = null
                )
            }
        }
    }

    data class ResumePrompt(
        val contentKey: String,
        val title: String,
        val streamUrl: String,
        val resumePositionMs: Long,
        val onCompleted: (() -> Unit)? = null
    )

    private val _resumePrompt = MutableStateFlow<ResumePrompt?>(null)
    val resumePrompt: StateFlow<ResumePrompt?> = _resumePrompt.asStateFlow()

    private var positionTrackingJob: kotlinx.coroutines.Job? = null

    private data class LastLiveChannel(val id: String, val name: String, val url: String)
    private var lastLiveChannel: LastLiveChannel? = null
    private var isCurrentlyShowingLive = true

    /** Pausa el reproductor de la pantalla principal (ej. al entrar a Películas/Series) */
    fun pausePreviewPlayer() {
        try { playerManager.livePlayer.pause() } catch (e: Exception) { /* ignorar */ }
    }

    /** Al volver a Inicio: si estaba mostrando otra cosa (película/serie), retoma el último canal EN VIVO */
    fun resumeLastLiveChannelIfNeeded() {
        val last = lastLiveChannel ?: return
        if (isCurrentlyShowingLive) {
            // Ya está en el canal correcto, solo asegurarse de que esté reproduciendo
            try { playerManager.livePlayer.play() } catch (e: Exception) { /* ignorar */ }
        } else {
            playerManager.playChannel(last.url, last.name)
            isCurrentlyShowingLive = true
            _liveChannelInfo.value = LiveChannelInfo(last.name, null, null, null, null, last.id)
        }
    }

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    /** Se llama apenas el usuario toca "Actualizar": el aviso no debe volver a salir hasta
     *  que exista una actualización realmente NUEVA (evita que reaparezca si el proceso
     *  de la app sigue vivo después de instalar, en vez de arrancar de cero). */
    fun dismissUpdateBanner() {
        _updateInfo.value = null
    }

    private var currentConfig: ServerConfig? = null

    /** Nombres (en minúscula) de categorías que el administrador ocultó — combinado de todas
     *  las fuentes (se usa solo para saber si hay que filtrar contenido en modo M3U simple). */
    private var hiddenCategoryNames: Set<String> = emptySet()

    /** Secciones completas ocultas por el administrador para este cliente
     *  (valores: "live", "movie", "series") — la interfaz no muestra esos botones/pestañas
     *  SOLO SI TODAS las fuentes combinadas la ocultan (si una la muestra, el botón se queda,
     *  y solo se filtra el contenido de la fuente que la ocultó). */
    private val _hiddenTypes = MutableStateFlow<Set<String>>(emptySet())
    val hiddenTypes: StateFlow<Set<String>> = _hiddenTypes.asStateFlow()

    /** Ocultamiento configurado POR CADA fuente combinada (mismo orden que [currentSources]),
     *  para poder mostrar igual una sección si al menos una de las fuentes la permite, filtrando
     *  solo el contenido de la fuente que la tiene oculta. */
    private var sourceHiddenConfigs: List<Pair<Set<String>, Set<String>>> = emptyList() // (categorías, tipos) por índice

    private var currentMode: String = "xtream" // "xtream" o "m3u"

    // Caché en memoria del catálogo completo para búsqueda instantánea tras la primera carga
    private var searchCatalog: List<ContentItem>? = null

    // Cuando el servidor no informa la extensión real del archivo, se van probando otras
    // comunes en orden si la actual falla — sin que el usuario tenga que hacer nada.
    private var pendingAlternateUrls: List<String> = emptyList()
    private var pendingContentKey: String = ""
    private var pendingTitle: String = ""
    private var pendingOnCompleted: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            androidx.compose.runtime.snapshotFlow { playerManager.playbackError.value }
                .collect { error ->
                    if (error != null && pendingAlternateUrls.isNotEmpty()) {
                        val nextUrl = pendingAlternateUrls.first()
                        pendingAlternateUrls = pendingAlternateUrls.drop(1)
                        playWithResumeCheck(pendingContentKey, pendingTitle, nextUrl, pendingOnCompleted)
                    }
                }
        }
        android.util.Log.e("JetGo_DIAG", "HomeViewModel se está creando de nuevo", Exception("rastro de diagnóstico"))

        viewModelScope.launch {
            configStore.mode.collect { currentMode = it }
        }
        viewModelScope.launch {
            configStore.config.collect { config ->
                currentConfig = config
                if (config != null) connectXtream(config)
            }
        }
        viewModelScope.launch {
            favoritesStore.favorites.collect { _favorites.value = it }
        }

        checkForUpdate()
    }

    /** Vuelve a intentar la conexión con la configuración ya guardada */
    fun retryConnection() {
        viewModelScope.launch {
            val config = configStore.config.first()
            if (config != null) {
                connectXtream(config)
            }
        }
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

    fun loginWithCode(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val context = getApplication<Application>()
            val projectId = context.getString(com.jetgo.tv.R.string.firebase_project_id)
            val deviceId = com.jetgo.tv.util.getDeviceId(context)
            val deviceName = com.jetgo.tv.util.getDeviceDisplayName(context)

            val result = withContext(Dispatchers.IO) {
                AccessCodeChecker.checkCodeAndRegisterDevice(projectId, code, deviceId, deviceName)
            }

            if (!result.valid) {
                val errorMsg = if (result.deviceLimitReached) {
                    "Límite de dispositivos alcanzado (máximo ${result.maxDevices})"
                } else {
                    "Código incorrecto o inactivo"
                }
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = errorMsg)
                return@launch
            }

            if (result.sources.isNotEmpty()) {
                connectMultiSource(result.sources)
            } else if (result.mode == "m3u" && !result.m3uUrl.isNullOrBlank()) {
                connectM3u(result.m3uUrl)
            } else {
                val host = result.host ?: "http://redworld.pro:8880"
                val username = result.username ?: code
                val password = result.password ?: code
                connectXtream(ServerConfig(host, username, password))
            }
        }
    }

    fun connectXtream(config: ServerConfig, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }
            configStore.saveXtream(config.host, config.username, config.password)
            currentConfig = config
            currentMode = "xtream"
            try {
                val userInfo = repository.login(config)
                if (userInfo == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "No se pudo autenticar con el servidor")
                    return@launch
                }
                val expirationText = userInfo.expDate?.toLongOrNull()?.let { unixSeconds ->
                    try {
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(unixSeconds * 1000))
                    } catch (e: Exception) { null }
                }
                _settingsInfo.value = _settingsInfo.value.copy(expirationDate = expirationText)
                val channels = repository.getLiveChannels(config, categoryId = null)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isConfigured = true,
                    liveChannels = channels
                )
                // En una reconexión silenciosa, si YA está reproduciendo algo (por ejemplo el
                // canal que el usuario acaba de tocar), no lo interrumpe volviendo a poner el
                // primer canal de la lista.
                if (!silent || !isCurrentlyShowingLive) {
                    val lastId = try { configStore.getLastChannelId() } catch (e: Exception) { null }
                    val startingChannel = channels.firstOrNull { it.streamId == lastId } ?: channels.firstOrNull()
                    startingChannel?.let { playChannel(it) }
                }
                ensureHomeCatalogLoaded()
                refreshContinueWatching()
                refreshParentalState()
                checkForAutoResume()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "No se pudo conectar al servidor. Verifica el host, usuario y contraseña.",
                    debugDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    fun connectM3u(url: String, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }
            configStore.saveM3u(url)
            currentMode = "m3u"
            try {
                val result = repository.loadFromM3u(url)
                if (result.channels.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "La lista M3U está vacía o no se pudo leer. Verifica la URL."
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isConfigured = true,
                    liveChannels = result.channels
                )
                if (!silent || !isCurrentlyShowingLive) {
                    val lastId = try { configStore.getLastChannelId() } catch (e: Exception) { null }
                    val startingChannel = result.channels.firstOrNull { it.streamId == lastId } ?: result.channels.firstOrNull()
                    startingChannel?.let { playChannel(it) }
                }
                checkForAutoResume()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "No se pudo cargar la lista M3U. Verifica la URL o tu conexión a internet.",
                    debugDetail = "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    /** Hasta 2 fuentes combinadas (2 Xtream, o Xtream + M3U) para el código actual — vacío si
     *  el código usa el formato viejo de una sola fuente (esos siguen por connectXtream/M3u). */
    private var currentSources: List<com.jetgo.tv.util.ContentSource> = emptyList()

    /** Conecta con hasta 2 fuentes de contenido a la vez (2 servidores Xtream, o un Xtream +
     *  una lista M3U/OTT) y junta los canales en vivo de ambas en una sola lista — así el
     *  cliente ve todo junto, sin tener que elegir entre un servidor u otro. Las categorías de
     *  Vivo quedan separadas por fuente (con el nombre de cada una) para que no se mezclen
     *  categorías con el mismo nombre que en realidad son cosas distintas en cada servidor.
     */
    fun connectMultiSource(sources: List<com.jetgo.tv.util.ContentSource>, silent: Boolean = false) {
        currentSources = sources
        // Se usa la primera fuente Xtream (si hay alguna) para todo lo que todavía no está
        // adaptado a múltiples fuentes (el carrusel de Inicio, la búsqueda) — así esas partes
        // siguen funcionando mostrando contenido real, aunque sea solo de una de las dos.
        val firstXtream = sources.firstOrNull { it.type != "m3u" && !it.host.isNullOrBlank() && !it.username.isNullOrBlank() && !it.password.isNullOrBlank() }
        currentConfig = firstXtream?.let { ServerConfig(it.host!!, it.username!!, it.password!!) }
        currentMode = "xtream" // para que las funciones que miran currentMode traten esto como Xtream, no M3U puro

        viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }
            val allLiveChannels = mutableListOf<Channel>()
            var anySourceWorked = false

            sources.forEachIndexed { index, source ->
                val (sourceHiddenCats, sourceHiddenTypes) = sourceHiddenConfigs.getOrNull(index) ?: (emptySet<String>() to emptySet<String>())
                if (sourceHiddenTypes.contains("live")) {
                    // Esta fuente tiene Vivo oculto: no aporta canales, pero la otra fuente
                    // (si la tiene visible) sigue mostrando los suyos con normalidad.
                    return@forEachIndexed
                }
                try {
                    if (source.type == "m3u" && !source.m3uUrl.isNullOrBlank()) {
                        val result = repository.loadFromM3u(source.m3uUrl)
                        allLiveChannels += result.channels
                            .filterNot { sourceHiddenCats.contains(it.categoryId.trim().lowercase()) }
                            .map { it.copy(streamId = "src${index}_${it.streamId}", categoryId = "$index::${it.categoryId}") }
                        anySourceWorked = true
                    } else if (!source.host.isNullOrBlank() && !source.username.isNullOrBlank() && !source.password.isNullOrBlank()) {
                        val config = ServerConfig(source.host, source.username, source.password)
                        val categoryNameById = try {
                            repository.getLiveCategories(config).associate { it.id to it.name }
                        } catch (e: Exception) { emptyMap() }
                        val channels = repository.getLiveChannels(config, categoryId = null)
                        allLiveChannels += channels
                            .filterNot { sourceHiddenCats.contains((categoryNameById[it.categoryId] ?: it.categoryId).trim().lowercase()) }
                            .map {
                                val readableCategory = categoryNameById[it.categoryId] ?: it.categoryId
                                it.copy(streamId = "src${index}_${it.streamId}", categoryId = "$index::$readableCategory")
                            }
                        anySourceWorked = true
                    }
                } catch (e: Exception) {
                    // Si una de las dos fuentes falla, se sigue con la otra en vez de fallar todo
                }
            }

            if (!anySourceWorked) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "No se pudo conectar con ninguno de los servidores configurados para este código."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isConfigured = true,
                liveChannels = allLiveChannels
            )
            if (!silent || !isCurrentlyShowingLive) {
                val lastId = try { configStore.getLastChannelId() } catch (e: Exception) { null }
                val startingChannel = allLiveChannels.firstOrNull { it.streamId == lastId } ?: allLiveChannels.firstOrNull()
                startingChannel?.let { playChannel(it) }
            }
            if (firstXtream != null) ensureHomeCatalogLoaded()
            refreshContinueWatching()
            refreshParentalState()
            checkForAutoResume()
        }
    }

    data class LiveChannelInfo(
        val channelName: String,
        val channelLogo: String?,
        val channelNumber: Int?,
        val current: com.jetgo.tv.data.model.EpgProgram?,
        val next: com.jetgo.tv.data.model.EpgProgram?,
        val channelId: String? = null,
        val categoryId: String? = null
    )

    private val _liveChannelInfo = MutableStateFlow<LiveChannelInfo?>(null)
    val liveChannelInfo: StateFlow<LiveChannelInfo?> = _liveChannelInfo.asStateFlow()

    /** La consulta de programación (EPG) del canal anterior — si el cliente cambia de canal
     *  rápido, se cancela la consulta vieja para que no llegue tarde y pise el banner con
     *  información de un canal que ya no es el que está viendo. */
    private var epgFetchJob: kotlinx.coroutines.Job? = null

    fun playChannel(channel: Channel) {
        playerManager.playChannel(channel.streamUrl, channel.name)
        lastLiveChannel = LastLiveChannel(channel.streamId, channel.name, channel.streamUrl)
        isCurrentlyShowingLive = true
        viewModelScope.launch { configStore.saveLastChannelId(channel.streamId) }
        _liveChannelInfo.value = LiveChannelInfo(channel.name, channel.logoUrl, channel.number, null, null, channel.streamId, channel.categoryId)

        epgFetchJob?.cancel()
        val config = currentConfig ?: return
        epgFetchJob = viewModelScope.launch {
            val epg = try { repository.getShortEpg(config, channel.streamId) } catch (e: Exception) { emptyList() }
            // Si mientras se esperaba esta consulta el cliente ya cambió a otro canal, esta
            // respuesta llegó tarde y no corresponde — no se aplica.
            if (_liveChannelInfo.value?.channelId != channel.streamId) return@launch
            _liveChannelInfo.value = LiveChannelInfo(
                channelName = channel.name,
                channelLogo = channel.logoUrl,
                channelNumber = channel.number,
                current = epg.getOrNull(0),
                next = epg.getOrNull(1),
                channelId = channel.streamId,
                categoryId = channel.categoryId
            )
        }
    }

    // ---------------------------------------------------------------------
    // Selector de subcategorías (evita traer TODO el catálogo de golpe)
    // ---------------------------------------------------------------------

    /** Paso 1: lista las subcategorías disponibles para el tipo tocado (Vivo/Serie/Película/Anime/Especial) */
    fun loadCategoriesForType(type: ContentType) {
        _categoryPickerState.value = CategoryPickerUiState(isLoading = true)
        _categoryContentState.value = CategoryContentUiState(isLoading = true)

        // Vivo con lista M3U pura, o con 2 fuentes combinadas: las categorías salen del propio
        // listado de canales ya cargado (no hay una sola API de categorías para todo junto).
        if (type == ContentType.LIVE && (currentMode == "m3u" || currentSources.size > 1)) {
            val categories = _uiState.value.liveChannels.map { it.categoryId }.distinct()
                .filterNot { hiddenCategoryNames.contains(it.substringAfter("::").trim().lowercase()) }
                .map { Category(id = it, name = it.substringAfter("::"), type = ContentType.LIVE) }
            _categoryPickerState.value = CategoryPickerUiState(categories = categories)
            if (categories.isNotEmpty()) {
                val target = _phoneLiveCategoryId.value?.takeIf { id -> categories.any { it.id == id } }
                    ?: categories.first().id
                loadCategoryContent(ContentType.LIVE, target)
            }
            return
        }

        if (currentMode == "m3u") {
            // Lista M3U pura (sin combinar) y no es Vivo: no maneja categorías de películas/series
            _categoryPickerState.value = CategoryPickerUiState(categories = emptyList())
            _categoryContentState.value = CategoryContentUiState(items = emptyList())
            return
        }

        if (currentSources.size > 1) {
            // Dos fuentes combinadas: se juntan las categorías de películas/series de cada
            // servidor Xtream que tenga (las fuentes M3U no aportan acá), separadas con un
            // prefijo para saber a cuál servidor pertenece cada una al elegirla después.
            val typeKey = when (type) {
                ContentType.MOVIE, ContentType.ANIME, ContentType.SPECIAL -> "movie"
                ContentType.SERIES -> "series"
                ContentType.LIVE -> "live"
            }
            viewModelScope.launch {
                val allCategories = mutableListOf<Category>()
                currentSources.forEachIndexed { index, source ->
                    if (source.type == "m3u" || source.host.isNullOrBlank() || source.username.isNullOrBlank() || source.password.isNullOrBlank()) return@forEachIndexed
                    val (sourceHiddenCats, sourceHiddenTypes) = sourceHiddenConfigs.getOrNull(index) ?: (emptySet<String>() to emptySet<String>())
                    if (sourceHiddenTypes.contains(typeKey)) {
                        // Esta fuente tiene esta sección oculta: no aporta categorías de acá,
                        // pero la otra fuente (si la tiene visible) sigue mostrando las suyas.
                        return@forEachIndexed
                    }
                    try {
                        val config = ServerConfig(source.host, source.username, source.password)
                        val categories = when (type) {
                            ContentType.MOVIE -> repository.getVodCategories(config)
                            ContentType.SERIES -> repository.getSeriesCategories(config)
                            ContentType.ANIME -> repository.getVodCategoriesByKeyword(config, "anime")
                            ContentType.SPECIAL -> {
                                val especial = repository.getVodCategoriesByKeyword(config, "especial")
                                val special = repository.getVodCategoriesByKeyword(config, "special")
                                (especial + special).distinctBy { it.id }
                            }
                            ContentType.LIVE -> emptyList()
                        }
                        allCategories += categories
                            .filterNot { sourceHiddenCats.contains(it.name.trim().lowercase()) }
                            .map { it.copy(id = "$index::${it.id}") }
                    } catch (e: Exception) {
                        // Si una fuente falla al traer categorías, se sigue con la otra
                    }
                }
                val filtered = if (_parentalState.value.enabled) {
                    allCategories.filterNot { AdultContentFilter.isAdult(it.name) }
                } else allCategories
                _categoryPickerState.value = CategoryPickerUiState(categories = filtered)
                if (filtered.isNotEmpty()) {
                    loadCategoryContent(type, filtered.first().id)
                }
            }
            return
        }

        val config = currentConfig ?: run {
            _categoryPickerState.value = CategoryPickerUiState(errorMessage = "Sin configuración de servidor")
            _categoryContentState.value = CategoryContentUiState(errorMessage = "Sin configuración de servidor")
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
                val filtered = categories
                    .filterNot { hiddenCategoryNames.contains(it.name.trim().lowercase()) }
                    .let { list ->
                        if (_parentalState.value.enabled) list.filterNot { AdultContentFilter.isAdult(it.name) } else list
                    }
                _categoryPickerState.value = CategoryPickerUiState(categories = filtered)

                if (filtered.isNotEmpty()) {
                    if (type == ContentType.LIVE) {
                        val target = _phoneLiveCategoryId.value?.takeIf { id -> filtered.any { it.id == id } }
                            ?: filtered.first().id
                        loadCategoryContent(ContentType.LIVE, target)
                    } else {
                        loadCategoryContent(type, filtered.first().id)
                    }
                }
            } catch (e: Exception) {
                _categoryPickerState.value = CategoryPickerUiState(errorMessage = "Error al cargar categorías: ${e.message}")
            }
        }
    }

    /** Paso 2: dentro de la subcategoría elegida, trae solo el contenido de esa categoría */
    fun loadCategoryContent(type: ContentType, categoryId: String) {
        if (type == ContentType.LIVE) {
            _phoneLiveCategoryId.value = categoryId
        }
        _categoryContentState.value = CategoryContentUiState(isLoading = true)

        // Vivo combinado o M3U puro: el contenido sale del listado de canales ya cargado
        if (type == ContentType.LIVE && (currentMode == "m3u" || currentSources.size > 1)) {
            val items = _uiState.value.liveChannels
                .filter { it.categoryId == categoryId }
                .map { ContentItem(it.streamId, it.name, it.logoUrl, ContentType.LIVE, it.streamUrl) }
            _categoryContentState.value = CategoryContentUiState(items = items)
            return
        }

        if (currentMode == "m3u") {
            _categoryContentState.value = CategoryContentUiState(items = emptyList())
            return
        }

        if (currentSources.size > 1) {
            // El categoryId viene con el prefijo "<índice>::<id real>" — se usa para saber a
            // cuál de las 2 fuentes combinadas pedirle el contenido.
            val sourceIndex = categoryId.substringBefore("::", "").toIntOrNull()
            val realCategoryId = categoryId.substringAfter("::", categoryId)
            val source = sourceIndex?.let { currentSources.getOrNull(it) }
            if (source == null || source.host.isNullOrBlank() || source.username.isNullOrBlank() || source.password.isNullOrBlank()) {
                _categoryContentState.value = CategoryContentUiState(errorMessage = "Fuente no encontrada")
                return
            }
            val config = ServerConfig(source.host, source.username, source.password)
            viewModelScope.launch {
                try {
                    val items = when (type) {
                        ContentType.MOVIE, ContentType.ANIME, ContentType.SPECIAL -> repository.getMovies(config, realCategoryId).map {
                            ContentItem("$sourceIndex::${it.streamId}", it.name, it.coverUrl, type, it.streamUrl, rating = it.rating)
                        }
                        ContentType.SERIES -> repository.getSeries(config, realCategoryId).map {
                            ContentItem("$sourceIndex::${it.seriesId}", it.name, it.coverUrl, ContentType.SERIES, null, rating = it.rating)
                        }
                        ContentType.LIVE -> emptyList()
                    }
                    val filteredItems = (if (_parentalState.value.enabled) items.filterNot { AdultContentFilter.isAdult(it.name) } else items).distinctBy { it.id }
                    _categoryContentState.value = CategoryContentUiState(items = filteredItems)
                    fillMissingPosters(filteredItems, isSeries = type == ContentType.SERIES) { updated ->
                        _categoryContentState.value = _categoryContentState.value.copy(items = updated)
                    }
                } catch (e: Exception) {
                    _categoryContentState.value = CategoryContentUiState(errorMessage = "Error al cargar contenido: ${e.message}")
                }
            }
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
                        ContentItem(it.streamId, it.name, it.coverUrl, type, it.streamUrl, rating = it.rating)
                    }
                    ContentType.SERIES -> repository.getSeries(config, categoryId).map {
                        ContentItem(it.seriesId, it.name, it.coverUrl, ContentType.SERIES, null, rating = it.rating)
                    }
                }
                val filteredItems = if (type != ContentType.LIVE && _parentalState.value.enabled) {
                    items.filterNot { AdultContentFilter.isAdult(it.name) }
                } else items
                val dedupedItems = if (type != ContentType.LIVE) filteredItems.distinctBy { it.id } else filteredItems
                _categoryContentState.value = CategoryContentUiState(items = dedupedItems)

                if (type != ContentType.LIVE) {
                    fillMissingPosters(dedupedItems, isSeries = type == ContentType.SERIES) { updated ->
                        _categoryContentState.value = _categoryContentState.value.copy(items = updated)
                    }
                }
            } catch (e: Exception) {
                _categoryContentState.value = CategoryContentUiState(errorMessage = "Error al cargar contenido: ${e.message}")
            }
        }
    }

    /** Se llama al tocar un ítem final. Resuelve el episodio si es una serie. */
    fun selectContentItem(item: ContentItem, onReady: () -> Unit) {
        pendingAlternateUrls = emptyList()
        if (item.streamUrl != null) {
            val isVod = item.type == ContentType.MOVIE || item.type == ContentType.ANIME || item.type == ContentType.SPECIAL
            if (isVod) {
                playWithResumeCheck("movie:${item.id}", item.name, item.streamUrl, onCompleted = {
                    viewModelScope.launch {
                        watchHistoryStore.remove(item.id, "MOVIE")
                        refreshContinueWatching()
                    }
                })
            } else {
                playerManager.playChannel(item.streamUrl, item.name)
                lastLiveChannel = LastLiveChannel(item.id, item.name, item.streamUrl)
                isCurrentlyShowingLive = true
                viewModelScope.launch { configStore.saveLastChannelId(item.id) }
                _liveChannelInfo.value = LiveChannelInfo(item.name, item.imageUrl, null, null, null, item.id)
                val config = currentConfig
                if (config != null) {
                    viewModelScope.launch {
                        val epg = try { repository.getShortEpg(config, item.id) } catch (e: Exception) { emptyList() }
                        _liveChannelInfo.value = LiveChannelInfo(item.name, item.imageUrl, null, epg.getOrNull(0), epg.getOrNull(1), item.id)
                    }
                }
            }
            onReady()
            return
        }
        val config = currentConfig ?: return
        viewModelScope.launch {
            val url = repository.getFirstEpisodeUrl(config, item.id)
            if (url != null) {
                playerManager.playChannel(url, item.name, isLive = false)
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
        var results = if (query.isBlank()) emptyList() else catalog.filter {
            it.name.contains(query, ignoreCase = true)
        }
        if (_parentalState.value.enabled) {
            results = results.filterNot { AdultContentFilter.isAdult(it.name) }
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
            // Las 5 consultas son independientes entre sí: se piden todas al mismo tiempo
            // en vez de esperar una por una (mucho más rápido para cargar todo de una vez).
            val vodCategoryNamesDeferred = async {
                try { repository.getVodCategories(config).associate { it.id to it.name } }
                catch (e: Exception) { emptyMap() }
            }
            val seriesCategoryNamesDeferred = async {
                try { repository.getSeriesCategories(config).associate { it.id to it.name } }
                catch (e: Exception) { emptyMap() }
            }
            val moviesDeferred = async {
                try { repository.getMovies(config, categoryId = null) } catch (e: Exception) { emptyList() }
            }
            val seriesDeferred = async {
                try { repository.getSeries(config, categoryId = null) } catch (e: Exception) { emptyList() }
            }
            val animeDeferred = async {
                try { repository.getMoviesByCategoryKeyword(config, "anime") } catch (e: Exception) { emptyList() }
            }

            val vodCategoryNames = vodCategoryNamesDeferred.await()
            val seriesCategoryNames = seriesCategoryNamesDeferred.await()

            val movies = moviesDeferred.await().map {
                ContentItem(it.streamId, it.name, it.coverUrl, ContentType.MOVIE, it.streamUrl, categoryName = vodCategoryNames[it.categoryId])
            }
            val series = seriesDeferred.await().map {
                ContentItem(it.seriesId, it.name, it.coverUrl, ContentType.SERIES, null, categoryName = seriesCategoryNames[it.categoryId])
            }
            val anime = animeDeferred.await().map {
                ContentItem(it.streamId, it.name, it.coverUrl, ContentType.ANIME, it.streamUrl, categoryName = vodCategoryNames[it.categoryId])
            }

            val parentalOn = _parentalState.value.enabled
            val cleanMovies = (if (parentalOn) movies.filterNot { AdultContentFilter.isAdult(it.name) } else movies).distinctBy { it.id }
            val cleanSeries = (if (parentalOn) series.filterNot { AdultContentFilter.isAdult(it.name) } else series).distinctBy { it.id }
            val cleanAnime = (if (parentalOn) anime.filterNot { AdultContentFilter.isAdult(it.name) } else anime).distinctBy { it.id }

            _homeCatalog.value = HomeCatalogState(isLoading = false, movies = cleanMovies, series = cleanSeries, anime = cleanAnime)
            homeCatalogLoaded = true

            fillMissingPosters(cleanMovies, isSeries = false) { updated ->
                _homeCatalog.value = _homeCatalog.value.copy(movies = updated)
            }
            fillMissingPosters(cleanSeries, isSeries = true) { updated ->
                _homeCatalog.value = _homeCatalog.value.copy(series = updated)
            }
            fillMissingPosters(cleanAnime, isSeries = false) { updated ->
                _homeCatalog.value = _homeCatalog.value.copy(anime = updated)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Pantalla de detalle de serie: temporadas, capítulos, auto-avance
    // ---------------------------------------------------------------------

    /** Para un ítem de película/serie: si su ID trae el prefijo "<índice>::<id real>" (viene de
     *  una fuente combinada), devuelve el servidor correcto de esa fuente puntual y el ID real
     *  sin el prefijo. Si no trae prefijo (código de un solo servidor), usa el servidor normal. */
    private fun resolveConfigForItem(itemId: String): Pair<ServerConfig?, String> {
        val sourceIndex = itemId.substringBefore("::", "").toIntOrNull()
        if (sourceIndex != null && currentSources.size > 1) {
            val realId = itemId.substringAfter("::", itemId)
            val source = currentSources.getOrNull(sourceIndex)
            val config = if (source != null && !source.host.isNullOrBlank() && !source.username.isNullOrBlank() && !source.password.isNullOrBlank()) {
                ServerConfig(source.host, source.username, source.password)
            } else null
            return Pair(config, realId)
        }
        return Pair(currentConfig, itemId)
    }

    /** Carga toda la ficha de la serie (sinopsis + todas las temporadas/capítulos) */
    fun loadSeriesDetail(item: ContentItem) {
        _seriesDetailState.value = SeriesDetailUiState(isLoading = true)
        viewModelScope.launch {
            watchHistoryStore.record(
                WatchHistoryEntry(item.id, item.name, item.imageUrl, "SERIES", System.currentTimeMillis())
            )
            refreshContinueWatching()
        }
        val (config, realId) = resolveConfigForItem(item.id)
        val resolvedConfig = config ?: run {
            _seriesDetailState.value = SeriesDetailUiState(errorMessage = "Sin configuración de servidor")
            return
        }
        viewModelScope.launch {
            val detail = try {
                repository.getSeriesDetail(resolvedConfig, realId, item.name, item.imageUrl)
            } catch (e: Exception) { null }
            if (detail == null) {
                _seriesDetailState.value = SeriesDetailUiState(errorMessage = "No se pudo cargar la serie")
                return@launch
            }
            val allEpisodes = detail.episodesBySeason.values.flatten()
            val lastWatchedId = try { positionStore.getLastWatchedEpisode(detail.seriesId) } catch (e: Exception) { null }
            val lastWatchedEpisode = lastWatchedId?.let { id -> allEpisodes.firstOrNull { it.id == id } }
            val startingEpisode = lastWatchedEpisode ?: detail.episodesBySeason.keys.minOrNull()
                ?.let { detail.episodesBySeason[it]?.firstOrNull() }

            _seriesDetailState.value = SeriesDetailUiState(
                detail = detail,
                selectedSeason = startingEpisode?.season ?: (detail.episodesBySeason.keys.minOrNull() ?: 1)
            )
            // Auto-avance: cuando termina un capítulo, reproduce el siguiente automáticamente
            playerManager.onPlaybackEnded = { playNextEpisode() }
            // Retoma el último capítulo visto de esta serie; si nunca la habías visto, arranca en el primero
            startingEpisode?.let { playEpisode(it.id) }
        }
    }

    fun selectSeason(season: Int) {
        _seriesDetailState.value = _seriesDetailState.value.copy(selectedSeason = season)
    }

    fun playEpisode(episodeId: String) {
        val episode = currentEpisode(episodeId) ?: return
        _seriesDetailState.value = _seriesDetailState.value.copy(
            currentEpisodeId = episodeId,
            selectedSeason = episode.season
        )
        val seriesId = _seriesDetailState.value.detail?.seriesId
        seriesId?.let { id ->
            viewModelScope.launch { positionStore.saveLastWatchedEpisode(id, episodeId) }
        }
        val title = "${_seriesDetailState.value.detail?.name} · ${episode.title}"
        playWithResumeCheck("series:$episodeId", title, episode.streamUrl, onCompleted = {
            seriesId?.let { id ->
                viewModelScope.launch {
                    watchHistoryStore.remove(id, "SERIES")
                    refreshContinueWatching()
                }
            }
        })
    }

    /** Reproduce directo, sin preguntar "seguir viendo" (usado en el auto-avance entre capítulos) */
    private fun playEpisodeDirect(episodeId: String) {
        val episode = currentEpisode(episodeId) ?: return
        val seriesId = _seriesDetailState.value.detail?.seriesId
        val seriesDetail = _seriesDetailState.value.detail
        playerManager.playChannel(episode.streamUrl, "${_seriesDetailState.value.detail?.name} · ${episode.title}", isLive = false)
        startPositionTracking("series:$episodeId", onCompleted = {
            seriesId?.let { id ->
                viewModelScope.launch {
                    watchHistoryStore.remove(id, "SERIES")
                    refreshContinueWatching()
                    lastPlayingStore.clear()
                }
            }
        })
        if (seriesId != null && seriesDetail != null) {
            viewModelScope.launch {
                lastPlayingStore.save(seriesId, "SERIES", seriesDetail.name, seriesDetail.coverUrl)
            }
        }
        _seriesDetailState.value = _seriesDetailState.value.copy(
            currentEpisodeId = episodeId,
            selectedSeason = episode.season
        )
        _seriesDetailState.value.detail?.seriesId?.let { seriesId ->
            viewModelScope.launch { positionStore.saveLastWatchedEpisode(seriesId, episodeId) }
        }
    }

    /** Se llama automáticamente cuando el reproductor termina un capítulo */
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

        if (next != null) {
            _showNextEpisodeMessage.value = true
            viewModelScope.launch {
                kotlinx.coroutines.delay(2500)
                _showNextEpisodeMessage.value = false
            }
            playEpisodeDirect(next.id)
        } else {
            // No hay más capítulos: sale de pantalla completa y avisa para volver atrás
            exitFullscreenPlayer()
            onSeriesFullyFinished?.invoke()
        }
    }

    /** MainActivity la usa para volver a la pantalla anterior cuando termina el último capítulo */
    var onSeriesFullyFinished: (() -> Unit)? = null

    private fun currentEpisode(episodeId: String): SeriesEpisode? {
        val detail = _seriesDetailState.value.detail ?: return null
        return detail.episodesBySeason.values.flatten().firstOrNull { it.id == episodeId }
    }

    /** Se llama al salir de la pantalla de detalle de serie, para no seguir auto-avanzando por error */
    fun clearSeriesDetail() {
        playerManager.onPlaybackEnded = null
        onSeriesFullyFinished = null
        stopPositionTracking()
        try { playerManager.vodPlayer.pause() } catch (e: Exception) { /* ignorar */ }
        viewModelScope.launch { lastPlayingStore.clear() }
        _seriesDetailState.value = SeriesDetailUiState()
    }

    // ---------------------------------------------------------------------
    // Pantalla de detalle de película (info + reproducción automática)
    // ---------------------------------------------------------------------

    /** Carga la ficha completa de la película y empieza a reproducirla automáticamente */
    fun loadMovieDetail(item: ContentItem) {
        _movieDetailState.value = MovieDetailUiState(isLoading = true)
        viewModelScope.launch {
            watchHistoryStore.record(
                WatchHistoryEntry(item.id, item.name, item.imageUrl, "MOVIE", System.currentTimeMillis())
            )
            refreshContinueWatching()
        }
        val (config, realId) = resolveConfigForItem(item.id)
        val resolvedConfig = config ?: run {
            _movieDetailState.value = MovieDetailUiState(errorMessage = "Sin configuración de servidor")
            return
        }
        val fallbackStreamUrl = item.streamUrl ?: ""
        viewModelScope.launch {
            var debugDetail: String? = null
            val detail = try {
                repository.getMovieDetail(resolvedConfig, realId, item.name, item.imageUrl, fallbackStreamUrl)
            } catch (e: Exception) {
                debugDetail = "${e.javaClass.simpleName}: ${e.message}"
                null
            }
            if (detail == null || detail.streamUrl.isBlank()) {
                _movieDetailState.value = MovieDetailUiState(
                    errorMessage = "No se pudo cargar la película",
                    debugDetail = debugDetail ?: "El servidor no devolvió una URL de video válida (streamUrl vacío)"
                )
                return@launch
            }
            _movieDetailState.value = MovieDetailUiState(detail = detail)
            playerManager.onPlaybackEnded = null
            pendingAlternateUrls = detail.alternateStreamUrls
            pendingContentKey = "movie:${item.id}"
            pendingTitle = detail.name
            viewModelScope.launch {
                lastPlayingStore.save(item.id, "MOVIE", detail.name, detail.coverUrl)
            }
            pendingOnCompleted = {
                viewModelScope.launch {
                    watchHistoryStore.remove(item.id, "MOVIE")
                    refreshContinueWatching()
                    lastPlayingStore.clear()
                }
            }
            playWithResumeCheck("movie:${item.id}", detail.name, detail.streamUrl, onCompleted = pendingOnCompleted)
        }
    }

    /** Se llama al salir de la pantalla de detalle de película */
    fun clearMovieDetail() {
        stopPositionTracking()
        playerManager.onPlaybackEnded = null
        try { playerManager.vodPlayer.pause() } catch (e: Exception) { /* ignorar */ }
        viewModelScope.launch { lastPlayingStore.clear() }
        _movieDetailState.value = MovieDetailUiState()
    }

    // ---------------------------------------------------------------------
    // "Seguir viendo" / "Desde el inicio" (películas, series y anime)
    // ---------------------------------------------------------------------

    /**
     * Punto de entrada único para reproducir una película/episodio: si hay progreso guardado,
     * pregunta antes de reproducir; si no, arranca directo desde el principio.
     */
    private fun playWithResumeCheck(contentKey: String, title: String, streamUrl: String, onCompleted: (() -> Unit)? = null) {
        isCurrentlyShowingLive = false
        viewModelScope.launch {
            val saved = try { positionStore.get(contentKey) } catch (e: Exception) { null }
            val hasMeaningfulProgress = saved != null &&
                saved.positionMs > 8_000 &&
                saved.positionMs < (saved.durationMs * 0.95)
            if (hasMeaningfulProgress && saved != null) {
                _resumePrompt.value = ResumePrompt(contentKey, title, streamUrl, saved.positionMs, onCompleted)
            } else {
                playerManager.playChannel(streamUrl, title, isLive = false)
                startPositionTracking(contentKey, onCompleted)
            }
        }
    }

    fun resumeFromPrompt() {
        val prompt = _resumePrompt.value ?: return
        playerManager.playChannel(prompt.streamUrl, prompt.title, isLive = false)
        startPositionTracking(prompt.contentKey, prompt.onCompleted)
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // deja que el reproductor prepare el contenido antes de saltar
            playerManager.seekTo(prompt.resumePositionMs)
        }
        _resumePrompt.value = null
    }

    fun startOverFromPrompt() {
        val prompt = _resumePrompt.value ?: return
        playerManager.playChannel(prompt.streamUrl, prompt.title, isLive = false)
        startPositionTracking(prompt.contentKey, prompt.onCompleted)
        _resumePrompt.value = null
    }

    fun dismissResumePrompt() {
        _resumePrompt.value = null
    }

    private fun startPositionTracking(contentKey: String, onCompleted: (() -> Unit)? = null) {
        positionTrackingJob?.cancel()
        positionTrackingJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(7_000)
                val pos = playerManager.currentPositionMs()
                val dur = playerManager.durationMs()
                if (dur > 0) {
                    if (pos > dur * 0.95) {
                        positionStore.clear(contentKey)
                        onCompleted?.invoke()
                    } else if (pos > 5_000) {
                        positionStore.save(contentKey, pos, dur)
                    }
                }
            }
        }
    }

    private fun stopPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = null
    }

    /** Quita manualmente un ítem de "Seguir viendo" (botón de basurero) */
    fun removeFromContinueWatching(item: ContentItem) {
        viewModelScope.launch {
            watchHistoryStore.remove(item.id, if (item.type == ContentType.SERIES) "SERIES" else "MOVIE")
            refreshContinueWatching()
        }
    }

    // ---------------------------------------------------------------------
    // Búsqueda automática de carátulas faltantes (TMDB)
    // ---------------------------------------------------------------------

    /** Para películas/series/anime/especial que no traen carátula del servidor, busca una en TMDB */
    private fun fillMissingPosters(
        items: List<ContentItem>,
        isSeries: Boolean,
        onUpdated: (List<ContentItem>) -> Unit
    ) {
        if (!com.jetgo.tv.data.remote.TmdbConfig.isConfigured) return
        val missing = items.filter { it.imageUrl.isNullOrBlank() && it.name.isNotBlank() }
        if (missing.isEmpty()) return

        viewModelScope.launch {
            val current = items.toMutableList()

            // ---- Paso 1: aplicar de una lo que YA está guardado (sin pausas, al instante) ----
            val cache = try { posterCacheStore.getAll() } catch (e: Exception) { emptyMap() }
            val stillMissing = mutableListOf<ContentItem>()
            var appliedFromCache = 0
            for (item in missing) {
                val cachedUrl = cache[item.name.trim().lowercase()]
                if (cachedUrl != null) {
                    if (cachedUrl.isNotBlank()) {
                        val idx = current.indexOfFirst { it.id == item.id && it.type == item.type }
                        if (idx != -1) {
                            current[idx] = current[idx].copy(imageUrl = cachedUrl)
                            appliedFromCache++
                        }
                    }
                    // si cachedUrl está vacío, ya se buscó antes y no había carátula: no reintentar
                } else {
                    stillMissing.add(item)
                }
            }
            if (appliedFromCache > 0) {
                onUpdated(current.toList())
            }

            // ---- Paso 2: buscar en TMDB solo los títulos genuinamente nuevos ----
            var changedSinceLastUpdate = 0
            for (item in stillMissing) {
                val posterUrl = try { fetchPosterFromTmdb(item.name, isSeries) } catch (e: Exception) { null }
                if (!posterUrl.isNullOrBlank()) {
                    val idx = current.indexOfFirst { it.id == item.id && it.type == item.type }
                    if (idx != -1) {
                        current[idx] = current[idx].copy(imageUrl = posterUrl)
                        changedSinceLastUpdate++
                    }
                }
                if (changedSinceLastUpdate >= 6) {
                    onUpdated(current.toList())
                    changedSinceLastUpdate = 0
                }
                kotlinx.coroutines.delay(150)
            }
            if (changedSinceLastUpdate > 0) {
                onUpdated(current.toList())
            }
        }
    }

    private suspend fun fetchPosterFromTmdb(name: String, isSeries: Boolean): String? {
        val cached = try { posterCacheStore.get(name) } catch (e: Exception) { null }
        if (cached != null) return cached.ifBlank { null }
        if (!com.jetgo.tv.data.remote.TmdbConfig.isConfigured) return null

        // Quita cosas como "(2016)" del título para que la búsqueda encuentre mejor coincidencia
        val cleanQuery = name.replace(Regex("\\(\\d{4}\\)"), "").trim()
        if (cleanQuery.isBlank()) return null

        val response = if (isSeries) {
            tmdbApi.searchTv(com.jetgo.tv.data.remote.TmdbConfig.API_KEY, cleanQuery)
        } else {
            tmdbApi.searchMovie(com.jetgo.tv.data.remote.TmdbConfig.API_KEY, cleanQuery)
        }
        val posterPath = response.body()?.results?.firstOrNull { !it.poster_path.isNullOrBlank() }?.poster_path
        val posterUrl = posterPath?.let { "${com.jetgo.tv.data.remote.TmdbApi.IMAGE_BASE_URL}$it" }
        posterCacheStore.save(name, posterUrl)
        return posterUrl
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
