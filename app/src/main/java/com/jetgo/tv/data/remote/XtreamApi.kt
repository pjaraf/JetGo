package com.jetgo.tv.data.remote

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

interface XtreamApi {

    @GET("player_api.php")
    suspend fun login(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<UserInfoResponse>

    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): Response<List<CategoryDto>>

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null
    ): Response<List<LiveStreamDto>>

    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): Response<List<CategoryDto>>

    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
        @Query("category_id") categoryId: String? = null
    ): Response<List<VodStreamDto>>

    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): Response<List<CategoryDto>>

    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series",
        @Query("category_id") categoryId: String? = null
    ): Response<List<SeriesDto>>

    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: String
    ): Response<SeriesInfoResponse>

    @GET("player_api.php")
    suspend fun getVodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_info",
        @Query("vod_id") vodId: String
    ): Response<VodInfoResponse>

    @GET("player_api.php")
    suspend fun getShortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_id") streamId: String,
        @Query("limit") limit: Int = 2
    ): Response<ShortEpgResponse>

    companion object {
        /** [baseUrl] debe terminar en "/", ej: "http://midominio.com:8080/" */
        fun create(baseUrl: String): XtreamApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val userAgentInterceptor = okhttp3.Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                    .build()
                chain.proceed(request)
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(userAgentInterceptor)
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(lenientGson))
                .build()
                .create(XtreamApi::class.java)
        }

        /**
         * Gson especial: algunos paneles Xtream mandan el campo "info" como una LISTA VACÍA `[]`
         * en vez de un objeto `{}` cuando una película/serie no tiene datos adicionales cargados
         * (sinopsis, elenco, etc.). Sin esto, la app se cae al intentar leer esos casos.
         */
        private val lenientGson: com.google.gson.Gson by lazy {
            val safeInfoAdapter = com.google.gson.JsonDeserializer<Any?> { json, typeOfT, context ->
                if (json != null && json.isJsonObject) {
                    context.deserialize(json, typeOfT)
                } else {
                    null // era una lista vacía (u otra cosa rara): no hay info real
                }
            }
            com.google.gson.GsonBuilder()
                .registerTypeAdapter(VodInfoDetailsDto::class.java, safeInfoAdapter)
                .registerTypeAdapter(SeriesInfoDetailsDto::class.java, safeInfoAdapter)
                .create()
        }

        /** Construye la URL directa de un canal en vivo para pasarla al reproductor */
        fun liveStreamUrl(host: String, username: String, password: String, streamId: Int, ext: String = "m3u8") =
            "${host.trimEnd('/')}/live/$username/$password/$streamId.$ext"

        /** Construye la URL directa de una película (VOD) */
        fun vodStreamUrl(host: String, username: String, password: String, streamId: Int, ext: String) =
            "${host.trimEnd('/')}/movie/$username/$password/$streamId.$ext"

        /** Construye la URL directa de un episodio de serie */
        fun seriesStreamUrl(host: String, username: String, password: String, episodeId: Int, ext: String) =
            "${host.trimEnd('/')}/series/$username/$password/$episodeId.$ext"
    }
}
