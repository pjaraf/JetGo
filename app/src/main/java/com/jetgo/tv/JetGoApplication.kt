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

        // Red de seguridad general: si ocurre un error que ni siquiera las protecciones
        // puntuales (ej. dentro del reproductor) alcanzan a cubrir, en vez de que la app se
        // cierre por completo y el cliente quede afuera, se reinicia sola en la pantalla de
        // inicio — así el problema puntual no lo deja sin poder usar la app.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val intent = android.content.Intent(this, MainActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
                alarmManager.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 500, pendingIntent)
            } catch (e: Exception) {
                // Si ni siquiera se pudo programar el reinicio, no hay más nada que hacer acá
            }
            android.os.Process.killProcess(android.os.Process.myPid())
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
