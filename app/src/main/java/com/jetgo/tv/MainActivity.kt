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
import com.jetgo.tv.data.model.ContentType
import com.jetgo.tv.ui.screens.CategoryPickerScreen
import com.jetgo.tv.ui.components.FullscreenPlayerEffect
import com.jetgo.tv.ui.components.FullscreenPlayerOverlay
import com.jetgo.tv.ui.components.PhoneBottomNav
import com.jetgo.tv.ui.components.PhoneMainTab
import com.jetgo.tv.ui.screens.ChannelListScreen
import com.jetgo.tv.ui.screens.FavoritesScreen
import com.jetgo.tv.ui.screens.HomeScreen
import com.jetgo.tv.ui.screens.HomeViewModel
import com.jetgo.tv.ui.screens.SearchScreen
import com.jetgo.tv.ui.screens.SetupScreen
import com.jetgo.tv.ui.screens.phone.PhoneInicioScreen
import com.jetgo.tv.ui.screens.phone.PhoneProfileScreen
import com.jetgo.tv.ui.screens.phone.PhoneTvScreen
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

@Composable
private fun AppRoot(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isFullscreen by viewModel.isFullscreenPlayer.collectAsState()
    val context = LocalContext.current
    val isTv = remember { isTelevision(context) }

    // Mantiene la orientación/inmersión sincronizadas con el estado de pantalla completa
    FullscreenPlayerEffect(
        isFullscreen = isFullscreen,
        onBackFromFullscreen = { viewModel.exitFullscreenPlayer() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && !uiState.isConfigured -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            !uiState.isConfigured -> {
                SetupScreen(
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onConnectXtream = { viewModel.connectXtream(it) },
                    onConnectM3u = { viewModel.connectM3u(it) }
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
                onItemSelected = { item ->
                    viewModel.selectContentItem(item) {
                        navController.popBackStack("home", inclusive = false)
                    }
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
                onItemSelected = { item ->
                    viewModel.selectContentItem(item) {
                        navController.popBackStack("home", inclusive = false)
                    }
                }
            )
        }

        composable("favorites") {
            FavoritesScreen(
                favorites = favorites,
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onItemSelected = { item ->
                    viewModel.playFavorite(item) {
                        navController.popBackStack("home", inclusive = false)
                    }
                }
            )
        }
    }
}

/** Shell para teléfonos: barra inferior Inicio / TV / Perfil */
@Composable
private fun PhoneApp(viewModel: HomeViewModel) {
    var selectedTab by remember { mutableStateOf(PhoneMainTab.TV) }
    var showSearch by remember { mutableStateOf(false) }

    val categoryPickerState by viewModel.categoryPickerState.collectAsState()
    val categoryContentState by viewModel.categoryContentState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val homeCatalog by viewModel.homeCatalog.collectAsState()

    BackHandler(enabled = showSearch) { showSearch = false }

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
                viewModel.selectContentItem(item) { showSearch = false }
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
                        onItemClick = { item ->
                            viewModel.selectContentItem(item) { viewModel.enterFullscreenPlayer() }
                        },
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
                        onFavoriteTap = { item -> viewModel.playFavorite(item) {} },
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
