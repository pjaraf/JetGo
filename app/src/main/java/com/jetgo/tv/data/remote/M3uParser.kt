package com.jetgo.tv.data.remote

import com.jetgo.tv.data.model.Category
import com.jetgo.tv.data.model.Channel
import com.jetgo.tv.data.model.ContentType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Descarga y parsea una lista M3U/M3U8 clásica con líneas #EXTINF:
 * #EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="Deportes",Nombre del canal
 * http://servidor/live/usuario/pass/12345.m3u8
 */
object M3uParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class ParseResult(val categories: List<Category>, val channels: List<Channel>)

    fun fetchAndParse(url: String): ParseResult {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return ParseResult(emptyList(), emptyList())
            return parse(body)
        }
    }

    fun parse(m3uContent: String): ParseResult {
        val lines = m3uContent.lines()
        val channels = mutableListOf<Channel>()
        val categoryNames = linkedSetOf<String>()

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingEpgId: String? = null
        var index = 0

        for (rawLine in lines) {
            val line = rawLine.trim()
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
