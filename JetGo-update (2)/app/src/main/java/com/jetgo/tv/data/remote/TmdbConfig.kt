package com.jetgo.tv.data.remote

/**
 * PON AQUÍ TU LLAVE (API KEY) DE TMDB (The Movie Database).
 *
 * Es gratis: entra a https://www.themoviedb.org/ , crea una cuenta, ve a
 * Configuración -> API -> "Create" (elige "Developer"), completa el formulario corto,
 * y te dan una "API Key (v3 auth)". Copia esa llave y pégala abajo, entre las comillas.
 *
 * Sin esto, la búsqueda automática de carátulas queda desactivada (no rompe nada,
 * simplemente no busca — la app sigue funcionando normal con lo que ya tenga).
 */
object TmdbConfig {
    const val API_KEY = "8144358ceac799311ea84de6c12f8286"

    val isConfigured: Boolean
        get() = API_KEY.isNotBlank() && API_KEY != "PON_AQUI_TU_API_KEY_DE_TMDB"
}
