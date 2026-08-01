package com.jetgo.tv

import android.os.Bundle
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
import com.jetgo.tv.ui.components.UpdateBanner
import com.jetgo.tv.ui.screens.ChannelListScreen
import com.jetgo.tv.ui.screens.ConnectionIssueScreen
import com.jetgo.tv.ui.screens.FavoritesScreen
import com.jetgo.tv.ui.screens.HomeScreen
import com.jetgo.tv.ui.screens.HomeViewModel
import com.jetgo.tv.ui.screens.SearchScreen
import com.jetgo.tv.ui.screens.TvCategoryGridScreen
import com.jetgo.tv.ui.screens.TvSeriesDetailScreen
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
        setContent {
            JetGoTheme {
                AppRoot(viewModel)
            }
        }
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

    // Mantiene la orientación/inmersión sincronizadas con el estado de pantalla completa
    FullscreenPlayerEffect(
        isFullscreen = isFullscreen,
        onBackFromFullscreen = { viewModel.exitFullscreenPlayer() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (updateInfo != null && !isFullscreen) {
                UpdateBanner(updateInfo = updateInfo!!)
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    accessState.isChecking -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
            FullscreenPlayerOverlay(
                playerManager = viewModel.playerManager,
                onExitFullscreen = { viewModel.exitFullscreenPlayer() }
            )
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
    val homeCatalog by viewModel.homeCatalog.collectAsState()

    // Manejador único: si es una serie, abre su ficha; si no, reproduce directo y vuelve a Inicio.
    val handleItemSelected: (ContentItem) -> Unit = { item ->
        if (item.type == ContentType.SERIES) {
            viewModel.loadSeriesDetail(item)
            navController.navigate("seriesDetail")
        } else {
            viewModel.selectContentItem(item) {
                navController.popBackStack("home", inclusive = false)
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                playerManager = viewModel.playerManager,
                liveChannels = uiState.liveChannels,
                onChannelSelected = { viewModel.playChannel(it) },
                onCategoryClick = { category -> navController.navigate("categoryPicker/$category") },
                onSearchClick = { navController.navigate("search") },
                onFavoritesClick = { navController.navigate("favorites") }
            )
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
            val recommendations = (homeCatalog.series + homeCatalog.movies)
                .filter { it.id != seriesDetailState.detail?.seriesId }
                .shuffled()
                .take(10)

            TvSeriesDetailScreen(
                state = seriesDetailState,
                playerManager = viewModel.playerManager,
                isFavorite = seriesDetailState.detail?.let {
                    viewModel.isFavorite(ContentItem(it.seriesId, it.name, it.coverUrl, ContentType.SERIES, null))
                } ?: false,
                recommendations = recommendations,
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
                }
            )
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
    }
}

/** Shell para teléfonos: barra inferior Inicio / TV / Perfil */
@Composable
private fun PhoneApp(viewModel: HomeViewModel) {
    var selectedTab by remember { mutableStateOf(PhoneMainTab.TV) }
    var showSearch by remember { mutableStateOf(false) }
    var seriesDetailItem by remember { mutableStateOf<ContentItem?>(null) }

    val categoryPickerState by viewModel.categoryPickerState.collectAsState()
    val categoryContentState by viewModel.categoryContentState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val homeCatalog by viewModel.homeCatalog.collectAsState()
    val seriesDetailState by viewModel.seriesDetailState.collectAsState()

    // Cualquier ítem: si es serie, abre la ficha con temporadas/capítulos; si no, reproduce directo
    val handleItemClick: (ContentItem) -> Unit = { item ->
        if (item.type == ContentType.SERIES) {
            seriesDetailItem = item
            viewModel.loadSeriesDetail(item)
        } else {
            viewModel.selectContentItem(item) { viewModel.enterFullscreenPlayer() }
        }
    }

    BackHandler(enabled = showSearch) { showSearch = false }
    BackHandler(enabled = seriesDetailItem != null) {
        viewModel.clearSeriesDetail()
        seriesDetailItem = null
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
                PhoneMainTab.INICIO -> {
                    PhoneInicioScreen(
                        catalog = homeCatalog,
                        onEnterScreen = { viewModel.ensureHomeCatalogLoaded() },
                        onItemClick = handleItemClick,
                        onSearchClick = { showSearch = true }
                    )
                }
                PhoneMainTab.TV -> {
                    PhoneTvScreen(
                        playerManager = viewModel.playerManager,
                        categories = categoryPickerState.categories,
                        categoriesLoading = categoryPickerState.isLoading,
                        channelsInCategory = categoryContentState.items,
                        channelsLoading = categoryContentState.isLoading,
                        favorites = favorites,
                        onLoadCategories = { viewModel.loadCategoriesForType(ContentType.LIVE) },
                        onLoadChannelsForCategory = { categoryId ->
                            viewModel.loadCategoryContent(ContentType.LIVE, categoryId)
                        },
                        onChannelTap = { item -> viewModel.selectContentItem(item) {} },
                        onFavoriteTap = { item -> handleItemClick(item) },
                        onSearchClick = { showSearch = true },
                        onEnterFullscreen = { viewModel.enterFullscreenPlayer() }
                    )
                }
                PhoneMainTab.PERFIL -> {
                    PhoneProfileScreen(onDisconnect = { viewModel.disconnect() })
                }
            }
        }

        PhoneBottomNav(selected = selectedTab, onSelect = { selectedTab = it })
    }
}
