package com.jetgo.tv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
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
 * Maneja DOS reproductores completamente separados — uno para Vivo y otro para Película/Serie
 * — para que cualquier ajuste que se necesite en uno (ej. compatibilidad de decodificador en
 * algún TV Box puntual) se pueda aplicar sin que le toque nada al otro, ni siquiera como
 * posible efecto secundario. Cada uno tiene su propia lógica de reconexión/reintento, así
 * ninguno de los dos depende del estado del otro.
 */
class PlayerManager(context: Context) {

    // =====================================================================================
    // REPRODUCTOR DE VIVO — configuración simple, sin ningún ajuste especial, tal como
    // siempre funcionó. No se debe tocar esta parte para probar cosas nuevas: para eso está
    // el reproductor de Película/Serie de más abajo, que es completamente independiente.
    // =====================================================================================
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    // =====================================================================================
    // REPRODUCTOR DE PELÍCULA/SERIE — separado por completo del de Vivo.
    //
    // El selector de decodificador NUNCA excluye nada de entrada — solo lo hace de forma
    // REACTIVA: si un intento de reproducción falla y el error confirma que fue justo el
    // decodificador "c2.mtk.avc.decoder" (con fallas conocidas en algunos chips MediaTek,
    // como el de varios TV con Google TV de la marca TCL) el que se cayó, recién ahí se
    // excluye SOLO para el siguiente intento de ESE mismo contenido — nunca de forma
    // preventiva ni global, porque otros equipos con el mismo chip pueden reproducir bien.
    // =====================================================================================
    private class VodRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
        /** Nombre del decoder a excluir en el próximo intento — vacío mientras no haya
         *  ninguna falla confirmada. @Volatile porque se escribe desde el hilo principal
         *  (al procesar el error) y se lee desde el hilo interno del reproductor. */
        @Volatile
        var decoderToExclude: String = ""

