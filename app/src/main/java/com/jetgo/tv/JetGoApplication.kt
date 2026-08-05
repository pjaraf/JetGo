package com.jetgo.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

/**
 * Configura un ImageLoader global para Coil que manda el mismo User-Agent tipo navegador
 * que usamos para las peticiones a la API de Xtream Codes. Muchos paneles bloquean también
 * las imágenes (carátulas/logos) si no reconocen el User-Agent, mostrando carátulas rotas
 * o genéricas en vez de las originales.
 */
class JetGoApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .crossfade(200)
            .memoryCachePolicy(coil.decode.CachePolicy.ENABLED)
            .diskCachePolicy(coil.decode.CachePolicy.ENABLED)
            .build()
    }
}
