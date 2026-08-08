package com.jetgo.tv.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jetgo.tv.BuildConfig
import com.jetgo.tv.data.local.AccessStore
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
import com.jetgo.tv.util.AccessCodeChecker
import com.jetgo.tv.util.AccessCodeResult
import com.jetgo.tv.util.AdultContentFilter
import com.jetgo.tv.util.getDeviceDisplayName
import com.jetgo.tv.util.getDeviceId
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

    /** Cierra la sesión por completo: pide el código de acceso de nuevo la próxima vez */
    fun logout() {
        viewModelScope.launch {
            playerManager.exoPlayer.stop()
            configStore.clear()
            accessStore.clear()
            currentConfig = null
            _uiState.value = HomeUiState()
            _settingsInfo.value = SettingsInfo()
            _accessState.value = AccessUiState(isChecking = false, isGranted = false)
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

    private fun checkForAutoResume() {
        viewModelScope.launch {
            val last = try { lastPlayingStore.get() } catch (e: Exception) { null } ?: return@launch
            val minutesAgo = (System.currentTimeMillis() - last.savedAtMs) / 60_000
            // Solo tiene sentido retomar solo si fue hace POCO (el sistema recién mató la app
            // a mitad de la película) — si pasó más de media hora, mejor que el usuario elija
            // desde "Seguir viendo" como siempre, no imponerle algo de hace rato.
            if (minutesAgo <= 30) {
                _pendingAutoResume.value = ContentItem(
                    id = last.itemId,
                    name = last.name,
                    imageUrl = last.imageUrl,
                    type = if (last.itemType == "SERIES") ContentType.SERIES else ContentType.MOVIE,
                    streamUrl = null
                )
            } else {
                lastPlayingStore.clear()
            }
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
        try { playerManager.exoPlayer.pause() } catch (e: Exception) { /* ignorar */ }
    }

    /** Al volver a Inicio: si estaba mostrando otra cosa (película/serie), retoma el último canal EN VIVO */
    fun resumeLastLiveChannelIfNeeded() {
        val last = lastLiveChannel ?: return
        if (isCurrentlyShowingLive) {
            // Ya está en el canal correcto, solo asegurarse de que esté reproduciendo
            try { playerManager.exoPlayer.play() } catch (e: Exception) { /* ignorar */ }
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

        // Cada cierto tiempo, mientras la app esté abierta y conectada, avisa "estoy en línea
        // ahora mismo" — así el panel admin puede mostrar qué dispositivos están usando la
        // app en tiempo real. Si falla (sin internet, etc.) no afecta nada más.
        viewModelScope.launch {
            val context = getApplication<Application>()
            while (true) {
                delay(45_000)
                val code = try { accessStore.savedCode.first() } catch (e: Exception) { null }
                if (!code.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val projectId = context.getString(com.jetgo.tv.R.string.firebase_project_id)
                            val deviceId = getDeviceId(context)
                            AccessCodeChecker.sendHeartbeat(projectId, code, deviceId)
                        } catch (e: Exception) { /* silencioso */ }
                    }
                }
            }
        }
        checkForUpdate()
    }

    /** Al abrir la app: si ya había un código guardado, lo re-valida contra Firestore
     *  (así una revocación hecha desde el panel de administración sí toma efecto), y
     *  refresca la conexión por si el administrador cambió el servidor de ese código. */
    private fun checkStoredAccess() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val projectId = context.getString(com.jetgo.tv.R.string.firebase_project_id)
            val deviceId = getDeviceId(context)
            val savedCode = accessStore.savedCode.first()

            if (savedCode.isNullOrBlank()) {
                _accessState.value = AccessUiState(isChecking = false, isGranted = false)
                return@launch
            }

            // Camino rápido: si ya había credenciales del servidor guardadas de antes, se
            // conecta de una sin esperar a Firestore — así una reconexión (por ejemplo si el
            // sistema recreó la app) es casi instantánea, sin mostrar el logo de carga.
            val savedMode = configStore.mode.first()
            val savedConfig = configStore.config.first()
            val savedM3u = configStore.m3uUrl.first()
            if (savedMode == "xtream" && savedConfig != null) {
                _accessState.value = AccessUiState(isChecking = false, isGranted = true)
                connectXtream(savedConfig, silent = true)
            } else if (savedMode == "m3u" && !savedM3u.isNullOrBlank()) {
                _accessState.value = AccessUiState(isChecking = false, isGranted = true)
                connectM3u(savedM3u, silent = true)
            }

            // Validación real contra Firestore, en segundo plano y en silencio: si el código
            // ya no es válido (revocado, etc.), ahí sí se cierra la sesión.
            val result = withContext(Dispatchers.IO) {
                AccessCodeChecker.checkCodeAndRegisterDevice(projectId, savedCode, deviceId, getDeviceDisplayName(context))
            }

            if (result.valid) {
                applyAccessCodeResult(result, savedCode, silent = true)
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
            val context = getApplication<Application>()
            val projectId = context.getString(com.jetgo.tv.R.string.firebase_project_id)
            val deviceId = getDeviceId(context)
            val result = withContext(Dispatchers.IO) {
                AccessCodeChecker.checkCodeAndRegisterDevice(projectId, code, deviceId, getDeviceDisplayName(context))
            }
            if (result.valid) {
                accessStore.saveCode(code.trim().uppercase())
                applyAccessCodeResult(result, code)
                _accessState.value = AccessUiState(isChecking = false, isGranted = true)
            } else {
                val message = if (result.deviceLimitReached) {
                    "Este código ya alcanzó el máximo de 3 dispositivos"
                } else {
                    "Código inválido o inactivo"
                }
                _accessState.value = AccessUiState(
                    isChecking = false,
                    isGranted = false,
                    errorMessage = message
                )
            }
        }
    }

    /** Conecta automáticamente al servidor cargado por el administrador para ese código,
     *  sin que el cliente tenga que ver ni escribir host/usuario/contraseña. */
    private fun applyAccessCodeResult(result: AccessCodeResult, code: String, silent: Boolean = false) {
        _settingsInfo.value = SettingsInfo(
            clientName = result.clientName,
            accessCode = code.trim().uppercase(),
            deviceCount = result.deviceCount,
            maxDevices = result.maxDevices
        )
        if (result.mode == "m3u" && !result.m3uUrl.isNullOrBlank()) {
            connectM3u(result.m3uUrl, silent = silent)
        } else if (!result.host.isNullOrBlank() && !result.username.isNullOrBlank() && !result.password.isNullOrBlank()) {
            connectXtream(ServerConfig(result.host, result.username, result.password), silent = silent)
        }
        // Si el código es válido pero el administrador no cargó credenciales todavía,
        // simplemente no se auto-conecta nada (uiState.isConfigured queda en false).
    }

    /** Vuelve a intentar la conexión con el código ya guardado (sin pedirle nada al cliente) */
    fun retryConnection() {
        checkStoredAccess()
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

    data class LiveChannelInfo(
        val channelName: String,
        val channelLogo: String?,
        val channelNumber: Int?,
        val current: com.jetgo.tv.data.model.EpgProgram?,
        val next: com.jetgo.tv.data.model.EpgProgram?,
        val channelId: String? = null
    )

    private val _liveChannelInfo = MutableStateFlow<LiveChannelInfo?>(null)
    val liveChannelInfo: StateFlow<LiveChannelInfo?> = _liveChannelInfo.asStateFlow()

    fun playChannel(channel: Channel) {
        playerManager.playChannel(channel.streamUrl, channel.name)
        lastLiveChannel = LastLiveChannel(channel.streamId, channel.name, channel.streamUrl)
        isCurrentlyShowingLive = true
        viewModelScope.launch { configStore.saveLastChannelId(channel.streamId) }
        _liveChannelInfo.value = LiveChannelInfo(channel.name, channel.logoUrl, channel.number, null, null, channel.streamId)
        val config = currentConfig ?: return
        viewModelScope.launch {
            val epg = try { repository.getShortEpg(config, channel.streamId) } catch (e: Exception) { emptyList() }
            _liveChannelInfo.value = LiveChannelInfo(
                channelName = channel.name,
                channelLogo = channel.logoUrl,
                channelNumber = channel.number,
                current = epg.getOrNull(0),
                next = epg.getOrNull(1),
                channelId = channel.streamId
            )
        }
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
                val filtered = if (_parentalState.value.enabled) {
                    categories.filterNot { AdultContentFilter.isAdult(it.name) }
                } else categories
                _categoryPickerState.value = CategoryPickerUiState(categories = filtered)

                // Para Vivo: siempre se queda mostrando la categoría que quedó por defecto
                // (la última elegida), y no la primera de la lista cada vez.
                if (type == ContentType.LIVE && filtered.isNotEmpty()) {
                    val target = _phoneLiveCategoryId.value?.takeIf { id -> filtered.any { it.id == id } }
                        ?: filtered.first().id
                    loadCategoryContent(ContentType.LIVE, target)
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
                        ContentItem(it.streamId, it.name, it.coverUrl, type, it.streamUrl, rating = it.rating)
                    }
                    ContentType.SERIES -> repository.getSeries(config, categoryId).map {
                        ContentItem(it.seriesId, it.name, it.coverUrl, ContentType.SERIES, null, rating = it.rating)
                    }
                }
                val filteredItems = if (type != ContentType.LIVE && _parentalState.value.enabled) {
                    items.filterNot { AdultContentFilter.isAdult(it.name) }
                } else items
                _categoryContentState.value = CategoryContentUiState(items = filteredItems)

                if (type != ContentType.LIVE) {
                    fillMissingPosters(filteredItems, isSeries = type == ContentType.SERIES) { updated ->
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
            val cleanMovies = if (parentalOn) movies.filterNot { AdultContentFilter.isAdult(it.name) } else movies
            val cleanSeries = if (parentalOn) series.filterNot { AdultContentFilter.isAdult(it.name) } else series
            val cleanAnime = if (parentalOn) anime.filterNot { AdultContentFilter.isAdult(it.name) } else anime

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

    /** Carga toda la ficha de la serie (sinopsis + todas las temporadas/capítulos) */
    fun loadSeriesDetail(item: ContentItem) {
        _seriesDetailState.value = SeriesDetailUiState(isLoading = true)
        viewModelScope.launch {
            watchHistoryStore.record(
                WatchHistoryEntry(item.id, item.name, item.imageUrl, "SERIES", System.currentTimeMillis())
            )
            refreshContinueWatching()
        }
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
        playerManager.playChannel(episode.streamUrl, "${_seriesDetailState.value.detail?.name} · ${episode.title}")
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
        try { playerManager.exoPlayer.pause() } catch (e: Exception) { /* ignorar */ }
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
        val config = currentConfig ?: run {
            _movieDetailState.value = MovieDetailUiState(errorMessage = "Sin configuración de servidor")
            return
        }
        val fallbackStreamUrl = item.streamUrl ?: ""
        viewModelScope.launch {
            var debugDetail: String? = null
            val detail = try {
                repository.getMovieDetail(config, item.id, item.name, item.imageUrl, fallbackStreamUrl)
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
        try { playerManager.exoPlayer.pause() } catch (e: Exception) { /* ignorar */ }
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
                playerManager.playChannel(streamUrl, title)
                startPositionTracking(contentKey, onCompleted)
            }
        }
    }

    fun resumeFromPrompt() {
        val prompt = _resumePrompt.value ?: return
        playerManager.playChannel(prompt.streamUrl, prompt.title)
        startPositionTracking(prompt.contentKey, prompt.onCompleted)
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // deja que el reproductor prepare el contenido antes de saltar
            playerManager.seekTo(prompt.resumePositionMs)
        }
        _resumePrompt.value = null
    }

    fun startOverFromPrompt() {
        val prompt = _resumePrompt.value ?: return
        playerManager.playChannel(prompt.streamUrl, prompt.title)
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
