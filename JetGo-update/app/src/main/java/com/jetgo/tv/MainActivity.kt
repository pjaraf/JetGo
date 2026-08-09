package com.jetgo.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jetgo.tv.data.model.ContentItem
import com.jetgo.tv.data.model.ContentType
import com.jetgo.tv.ui.screens.AccessCodeScreen
import com.jetgo.tv.ui.screens.CategoryPickerScreen
import com.jetgo.tv.ui.components.FullscreenPlayerEffect
import com.jetgo.tv.ui.components.FullscreenPlayerOverlay
import com.jetgo.tv.ui.components.PhoneBottomNav
import com.jetgo.tv.ui.components.PhoneMainTab
import com.jetgo.tv.ui.components.ResumeWatchingDialog
import com.jetgo.tv.ui.components.UpdateBanner
import com.jetgo.tv.ui.components.UpdateDialogTv
import com.jetgo.tv.ui.components.formatDurationLabel
import com.jetgo.tv.ui.screens.ChannelListScreen
import com.jetgo.tv.ui.screens.ContinueWatchingScreen
import com.jetgo.tv.ui.screens.ConnectionIssueScreen
import com.jetgo.tv.ui.screens.FavoritesScreen
import com.jetgo.tv.ui.screens.HomeScreen
import com.jetgo.tv.ui.screens.TvHomeScreen
import com.jetgo.tv.ui.screens.HomeViewModel
import com.jetgo.tv.ui.screens.SearchScreen
import com.jetgo.tv.ui.screens.TvCategoryGridScreen
import com.jetgo.tv.ui.screens.TvMovieDetailScreen
import com.jetgo.tv.ui.screens.TvSeriesDetailScreen
import com.jetgo.tv.ui.screens.SplashLoadingScreen
import com.jetgo.tv.ui.screens.TvSettingsScreen
import com.jetgo.tv.ui.screens.phone.MovieDetailScreen
import com.jetgo.tv.ui.screens.phone.PhoneCategoryScreen
import com.jetgo.tv.ui.screens.phone.PhoneInicioScreen
import com.jetgo.tv.ui.screens.phone.PhoneProfileScreen
import com.jetgo.tv.ui.screens.phone.PhoneTvScreen
import com.jetgo.tv.ui.screens.phone.SeriesDetailScreen
import com.jetgo.tv.ui.theme.JetGoTheme
import com.jetgo.tv.util.isTelevision

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.e(
            "JetGo_DIAG",
            "MainActivity.onCreate() — savedInstanceState es " +
                if (savedInstanceState == null) "NULO (arranque limpio)" else "NO nulo (esto es una RECREACIÓN)",
            Exception("rastro de diagnóstico")
        )

        // No queremos que la pantalla (TV o teléfono) se apague sola mientras se usa la app.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            JetGoTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // La app no debe seguir reproduciendo cuando queda en segundo plano (se minimiza,
        // se apaga la pantalla, o se cambia a otra app) — se pausa por completo.
        try { viewModel.playerManager.exoPlayer.pause() } catch (e: Exception) { /* ignorar */ }
    }

    override fun onStart() {
        super.onStart()
        // Al volver al primer plano, retoma automáticamente lo que se estaba viendo.
        try { viewModel.playerManager.exoPlayer.play() } catch (e: Exception) { /* ignorar */ }
    }
}

/** Convierte el nombre de ruta ("LIVE", "SERIES", ...) al enum ContentType */
private fun categoryFromRoute(route: String?): ContentType = when (route) {
    "LIVE" -> ContentType.LIVE
    "SERIES" -> ContentType.SERIES
    "MOVIE" -> ContentType.MOVIE
    "ANIME" -> ContentType.ANIME
    "SPECIAL" -> ContentType.SPECIAL
    else -> ContentType.LIVE
}

private fun labelForType(type: ContentType): String = when (type) {
    ContentType.LIVE -> "Vivo"
    ContentType.SERIES -> "Series"
    ContentType.MOVIE -> "Películas"
    ContentType.ANIME -> "Anime"
    ContentType.SPECIAL -> "Especiales"
}