        private val selector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = try {
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            } catch (e: Exception) {
                emptyList()
            }
            val toExclude = decoderToExclude
            if (toExclude.isNotEmpty() && decoders.size > 1) {
                val filtered = decoders.filterNot { it.name == toExclude }
                // Si excluirlo dejara la lista vacía, mejor usar el original (es la única
                // opción que tiene el equipo) — la reproducción puede fallar igual, pero no
                // hay que dejarla sin NINGÚN decodificador para probar.
                if (filtered.isNotEmpty()) filtered else decoders
            } else {
                decoders
            }
        }

        override fun buildVideoRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            eventHandler: android.os.Handler,
            eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
            allowedVideoJoiningTimeMs: Long,
            out: ArrayList<androidx.media3.exoplayer.Renderer>
        ) {
            super.buildVideoRenderers(
                context, extensionRendererMode, selector, enableDecoderFallback,
                eventHandler, eventListener, allowedVideoJoiningTimeMs, out
            )
        }
    }

    private val vodRenderersFactory = VodRenderersFactory(context).apply {
        setEnableDecoderFallback(true)
        // Dejar que Media3 prefiera un decodificador de extensión (si el proyecto llegara a
        // incluir alguno más adelante) antes que el de plataforma — hoy no cambia nada porque
        // no hay extensiones agregadas, pero no tiene costo dejarlo listo.
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    }

    val vodExoPlayer: ExoPlayer = ExoPlayer.Builder(context, vodRenderersFactory).build()

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

    /** Últimos datos técnicos registrados (para diagnóstico, sin credenciales ni URL) —
     *  fabricante/modelo, decoder usado, código de error y en qué etapa pasó. */
    private val _lastDiagnostic = mutableStateOf<String>("")
    val lastDiagnostic: State<String> get() = _lastDiagnostic

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Agrupa todo el estado de reconexión/reintento que necesita CADA reproductor por
     *  separado — así el de Vivo y el de Película/Serie nunca se pisan entre sí. */
    private inner class PlayerState(val player: ExoPlayer, val isLive: Boolean) {
        var retryCount = 0
        var bufferingReconnectCount = 0
        var lastUrl: String? = null
        var lastName: String = ""
        var currentUrl: String? = null
        var bufferingTimeoutRunnable: Runnable? = null
        /** true una vez que ya se reintentó ESTE contenido excluyendo el decoder MTK —
         *  para no entrar en un bucle si vuelve a fallar igual. */
        var yaExcluyoDecoder = false

        fun cancelBufferingTimeout() {
            bufferingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            bufferingTimeoutRunnable = null
        }

        fun startBufferingTimeout() {
            cancelBufferingTimeout()
            val runnable = Runnable {
                // Si sigue "cargando" sin avanzar después de unos segundos, probablemente se
                // pegó (corte momentáneo de señal, etc.) — se reconecta solo, sin avisarle
                // nada al cliente, para que la interrupción se note lo menos posible.
                if (player.playbackState == Player.STATE_BUFFERING && bufferingReconnectCount < 4) {
                    bufferingReconnectCount++
                    val url = lastUrl
                    if (url != null) {
                        try {
                            player.stop()
                            player.prepare()
                            player.playWhenReady = true
                            player.play()
                        } catch (e: Exception) { /* se reintenta de nuevo si vuelve a quedar pegado */ }
                    }
                    startBufferingTimeout() // sigue vigilando por si necesita reconectar de nuevo
                }
            }
            bufferingTimeoutRunnable = runnable
            mainHandler.postDelayed(runnable, 8_000)
        }
    }

    private val liveState = PlayerState(exoPlayer, isLive = true)
    private val vodState = PlayerState(vodExoPlayer, isLive = false)

    init {
        setupPlayer(exoPlayer, liveState)
        setupPlayer(vodExoPlayer, vodState)
    }

    private fun setupPlayer(player: ExoPlayer, state: PlayerState) {
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long
            ) {
                // Solo actualiza el bitrate visible si este reproductor es el que está
                // activo ahora mismo (evita que el que está en pausa de fondo pise el dato).
                if (state.currentUrl != null) {
                    val kbps = (bitrateEstimate / 1000).toInt()
                    _stats.value = _stats.value.copy(bitrateKbps = kbps)
                }
            }
        })

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackEnded?.invoke()
                }
                if (playbackState == Player.STATE_READY) {
                    // Volvió a andar bien: limpia cualquier error anterior y resetea los reintentos
                    _playbackError.value = null
                    state.retryCount = 0; state.bufferingReconnectCount = 0
                    state.cancelBufferingTimeout()
                } else if (playbackState == Player.STATE_BUFFERING) {
                    state.startBufferingTimeout()
                } else {
                    state.cancelBufferingTimeout()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // ---- Diagnóstico: identifica si la falla fue justo del decoder MTK ----
                val decoderName = extractDecoderName(error)
                _lastDiagnostic.value = "modelo=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL} " +
                    "android=${android.os.Build.VERSION.RELEASE} decoder=${decoderName ?: "?"} " +
                    "error=${error.errorCodeName} etapa=${if (state.isLive) "vivo" else "vod"}"

                // Si en PELÍCULA/SERIE la falla confirma que fue "c2.mtk.avc.decoder" el que se
                // cayó, y todavía no se probó excluyéndolo para este contenido puntual, se
                // reintenta UNA vez sin ese decoder — nunca de forma preventiva ni para Vivo.
                if (!state.isLive && decoderName == "c2.mtk.avc.decoder" && !state.yaExcluyoDecoder) {
                    state.yaExcluyoDecoder = true
                    vodRenderersFactory.decoderToExclude = "c2.mtk.avc.decoder"
                    val url = state.lastUrl
                    if (url != null) {
                        try {
                            player.stop()
                            player.clearMediaItems()
                            player.setMediaSource(
                                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                                    DefaultHttpDataSource.Factory()
                                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                        .setConnectTimeoutMs(15000)
                                        .setReadTimeoutMs(15000)
                                        .setAllowCrossProtocolRedirects(true)
                                ).createMediaSource(MediaItem.fromUri(url))
                            )
                            player.prepare()
                            player.playWhenReady = true
                            return
                        } catch (e: Exception) { /* si ni siquiera esto se puede armar, sigue abajo */ }
                    }
                }

                val url = state.lastUrl
                if (url != null && state.retryCount < 2) {
                    // Reintenta un par de veces solo (cortes momentáneos de red/servidor),
                    // antes de mostrarle un error al usuario.
                    state.retryCount++
                    try {
                        player.prepare()
                        player.playWhenReady = true
                    } catch (e: Exception) { /* ignorar, se maneja abajo si vuelve a fallar */ }
                } else {
                    val causaHttp = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                    val detalle = when {
                        causaHttp != null -> "El servidor respondió con error HTTP $causaHttp"
                        else -> "${error.errorCodeName} — ${error.cause?.message ?: error.message}"
                    }
                    _playbackError.value = "No se pudo reproducir \"${state.lastName}\".\n$detalle"
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

    /** Busca en la cadena de causas del error el nombre del decoder que falló, si el error
     *  fue justo una falla de inicialización/decodificación (no todos los errores lo traen). */
    private fun extractDecoderName(error: androidx.media3.common.PlaybackException): String? {
        var cause: Throwable? = error
        var vueltas = 0
        while (cause != null && vueltas < 8) {
            val mensaje = cause.message
            if (cause is androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException) {
                return cause.codecInfo?.name
            }
            if (mensaje != null && mensaje.contains("c2.mtk.avc.decoder")) {
                return "c2.mtk.avc.decoder"
            }
            cause = cause.cause
            vueltas++
        }
        return null
    }

    private var currentUrl: String? = null

    /** [isLive] decide cuál de los 2 reproductores se usa — Vivo o Película/Serie. */
    fun playChannel(url: String, name: String, isLive: Boolean = true) {
        val state = if (isLive) liveState else vodState
        val player = state.player

        if (url == state.currentUrl && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
            // Ya está reproduciendo justo esta URL: no la recarga de nuevo
            player.playWhenReady = true
            return
        }
        state.currentUrl = url
        state.lastUrl = url
        state.lastName = name
        state.retryCount = 0; state.bufferingReconnectCount = 0
        state.yaExcluyoDecoder = false
        vodRenderersFactory.decoderToExclude = "" // cada contenido nuevo empieza sin exclusiones
        _playbackError.value = null
        _videoQuality.value = null
        state.cancelBufferingTimeout()

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

        _stats.value = _stats.value.copy(channelName = name, isLive = isLive)
        // Se limpia por completo el reproductor (no solo "detenido") antes de cargar lo nuevo:
        // si solo se detiene, puede quedar algo del canal anterior a medio camino, y cambiar
        // rápido entre canales (sobre todo volviendo al mismo de hace un momento) se queda con
        // la imagen en negro. Limpiando la cola entera se fuerza a arrancar siempre de cero.
        player.stop()
        player.clearMediaItems()
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
        player.play() // explícito además de playWhenReady: en algunos TV Box, solo con
                       // la marca "listo para reproducir" no alcanza para que arranque solo
    }

    fun release() {
        exoPlayer.release()
        vodExoPlayer.release()
    }

    /** Pausa ambos reproductores — se usa cuando la app pasa a segundo plano */
    fun pauseAll() {
        try { exoPlayer.pause() } catch (e: Exception) { /* ignorar */ }
        try { vodExoPlayer.pause() } catch (e: Exception) { /* ignorar */ }
    }

    /** Reanuda ambos reproductores (el que no tenía nada cargado simplemente no hace nada) */
    fun playAll() {
        try { exoPlayer.play() } catch (e: Exception) { /* ignorar */ }
        try { vodExoPlayer.play() } catch (e: Exception) { /* ignorar */ }
    }

    /** Detiene ambos por completo — usado al cerrar sesión o desconectar */
    fun stopAll() {
        try { exoPlayer.stop() } catch (e: Exception) { /* ignorar */ }
        try { vodExoPlayer.stop() } catch (e: Exception) { /* ignorar */ }
    }

    // ---- Todo lo de acá para abajo es SOLO para Película/Serie (el reproductor de Vivo no
    // usa nada de esto: no tiene controles de avance/retroceso ni pistas seleccionables) ----

    /** Posición actual de reproducción, en milisegundos */
    fun currentPositionMs(): Long = try { vodExoPlayer.currentPosition } catch (e: Exception) { 0L }

    /** Duración total del contenido actual, en milisegundos (0 si aún no se sabe) */
    fun durationMs(): Long = try { vodExoPlayer.duration.coerceAtLeast(0) } catch (e: Exception) { 0L }

    /** Salta a una posición específica (usado para "Seguir viendo" y la barra de progreso) */
    fun seekTo(positionMs: Long) {
        try { vodExoPlayer.seekTo(positionMs) } catch (e: Exception) { /* ignorar */ }
    }

    fun togglePlayPause() {
        if (vodExoPlayer.isPlaying) vodExoPlayer.pause() else vodExoPlayer.play()
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
            val tracks = vodExoPlayer.currentTracks
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
            vodExoPlayer.trackSelectionParameters = vodExoPlayer.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(override)
                .build()
        } catch (e: Exception) { /* ignorar */ }
    }

    /** Apaga los subtítulos por completo */
    fun disableSubtitles() {
        try {
            vodExoPlayer.trackSelectionParameters = vodExoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } catch (e: Exception) { /* ignorar */ }
    }

    private fun enableSubtitleType() {
        try {
            vodExoPlayer.trackSelectionParameters = vodExoPlayer.trackSelectionParameters
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
