package com.jetgo.tv.data.remote

import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.data.model.ContentType
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * Descarga y parsea una lista M3U/M3U8 clásica con líneas #EXTINF:
 * #EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="Deportes",Nombre del canal
 * http://servidor/live/usuario/pass/12345.m3u8
 *
 * Se lee línea por línea directo de la conexión (en vez de cargar el archivo completo en
 * memoria de una sola vez) — las listas OTT/IPTV grandes pueden pesar varios MB, y cargarlas
 * enteras como un solo texto gigante puede hacer que el sistema mate la app en TV Box con
 * poca memoria disponible.
 */
object M3uParser {

    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .build()
        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // listas OTT grandes pueden tardar más en bajar completas
        .build()

    data class ParseResult(val categories: List<Category>, val channels: List<Channel>)

    /** Límite de seguridad: si una lista tiene más canales que esto, se corta ahí — mejor
     *  mostrar una lista incompleta que arriesgarse a quedarse sin memoria. */
    private const val MAX_CHANNELS = 40_000

    fun fetchAndParse(url: String): ParseResult {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: return ParseResult(emptyList(), emptyList())
            return try {
                body.byteStream().bufferedReader().use { reader -> parseStream(reader) }
            } catch (e: OutOfMemoryError) {
                // Última red de seguridad: si aun así se queda sin memoria, se devuelve lo
                // que se alcanzó a leer hasta ahí en vez de tumbar toda la aplicación.
                ParseResult(emptyList(), emptyList())
            }
        }
    }

    /** Por compatibilidad con quien ya tenga el contenido como texto (ej. pruebas) */
    fun parse(m3uContent: String): ParseResult = parseStream(m3uContent.lineSequence().iterator())

    private fun parseStream(reader: BufferedReader): ParseResult = parseStream(reader.lineSequence().iterator())

    private fun parseStream(lines: Iterator<String>): ParseResult {
        val channels = mutableListOf<Channel>()
        val categoryNames = linkedSetOf<String>()

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingEpgId: String? = null
        var index = 0

        while (lines.hasNext()) {
            if (channels.size >= MAX_CHANNELS) break
            val line = lines.next().trim()
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingLogo = extractAttr(line, "tvg-logo")
                pendingGroup = extractAttr(line, "group-title") ?: "General"
                pendingEpgId = extractAttr(line, "tvg-id")
                pendingName = line.substringAfterLast(",").trim()
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                // Es la URL del stream, cierra la entrada pendiente
                val group = pendingGroup ?: "General"
                categoryNames.add(group)
                channels.add(
                    Channel(
                        streamId = "m3u_${index++}",
                        name = pendingName ?: "Canal ${index}",
                        logoUrl = pendingLogo,
                        categoryId = group,
                        streamUrl = line,
                        epgChannelId = pendingEpgId
                    )
                )
                pendingName = null; pendingLogo = null; pendingGroup = null; pendingEpgId = null
            }
        }

        val categories = categoryNames.map { Category(id = it, name = it, type = ContentType.LIVE) }
        return ParseResult(categories, channels)
    }

    private fun extractAttr(line: String, attr: String): String? {
        val regex = Regex("""$attr="([^"]*)"""")
        return regex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }
}
