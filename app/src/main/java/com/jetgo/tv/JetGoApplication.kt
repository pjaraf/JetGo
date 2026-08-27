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

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("JetGo_Crash", "Excepción no capturada en ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

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
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.12) // más bajo que el 25% por defecto: deja más memoria libre para el video
                    .build()
            }
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565) // carátulas livianas, la mitad de memoria que el formato normal
            .build()
    }
}
