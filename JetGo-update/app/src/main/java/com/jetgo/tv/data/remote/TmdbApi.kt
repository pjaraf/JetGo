package com.jetgo.tv.data.remote

import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class TmdbSearchResponse(
    val results: List<TmdbResultDto> = emptyList()
)

data class TmdbResultDto(
    val title: String? = null,       // para películas
    val name: String? = null,        // para series
    val poster_path: String? = null
)

interface TmdbApi {

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "es-ES"
    ): Response<TmdbSearchResponse>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "es-ES"
    ): Response<TmdbSearchResponse>

    companion object {
        /** Tamaño de imagen razonable para pósters en TV/teléfono */
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"

        fun create(): TmdbApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TmdbApi::class.java)
        }
    }
}
