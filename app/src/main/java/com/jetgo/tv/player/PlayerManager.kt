package com.jetgo.tv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.jetgo.tv.data.model.PlaybackStats

/** Una pista de audio o subtítulo disponible para elegir */
data class TrackOption(
    val group: androidx.media3.common.TrackGroup,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

/**
 * Encapsula un único ExoPlayer reutilizable para el panel de reproducción en vivo,
 * exponiendo bitrate/estado como State de Compose para actualizar el overlay
 * (equivalente al indicador "4Kb/s" que se ve en la captura de referencia).
 */
class PlayerManager(context: Context) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _stats = mutableStateOf(PlaybackStats())
    val stats: State<PlaybackStats> get() = _stats

    private val _isPlaying = mutableStateOf(true)
    val isPlaying: State<Boolean> get() = _isPlaying

    /** Calidad real del video que se está reproduciendo AHORA MISMO (según su resolución real) */
    private val _videoQuality = mutableStateOf<String?>(null)
    val videoQuality: State<String?> get() = _videoQuality

    /** Se dispara cuando el contenido actual termina de reproducirse por completo (fin de capítulo/película) */
    var onPlaybackEnded: (() -> Unit)? = null

    /** Mensaje de error si la reproducción falló (canal caído, URL rota, etc.) — antes esto
     *  se perdía en silencio y solo se veía una pantalla negra sin ninguna explicación. */
    private val _playbackError = mutableStateOf<String?>(null)
    val playbackError: State<String?> get() = _playbackError

    private var retryCount = 0
    private var bufferingReconnectCount = 0
    private var lastUrl: String? = null
    private var lastName: String = ""

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var bufferingTimeoutRunnable: Runnable? = null

    private fun cancelBufferingTimeout() {
        bufferingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        bufferingTimeoutRunnable = null
    }

    private fun startBufferingTimeout() {
        cancelBufferingTimeout()
        val runnable = Runnable {
            // Si sigue "cargando" sin avanzar después de unos segundos, probablemente se pegó
            // (corte momentáneo de señal, etc.) — se reconecta sola, sin avisarle nada al
            // cliente, para que la interrupción se note lo menos posible.
            if (exoPlayer.playbackState == Player.STATE_BUFFERING && bufferingReconnectCount < 4) {
                bufferingReconnectCount++
                val url = lastUrl
                if (url != null) {
                    try {
                        exoPlayer.stop()
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    } catch (e: Exception) { /* se reintenta de nuevo si vuelve a quedar pegado */ }
                }
                startBufferingTimeout() // sigue vigilando por si necesita reconectar de nuevo
            }
        }
        bufferingTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, 8_000)
    }

    init {
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long
            ) {
                val kbps = (bitrateEstimate / 1000).toInt()
                _stats.value = _stats.value.copy(bitrateKbps = kbps)
            }
        })

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackEnded?.invoke()
                }
                if (playbackState == Player.STATE_READY) {
                    // Volvió a andar bien: limpia cualquier error anterior y resetea los reintentos
                    _playbackError.value = null
                    retryCount = 0; bufferingReconnectCount = 0
                    cancelBufferingTimeout()
                } else if (playbackState == Player.STATE_BUFFERING) {
                    startBufferingTimeout()
                } else {
                    cancelBufferingTimeout()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val url = lastUrl
                if (url != null && retryCount < 2) {
                    // Reintenta un par de veces solo (cortes momentáneos de red/servidor),
                    // antes de mostrarle un error al usuario.
                    retryCount++
                    try {
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    } catch (e: Exception) { /* ignorar, se maneja abajo si vuelve a fallar */ }
                } else {
                    val causaHttp = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                    val detalle = when {
                        causaHttp != null -> "El servidor respondió con error HTTP $causaHttp"
                        else -> "${error.errorCodeName} — ${error.cause?.message ?: error.message}"
                    }
                    _playbackError.value = "No se pudo reproducir \"$lastName\".\n$detalle"
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                _videoQuality.value = when {
                    videoSize.height <= 0 -> null
                    videoSize.height >= 2000 -> "4K"
                    videoSize.height >= 1000 -> "FHD"
                    videoSize.height >= 700 -> "HD"
                    else -> "SD"
                }
            }
        })
    }

    private var currentUrl: String? = null

    fun playChannel(url: String, name: String) {
        if (url == currentUrl && exoPlayer.playbackState != Player.STATE_IDLE && exoPlayer.playbackState != Player.STATE_ENDED) {
            // Ya está reproduciendo justo esta URL: no la recarga de nuevo
            exoPlayer.playWhenReady = true
            return
        }
        currentUrl = url
        lastUrl = url
        lastName = name
        retryCount = 0; bufferingReconnectCount = 0
        _playbackError.value = null
        _videoQuality.value = null
        cancelBufferingTimeout()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        val mediaItem = MediaItem.fromUri(url)

        // Antes se elegía a mano entre HLS/progresivo mirando si la URL tenía ".m3u8" — pero
        // las películas/series vienen en formatos variados (.mp4, .mkv, .ts, etc.) que ese
        // chequeo simple no cubre bien en todos los casos. DefaultMediaSourceFactory detecta
        // el tipo correcto de forma más confiable, sea cual sea el formato del archivo.
        val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpDataSourceFactory)
            .createMediaSource(mediaItem)

        _stats.value = _stats.value.copy(channelName = name, isLive = true)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.play() // explícito además de playWhenReady: en algunos TV Box, solo con
                          // la marca "listo para reproducir" no alcanza para que arranque solo
    }

    fun release() {
        exoPlayer.release()
    }

    /** Posición actual de reproducción, en milisegundos */
    fun currentPositionMs(): Long = try { exoPlayer.currentPosition } catch (e: Exception) { 0L }

    /** Duración total del contenido actual, en milisegundos (0 si aún no se sabe, ej. streams en vivo) */
    fun durationMs(): Long = try { exoPlayer.duration.coerceAtLeast(0) } catch (e: Exception) { 0L }

    /** Salta a una posición específica (usado para "Seguir viendo" y la barra de progreso) */
    fun seekTo(positionMs: Long) {
        try { exoPlayer.seekTo(positionMs) } catch (e: Exception) { /* ignorar */ }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekForward(ms: Long = 10_000) {
        seekTo((currentPositionMs() + ms).coerceAtMost(durationMs()))
    }

    fun seekBackward(ms: Long = 10_000) {
        seekTo((currentPositionMs() - ms).coerceAtLeast(0))
    }

    /** Pistas de audio disponibles en el contenido actual */
    fun getAudioTracks(): List<TrackOption> = getTracks(C.TRACK_TYPE_AUDIO)

    /** Pistas de subtítulos disponibles en el contenido actual */
    fun getSubtitleTracks(): List<TrackOption> = getTracks(C.TRACK_TYPE_TEXT)

    private fun getTracks(type: Int): List<TrackOption> {
        val result = mutableListOf<TrackOption>()
        try {
            val tracks = exoPlayer.currentTracks
            for (group in tracks.groups) {
                if (group.type != type) continue
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = format.label
                        ?: format.language?.uppercase()
                        ?: "Pista ${result.size + 1}"
                    result.add(TrackOption(group.mediaTrackGroup, i, label, group.isTrackSelected(i)))
                }
            }
        } catch (e: Exception) { /* sin pistas disponibles */ }
        return result
    }

    fun selectTrack(option: TrackOption) {
        try {
            val override = TrackSelectionOverride(option.group, listOf(option.trackIndex))
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(override)
                .build()
        } catch (e: Exception) { /* ignorar */ }
    }

    /** Apaga los subtítulos por completo */
    fun disableSubtitles() {
        try {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } catch (e: Exception) { /* ignorar */ }
    }

    private fun enableSubtitleType() {
        try {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        } catch (e: Exception) { /* ignorar */ }
    }

    fun selectSubtitleTrack(option: TrackOption) {
        enableSubtitleType()
        selectTrack(option)
    }
}
