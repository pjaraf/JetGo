package com.jetgo.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jetgo.tv.data.model.ContentType
import com.jetgo.tv.ui.screens.CategoryPickerScreen
import com.jetgo.tv.ui.screens.ChannelListScreen
import com.jetgo.tv.ui.screens.FavoritesScreen
import com.jetgo.tv.ui.screens.HomeScreen
import com.jetgo.tv.ui.screens.HomeViewModel
import com.jetgo.tv.ui.screens.SearchScreen
import com.jetgo.tv.ui.screens.SetupScreen
import com.jetgo.tv.ui.theme.JetGoTheme
import java.net.URLDecoder
import java.net.URLEncoder

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
    val categoryPickerState by viewModel.categoryPickerState.collectAsState()
    val categoryContentState by viewModel.categoryContentState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val navController = rememberNavController()

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
            else -> {
                NavHost(navController = navController, startDestination = "home") {

                    // ---- Pantalla principal ----
                    composable("home") {
                        HomeScreen(
                            playerManager = viewModel.playerManager,
                            liveChannels = uiState.liveChannels,
                            onChannelSelected = { viewModel.playChannel(it) },
                            onCategoryClick = { category ->
                                navController.navigate("categoryPicker/$category")
                            },
                            onSearchClick = { navController.navigate("search") },
                            onFavoritesClick = { navController.navigate("favorites") }
                        )
                    }

                    // ---- Paso 1: elegir subcategoría dentro de Vivo/Serie/Película/Anime/Especial ----
                    composable("categoryPicker/{category}") { backStackEntry ->
                        val category = backStackEntry.arguments?.getString("category")
                        val type = categoryFromRoute(category)

                        LaunchedEffect(category) {
                            viewModel.loadCategoriesForType(type)
                        }

                        CategoryPickerScreen(
                            isLoading = categoryPickerState.isLoading,
                            categories = categoryPickerState.categories,
                            errorMessage = categoryPickerState.errorMessage,
                            onCategorySelected = { selectedCategory ->
                                val encodedId = URLEncoder.encode(selectedCategory.id, "UTF-8")
                                navController.navigate("categoryContent/$category/$encodedId")
                            }
                        )
                    }

                    // ---- Paso 2: contenido real de la subcategoría elegida ----
                    composable("categoryContent/{category}/{categoryId}") { backStackEntry ->
                        val category = backStackEntry.arguments?.getString("category")
                        val categoryId = backStackEntry.arguments?.getString("categoryId")
                            ?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
                        val type = categoryFromRoute(category)

                        LaunchedEffect(category, categoryId) {
                            viewModel.loadCategoryContent(type, categoryId)
                        }

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

                    // ---- Búsqueda global ----
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

                    // ---- Favoritos ----
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
        }
    }
}