@Composable
private fun AppRoot(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val accessState by viewModel.accessState.collectAsState()
    val isFullscreen by viewModel.isFullscreenPlayer.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val context = LocalContext.current
    val isTv = remember { isTelevision(context) }

    // Si la demo venció, el código fue revocado, o el panel mandó la señal de "cerrar ahora"
    // (por ejemplo al renovar un plan), la app se cierra por completo — aunque el cliente
    // esté viendo algo en ese momento.
    val forceCloseApp by viewModel.forceCloseApp.collectAsState()
    LaunchedEffect(forceCloseApp) {
        if (forceCloseApp) {
            val activity = context as? android.app.Activity
            activity?.finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    // Mantiene la orientación/inmersión sincronizadas con el estado de pantalla completa
    FullscreenPlayerEffect(
        isFullscreen = isFullscreen,
        onBackFromFullscreen = { viewModel.exitFullscreenPlayer() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (updateInfo != null && !isFullscreen && !isTv) {
                UpdateBanner(
                    updateInfo = updateInfo!!,
                    onUpdateStarted = { viewModel.dismissUpdateBanner() }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    accessState.isChecking -> {
                        SplashLoadingScreen()
                    }
                    !accessState.isGranted -> {
                        // ---- Filtro de código de acceso: se muestra ANTES que todo lo demás ----
                        AccessCodeScreen(
                            isChecking = false,
                            errorMessage = accessState.errorMessage,
                            onSubmitCode = { viewModel.submitAccessCode(it) }
                        )
                    }
                    uiState.isLoading -> {
                        SplashLoadingScreen()
                    }
                    !uiState.isConfigured -> {
                        // El código es válido, pero no se pudo conectar (credenciales del panel
                        // mal cargadas, servidor caído, etc.). NUNCA se le muestra al cliente la
                        // pantalla de configuración manual — solo un aviso + reintentar.
                        ConnectionIssueScreen(
                            onRetry = { viewModel.retryConnection() },
                            debugDetail = uiState.debugDetail
                        )
                    }
                    isTv -> {
                        // ---- Interfaz original para Android TV / Google TV / TV Box (sin cambios) ----
                        val navController = rememberNavController()
                        DashboardNavHost(navController, viewModel)
                    }
                    else -> {
                        // ---- Interfaz para teléfonos: barra inferior Inicio / TV / Perfil ----
                        PhoneApp(viewModel)
                    }
                }
            }
        }

        // El reproductor a pantalla completa se dibuja por ENCIMA de todo lo demás
        if (isFullscreen) {
            val movieDetailState by viewModel.movieDetailState.collectAsState()
            val seriesDetailState by viewModel.seriesDetailState.collectAsState()
            val liveChannelInfo by viewModel.liveChannelInfo.collectAsState()
            val categoryPickerState by viewModel.categoryPickerState.collectAsState()
            val categoryContentState by viewModel.categoryContentState.collectAsState()

            val currentEpisode = seriesDetailState.detail?.episodesBySeason?.values
                ?.flatten()
                ?.firstOrNull { it.id == seriesDetailState.currentEpisodeId }

            val isVod = movieDetailState.detail != null || seriesDetailState.detail != null
            val posterUrl = movieDetailState.detail?.coverUrl ?: seriesDetailState.detail?.coverUrl
            val title = movieDetailState.detail?.name
                ?: seriesDetailState.detail?.let { detail ->
                    if (currentEpisode != null) "${detail.name} · ${currentEpisode.title}" else detail.name
                }
                ?: ""

            FullscreenPlayerOverlay(
                playerManager = viewModel.playerManager,
                onExitFullscreen = { viewModel.exitFullscreenPlayer() },
                isVod = isVod,
                posterUrl = posterUrl,
                title = title,
                liveChannelInfo = if (!isVod) liveChannelInfo else null,
                allLiveChannels = uiState.liveChannels,
                onChangeChannel = { channel -> viewModel.playChannel(channel) },
                liveCategories = categoryPickerState.categories,
                liveChannelsInCategory = categoryContentState.items,
                onLoadLiveCategories = { viewModel.loadCategoriesForType(ContentType.LIVE) },
                onSelectLiveCategory = { categoryId -> viewModel.loadCategoryContent(ContentType.LIVE, categoryId) },
                onSelectLiveChannel = { item -> viewModel.selectContentItem(item) {} },
                showNextEpisodeMessage = viewModel.showNextEpisodeMessage.collectAsState().value
            )
        }

        // "Seguir viendo" / "Desde el inicio" — por encima de cualquier pantalla
        val resumePrompt by viewModel.resumePrompt.collectAsState()
        resumePrompt?.let { prompt ->
            ResumeWatchingDialog(
                title = prompt.title,
                resumeLabel = "Continuar en " + formatDurationLabel(prompt.resumePositionMs),
                onResume = { viewModel.resumeFromPrompt() },
                onStartOver = { viewModel.startOverFromPrompt() },
                onDismiss = { viewModel.dismissResumePrompt() }
            )
        }

        // En TV, la actualización se muestra como ventana centrada (no como barra arriba)
        if (isTv && updateInfo != null && !isFullscreen) {
            var updateDismissed by remember { mutableStateOf(false) }
            if (!updateDismissed) {
                UpdateDialogTv(
                    updateInfo = updateInfo!!,
                    onDismiss = { updateDismissed = true },
                    onUpdateStarted = { viewModel.dismissUpdateBanner() }
                )
            }
        }
    }
}

/**
 * Contenido de la pestaña "Inicio" en teléfonos, e interfaz completa en TV:
 * el dashboard con las 5 categorías + reproductor, y sus pantallas de detalle.
 */
@Composable
private fun DashboardNavHost(navController: NavHostController, viewModel: HomeViewModel) {
    val categoryPickerState by viewModel.categoryPickerState.collectAsState()
    val categoryContentState by viewModel.categoryContentState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val seriesDetailState by viewModel.seriesDetailState.collectAsState()
    val movieDetailState by viewModel.movieDetailState.collectAsState()
    val homeCatalog by viewModel.homeCatalog.collectAsState()
    val isFullscreen by viewModel.isFullscreenPlayer.collectAsState()
    val showNextEpisodeMessage by viewModel.showNextEpisodeMessage.collectAsState()

    // Manejador único: si es una serie o película, abre su ficha; si no (Vivo), reproduce directo.
    val handleItemSelected: (ContentItem) -> Unit = { item ->
        when (item.type) {
            ContentType.SERIES -> {
                viewModel.loadSeriesDetail(item)
                navController.navigate("seriesDetail")
            }
            ContentType.MOVIE, ContentType.ANIME, ContentType.SPECIAL -> {
                viewModel.loadMovieDetail(item)
                navController.navigate("movieDetail")
            }
            else -> {
                viewModel.selectContentItem(item) {
                    navController.popBackStack("home", inclusive = false)
                }
            }
        }
    }

    // Si el sistema mató la app mientras el usuario veía una película/serie (común en TV Box
    // con poca memoria), al reconectar se detecta y se retoma solo, sin que el usuario tenga
    // que volver a buscarla desde cero.
    val pendingAutoResume by viewModel.pendingAutoResume.collectAsState()
    LaunchedEffect(pendingAutoResume) {
        pendingAutoResume?.let { item ->
            handleItemSelected(item)
            viewModel.enterFullscreenPlayer()
            viewModel.consumeAutoResume()
        }
    }

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            LaunchedEffect(Unit) {
                viewModel.resumeLastLiveChannelIfNeeded()
            }
            if (!isFullscreen) {
                val hiddenTypes by viewModel.hiddenTypes.collectAsState()
                val newestItems = (homeCatalog.movies + homeCatalog.series).shuffled().take(10)
                HomeScreen(
                    playerManager = viewModel.playerManager,
                    liveChannels = uiState.liveChannels,
                    newestItems = newestItems,
                    onItemClick = { item -> viewModel.pausePreviewPlayer(); handleItemSelected(item) },
                    onChannelSelected = { viewModel.playChannel(it) },
                    onCategoryClick = { category ->
                        viewModel.pausePreviewPlayer()
                        navController.navigate("categoryPicker/$category")
                    },
                    onLiveClick = { viewModel.enterFullscreenPlayer() },
                    onSearchClick = {
                        viewModel.pausePreviewPlayer()
                        navController.navigate("search")
                    },
                    onFavoritesClick = {
                        viewModel.pausePreviewPlayer()
                        navController.navigate("favorites")
                    },
                    onContinueWatchingClick = {
                        viewModel.pausePreviewPlayer()
                        navController.navigate("continueWatching")
                    },
                    onSettingsClick = {
                        viewModel.pausePreviewPlayer()
                        navController.navigate("settings")
                    },
                    isFullscreen = isFullscreen,
                    hiddenTypes = hiddenTypes
                )
            }
            // Mientras isFullscreen es true, esta pantalla NO se dibuja (ni oculta): así el
            // control remoto no puede "tocar sin querer" ninguno de estos botones por detrás.
        }

        composable("categoryPicker/{category}") { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category")
            val type = categoryFromRoute(category)

            if (type == ContentType.LIVE) {
                // Vivo sigue con el flujo clásico (lista de canales, no pósters)
                LaunchedEffect(category) { viewModel.loadCategoriesForType(type) }

                CategoryPickerScreen(
                    isLoading = categoryPickerState.isLoading,
                    categories = categoryPickerState.categories,
                    errorMessage = categoryPickerState.errorMessage,
                    onCategorySelected = { selectedCategory ->
                        val encodedId = java.net.URLEncoder.encode(selectedCategory.id, "UTF-8")
                        navController.navigate("categoryContent/$category/$encodedId")
                    }
                )
            } else {
                // Película/Serie/Anime/Especial: categorías + grilla de pósters en una sola pantalla
                TvCategoryGridScreen(
                    typeLabel = labelForType(type),
                    categories = categoryPickerState.categories,
                    categoriesLoading = categoryPickerState.isLoading,
                    items = categoryContentState.items,
                    itemsLoading = categoryContentState.isLoading,
                    onLoadCategories = { viewModel.loadCategoriesForType(type) },
                    onCategorySelected = { categoryId -> viewModel.loadCategoryContent(type, categoryId) },
                    onItemSelected = handleItemSelected,
                    isFavorite = { viewModel.isFavorite(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                )
            }
        }

        composable("categoryContent/{category}/{categoryId}") { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category")
            val categoryId = backStackEntry.arguments?.getString("categoryId")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            val type = categoryFromRoute(category)

            LaunchedEffect(category, categoryId) { viewModel.loadCategoryContent(type, categoryId) }

            ChannelListScreen(
                isLoading = categoryContentState.isLoading,
                items = categoryContentState.items,
                errorMessage = categoryContentState.errorMessage,
                isFavorite = { viewModel.isFavorite(it) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onItemSelected = handleItemSelected
            )
        }

        composable("seriesDetail") {
            LaunchedEffect(seriesDetailState.detail?.seriesId) {
                if (seriesDetailState.detail != null) {
                    viewModel.onSeriesFullyFinished = {
                        viewModel.clearSeriesDetail()
                        navController.popBackStack()
                    }
                }
            }
            if (!isFullscreen) {
                TvSeriesDetailScreen(
                    state = seriesDetailState,
                    playerManager = viewModel.playerManager,
                    isFavorite = seriesDetailState.detail?.let {
                        viewModel.isFavorite(ContentItem(it.seriesId, it.name, it.coverUrl, ContentType.SERIES, null))
                    } ?: false,
                    recommendations = emptyList(),
                    onBack = {
                        viewModel.clearSeriesDetail()
                        navController.popBackStack()
                    },
                    onSelectSeason = { viewModel.selectSeason(it) },
                    onPlayEpisode = { viewModel.playEpisode(it) },
                    onToggleFavorite = {
                        seriesDetailState.detail?.let {
                            viewModel.toggleFavorite(ContentItem(it.seriesId, it.name, it.coverUrl, ContentType.SERIES, null))
                        }
                    },
                    onEnterFullscreen = { viewModel.enterFullscreenPlayer() },
                    onRecommendationClick = { item ->
                        viewModel.clearSeriesDetail()
                        handleItemSelected(item)
                    },
                    isFullscreen = isFullscreen,
                    showNextEpisodeMessage = showNextEpisodeMessage
                )
            }
        }

        composable("movieDetail") {
            LaunchedEffect(movieDetailState.detail?.streamId) {
                if (movieDetailState.detail != null) {
                    viewModel.playerManager.onPlaybackEnded = {
                        viewModel.exitFullscreenPlayer()
                        viewModel.clearMovieDetail()
                        navController.popBackStack()
                    }
                }
            }
            if (!isFullscreen) {
                TvMovieDetailScreen(
                    state = movieDetailState,
                    playerManager = viewModel.playerManager,
                    isFavorite = movieDetailState.detail?.let {
                        viewModel.isFavorite(ContentItem(it.streamId, it.name, it.coverUrl, ContentType.MOVIE, it.streamUrl))
                    } ?: false,
                    recommendations = emptyList(),
                    onBack = {
                        viewModel.clearMovieDetail()
                        navController.popBackStack()
                    },
                    onToggleFavorite = {
                        movieDetailState.detail?.let {
                            viewModel.toggleFavorite(ContentItem(it.streamId, it.name, it.coverUrl, ContentType.MOVIE, it.streamUrl))
                        }
                    },
                    onEnterFullscreen = { viewModel.enterFullscreenPlayer() },
                    onRecommendationClick = { item ->
                        viewModel.clearMovieDetail()
                        handleItemSelected(item)
                    },
                    isFullscreen = isFullscreen
                )
            }
        }

        composable("search") {
            SearchScreen(
                isLoadingCatalog = searchState.isLoadingCatalog,
                query = searchState.query,
                results = searchState.results,
                isFavorite = { viewModel.isFavorite(it) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                onEnterScreen = { viewModel.ensureSearchCatalogLoaded() },
                onItemSelected = handleItemSelected
            )
        }

        composable("favorites") {
            FavoritesScreen(
                favorites = favorites,
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onItemSelected = handleItemSelected
            )
        }

        composable("continueWatching") {
            val continueWatching by viewModel.continueWatching.collectAsState()
            ContinueWatchingScreen(
                items = continueWatching,
                onItemClick = handleItemSelected,
                onRemoveItem = { item -> viewModel.removeFromContinueWatching(item) },
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            val settingsInfo by viewModel.settingsInfo.collectAsState()
            val parentalState by viewModel.parentalState.collectAsState()
            TvSettingsScreen(
                settingsInfo = settingsInfo,
                parentalState = parentalState,
                onEnterScreen = { viewModel.refreshParentalState() },
                onEnableParental = { pin -> viewModel.enableParentalControl(pin) },
                onDisableParental = { viewModel.disableParentalControl() },
                onCheckPin = { pin -> viewModel.checkParentalPin(pin) },
                onClearCache = { viewModel.clearCache() },
                onLogout = { viewModel.logout() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/** Shell para teléfonos: barra inferior Inicio / TV / Perfil */
@Composable
private fun PhoneApp(viewModel: HomeViewModel) {
    var selectedTab by remember { mutableStateOf(PhoneMainTab.TV) }
    var showSearch by remember { mutableStateOf(false) }
    var seriesDetailItem by remember { mutableStateOf<ContentItem?>(null) }
    var movieDetailItem by remember { mutableStateOf<ContentItem?>(null) }

    // Al salir de la pestaña TV (a Series/Películas/Perfil) se pausa el canal en vivo; al
    // volver a TV, retoma justo el canal donde había quedado.
    LaunchedEffect(selectedTab) {
        if (selectedTab == PhoneMainTab.TV) {
            viewModel.resumeLastLiveChannelIfNeeded()
        } else {
            viewModel.pausePreviewPlayer()
        }
    }

    val categoryPickerState by viewModel.categoryPickerState.collectAsState()
    val categoryContentState by viewModel.categoryContentState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val homeCatalog by viewModel.homeCatalog.collectAsState()
    val seriesDetailState by viewModel.seriesDetailState.collectAsState()
    val movieDetailState by viewModel.movieDetailState.collectAsState()

    // Serie: abre la ficha con temporadas/capítulos. Película: abre su propia ficha, mismo
    // estilo que la de serie. Cualquier otra cosa (canal en vivo, etc.): reproduce directo.
    val handleItemClick: (ContentItem) -> Unit = { item ->
        when (item.type) {
            ContentType.SERIES -> {
                seriesDetailItem = item
                viewModel.loadSeriesDetail(item)
            }
            ContentType.MOVIE -> {
                movieDetailItem = item
                viewModel.loadMovieDetail(item)
            }
            else -> {
                viewModel.selectContentItem(item) { viewModel.enterFullscreenPlayer() }
            }
        }
    }

    BackHandler(enabled = showSearch) { showSearch = false }
    BackHandler(enabled = seriesDetailItem != null) {
        viewModel.clearSeriesDetail()
        seriesDetailItem = null
    }
    BackHandler(enabled = movieDetailItem != null) {
        viewModel.clearMovieDetail()
        movieDetailItem = null
    }

    if (movieDetailItem != null) {
        val recommendations = (homeCatalog.movies + homeCatalog.series)
            .filter { it.id != movieDetailItem?.id }
            .shuffled()
            .take(10)

        MovieDetailScreen(
            state = movieDetailState,
            playerManager = viewModel.playerManager,
            recommendations = recommendations,
            onBack = { viewModel.clearMovieDetail(); movieDetailItem = null },
            onEnterFullscreen = { viewModel.enterFullscreenPlayer() },
            onRecommendationClick = { item -> handleItemClick(item) }
        )
        return
    }

    if (seriesDetailItem != null) {
        val recommendations = (homeCatalog.series + homeCatalog.movies)
            .filter { it.id != seriesDetailItem?.id }
            .shuffled()
            .take(10)

        SeriesDetailScreen(
            state = seriesDetailState,
            playerManager = viewModel.playerManager,
            recommendations = recommendations,
            onBack = { viewModel.clearSeriesDetail(); seriesDetailItem = null },
            onSelectSeason = { viewModel.selectSeason(it) },
            onPlayEpisode = { viewModel.playEpisode(it) },
            onEnterFullscreen = { viewModel.enterFullscreenPlayer() },
            onRecommendationClick = { item -> handleItemClick(item) }
        )
        return
    }

    if (showSearch) {
        SearchScreen(
            isLoadingCatalog = searchState.isLoadingCatalog,
            query = searchState.query,
            results = searchState.results,
            isFavorite = { viewModel.isFavorite(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onQueryChanged = { viewModel.onSearchQueryChanged(it) },
            onEnterScreen = { viewModel.ensureSearchCatalogLoaded() },
            onItemSelected = { item ->
                showSearch = false
                handleItemClick(item)
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                PhoneMainTab.TV -> {
                    val liveChannelInfo by viewModel.liveChannelInfo.collectAsState()
                    val persistedCategoryId by viewModel.phoneLiveCategoryId.collectAsState()
                    PhoneTvScreen(
                        playerManager = viewModel.playerManager,
                        categories = categoryPickerState.categories,
                        categoriesLoading = categoryPickerState.isLoading,
                        channelsInCategory = categoryContentState.items,
                        channelsLoading = categoryContentState.isLoading,
                        favorites = favorites,
                        activeChannelId = liveChannelInfo?.channelId,
                        persistedCategoryId = persistedCategoryId,
                        onLoadCategories = { viewModel.loadCategoriesForType(ContentType.LIVE) },
                        onLoadChannelsForCategory = { categoryId ->
                            viewModel.setPhoneLiveCategoryId(categoryId)
                            viewModel.loadCategoryContent(ContentType.LIVE, categoryId)
                        },
                        onChannelTap = { item -> viewModel.selectContentItem(item) {} },
                        onFavoriteTap = { item -> handleItemClick(item) },
                        onSearchClick = { showSearch = true },
                        onEnterFullscreen = { viewModel.enterFullscreenPlayer() }
                    )
                }
                PhoneMainTab.SERIES -> {
                    PhoneCategoryScreen(
                        typeLabel = "Series",
                        categories = categoryPickerState.categories,
                        categoriesLoading = categoryPickerState.isLoading,
                        items = categoryContentState.items,
                        itemsLoading = categoryContentState.isLoading,
                        onLoadCategories = { viewModel.loadCategoriesForType(ContentType.SERIES) },
                        onCategorySelected = { categoryId ->
                            viewModel.loadCategoryContent(ContentType.SERIES, categoryId)
                        },
                        onItemSelected = handleItemClick
                    )
                }
                PhoneMainTab.PELICULAS -> {
                    PhoneCategoryScreen(
                        typeLabel = "Películas",
                        categories = categoryPickerState.categories,
                        categoriesLoading = categoryPickerState.isLoading,
                        items = categoryContentState.items,
                        itemsLoading = categoryContentState.isLoading,
                        onLoadCategories = { viewModel.loadCategoriesForType(ContentType.MOVIE) },
                        onCategorySelected = { categoryId ->
                            viewModel.loadCategoryContent(ContentType.MOVIE, categoryId)
                        },
                        onItemSelected = handleItemClick
                    )
                }
                PhoneMainTab.PERFIL -> {
                    val settingsInfo by viewModel.settingsInfo.collectAsState()
                    val parentalState by viewModel.parentalState.collectAsState()
                    TvSettingsScreen(
                        settingsInfo = settingsInfo,
                        parentalState = parentalState,
                        onEnterScreen = { viewModel.refreshParentalState() },
                        onEnableParental = { pin -> viewModel.enableParentalControl(pin) },
                        onDisableParental = { viewModel.disableParentalControl() },
                        onCheckPin = { pin -> viewModel.checkParentalPin(pin) },
                        onClearCache = { viewModel.clearCache() },
                        onLogout = { viewModel.logout() },
                        onBack = {}
                    )
                }
            }
        }

        val hiddenTypes by viewModel.hiddenTypes.collectAsState()
        PhoneBottomNav(selected = selectedTab, onSelect = { selectedTab = it }, hiddenTypes = hiddenTypes)
    }
}
