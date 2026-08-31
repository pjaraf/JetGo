package com.jetgo.tv.player

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.jetgo.tv.data.model.PlaybackStats
import java.util.concurrent.Executors
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/** Una pista de audio o subtítulo disponible para elegir (id interno de VLC + nombre) */
data class TrackOption(
    val trackId: Int,
    val label: String,
    val isSelected: Boolean
)

/**
 * Maneja DOS reproductores VLC completamente separados — uno para Vivo y otro para Película/
 * Serie — optimizados para zapping instantáneo sin congelamiento ni cierres inesperados.
 */
class PlayerManager(context: Context) {

    /** Executor en segundo plano para liberar recursos de streams anteriores sin demorar el hilo UI */
    private val releaseExecutor = Executors.newSingleThreadExecutor()

    /** Motor de VLC — optimizado para zapping ultra rápido en TV Boxes, Android TV y teléfonos */
    private val libVLC: LibVLC by lazy {
        try {
            LibVLC(
                context,
                arrayListOf(
                    "--network-caching=300",
                    "--live-caching=300",
                    "--file-caching=1000",
                    "--ipv4",
                    "--avcodec-fast",
                    "--avcodec-threads=0",
                    "--no-stats",
                    "--no-video-title-show",
                    "--rtsp-tcp",
                    "--no-drop-late-frames"
                )
            )
        } catch (e: Throwable) {
            android.util.Log.e("JetGo_Player", "Error al iniciar LibVLC con opciones personalizadas", e)
            LibVLC(context)
        }
    }

    // =====================================================================================
    // REPRODUCTOR DE VIVO — optimizado para cambio rápido de canales (zapping)
    // =====================================================================================
    val livePlayer: MediaPlayer by lazy { MediaPlayer(libVLC) }

    // =====================================================================================
    // REPRODUCTOR DE PELÍCULA/SERIE — independiente del reproductor de vivo
    // =====================================================================================
    val vodPlayer: MediaPlayer by lazy { MediaPlayer(libVLC) }

    private val _stats = mutableStateOf(PlaybackStats())
    val stats: State<PlaybackStats> get() = _stats

    private val _isPlaying = mutableStateOf(true)
    val isPlaying: State<Boolean> get() = _isPlaying

    /** Calidad real del video que se está reproduciendo AHORA MISMO */
    private val _videoQuality = mutableStateOf<String?>(null)
    val videoQuality: State<String?> get() = _videoQuality

    /** Se dispara cuando el contenido actual termina de reproducirse por completo */
    var onPlaybackEnded: (() -> Unit)? = null

    /** Mensaje de error si la reproducción falló */
    private val _playbackError = mutableStateOf<String?>(null)
    val playbackError: State<String?> get() = _playbackError

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Agrupa todo el estado de reconexión/reintento y generación de cada reproductor */
    private inner class PlayerState(val player: MediaPlayer, val isLive: Boolean) {
        var generationId: Long = 0L
        var retryCount = 0
        var bufferingReconnectCount = 0
        var lastUrl: String? = null
        var lastName: String = ""
        var currentUrl: String? = null
        var currentMedia: Media? = null
        var bufferingTimeoutRunnable: Runnable? = null
        var errorRetryRunnable: Runnable? = null
        var isBuffering = false
        var yaForzoSoftware = false
        var lastPosition: Long = -1L
        var lastProgressTime: Long = System.currentTimeMillis()

        fun cancelBufferingTimeout() {
            bufferingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            bufferingTimeoutRunnable = null
        }

        fun cancelErrorRetry() {
            errorRetryRunnable?.let { mainHandler.removeCallbacks(it) }
            errorRetryRunnable = null
        }

        fun cancelAllCallbacks() {
            cancelBufferingTimeout()
            cancelErrorRetry()
        }

        fun startBufferingTimeout() {
            cancelBufferingTimeout()
            val gen = generationId
            val runnable = Runnable {
                if (generationId != gen) return@Runnable
                if (isBuffering && !isLive) {
                    if (bufferingReconnectCount < 3) {
                        bufferingReconnectCount++
                        val url = lastUrl
                        if (url != null) {
                            try {
                                player.stop()
                                player.play()
                            } catch (e: Throwable) {}
                        }
                        startBufferingTimeout()
                    }
                }
            }
            bufferingTimeoutRunnable = runnable
            mainHandler.postDelayed(runnable, 8_000)
        }
    }

    private val liveState = PlayerState(livePlayer, isLive = true)
    private val vodState = PlayerState(vodPlayer, isLive = false)

    init {
        setupPlayer(livePlayer, liveState)
        setupPlayer(vodPlayer, vodState)
    }

    private fun setupPlayer(player: MediaPlayer, state: PlayerState) {
        player.setEventListener { event ->
            val eventGen = state.generationId
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    if (state.generationId != eventGen) return@setEventListener
                    state.isBuffering = false
                    _isPlaying.value = true
                    _playbackError.value = null
                    state.retryCount = 0
                    state.bufferingReconnectCount = 0
                    state.cancelBufferingTimeout()
                }
                MediaPlayer.Event.Paused -> {
                    if (state.generationId != eventGen) return@setEventListener
                    _isPlaying.value = false
                }
                MediaPlayer.Event.Stopped -> {
                    if (state.generationId != eventGen) return@setEventListener
                    _isPlaying.value = false
                    state.cancelBufferingTimeout()
                }
                MediaPlayer.Event.EndReached -> {
                    if (state.generationId != eventGen) return@setEventListener
                    onPlaybackEnded?.invoke()
                }
                MediaPlayer.Event.Buffering -> {
                    if (state.generationId != eventGen) return@setEventListener
                    val pct = event.buffering
                    if (pct < 100f) {
                        if (!state.isBuffering) {
                            state.isBuffering = true
                            state.startBufferingTimeout()
                        }
                    } else {
                        state.isBuffering = false
                        state.cancelBufferingTimeout()
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    if (state.generationId != eventGen) return@setEventListener
                    handlePlaybackError(player, state, eventGen)
                }
                MediaPlayer.Event.Vout -> {
                    if (state.generationId != eventGen) return@setEventListener
                    updateVideoQuality(player)
                }
                else -> {}
            }
        }
    }

    private fun updateVideoQuality(player: MediaPlayer) {
        try {
            val track = player.currentVideoTrack
            val height = track?.height ?: 0
            _videoQuality.value = when {
                height <= 0 -> null
                height >= 2000 -> "4K"
                height >= 1000 -> "FHD"
                height >= 700 -> "HD"
                else -> "SD"
            }
        } catch (e: Throwable) { /* ignorar */ }
    }

    private fun handlePlaybackError(player: MediaPlayer, state: PlayerState, generation: Long) {
        if (state.generationId != generation || state.currentUrl == null) return

        if (!state.yaForzoSoftware) {
            state.yaForzoSoftware = true
            val url = state.lastUrl
            if (url != null && reloadWithSoftwareDecoder(player, url, generation)) {
                return
            }
        }

        if (state.isLive) {
            // Para TV en vivo, reintentar silenciosamente sin mostrar error que interrumpa la experiencia
            val url = state.lastUrl
            if (url != null) {
                state.cancelErrorRetry()
                val runnable = Runnable {
                    if (state.generationId != generation) return@Runnable
                    try {
                        player.stop()
                        player.media = null
                        val oldMedia = state.currentMedia
                        state.currentMedia = null
                        if (oldMedia != null) {
                            releaseExecutor.execute {
                                try { if (!oldMedia.isReleased) oldMedia.release() } catch (t: Throwable) {}
                            }
                        }
                        val cachingMs = if (state.isLive) "300" else "1500"
                        val media = Media(libVLC, android.net.Uri.parse(url))
                        media.addOption(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        media.addOption(":network-caching=$cachingMs")
                        media.addOption(":live-caching=$cachingMs")
                        media.addOption(":http-reconnect=true")
                        media.addOption(":http-continuous=1")
                        media.addOption(":clock-jitter=0")
                        media.addOption(":no-sub-autodetect-file")
                        if (state.generationId != generation) {
                            releaseExecutor.execute {
                                try { if (!media.isReleased) media.release() } catch (t: Throwable) {}
                            }
                            return@Runnable
                        }
                        player.media = media
                        state.currentMedia = media
                        player.play()
                    } catch (e: Throwable) {}
                }
                state.errorRetryRunnable = runnable
                mainHandler.postDelayed(runnable, 1_500)
            }
            return
        }

        val url = state.lastUrl
        if (url != null && state.retryCount < 2) {
            state.retryCount++
            try {
                player.play()
            } catch (e: Throwable) { /* ignorar */ }
        } else {
            if (state.generationId == generation) {
                _playbackError.value = "No se pudo reproducir \"${state.lastName}\"."
            }
        }
    }

    /** Vuelve a cargar el mismo contenido forzando decodificador por software */
    private fun reloadWithSoftwareDecoder(player: MediaPlayer, url: String, generation: Long): Boolean {
        val state = if (player == livePlayer) liveState else vodState
        if (state.generationId != generation) return false
        return try {
            player.stop()
            player.media = null
            val oldMedia = state.currentMedia
            state.currentMedia = null
            if (oldMedia != null) {
                releaseExecutor.execute {
                    try { if (!oldMedia.isReleased) oldMedia.release() } catch (t: Throwable) {}
                }
            }
            val cachingMs = if (state.isLive) "300" else "1500"
            val media = Media(libVLC, android.net.Uri.parse(url))
            media.addOption(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            media.addOption(":network-caching=$cachingMs")
            media.addOption(":live-caching=$cachingMs")
            media.addOption(":http-reconnect=true")
            media.addOption(":http-continuous=1")
            media.addOption(":clock-jitter=0")
            media.addOption(":no-sub-autodetect-file")
            media.setHWDecoderEnabled(false, true)
            if (state.generationId != generation) {
                releaseExecutor.execute {
                    try { if (!media.isReleased) media.release() } catch (t: Throwable) {}
                }
                return false
            }
            player.media = media
            state.currentMedia = media
            player.play()
            true
        } catch (e: Throwable) {
            false
        }
    }

    /** [isLive] decide cuál de los 2 reproductores se usa — Vivo o Película/Serie. */
    fun playChannel(url: String, name: String, isLive: Boolean = true) {
        val state = if (isLive) liveState else vodState
        val player = state.player

        if (url == state.currentUrl && player.isPlaying) {
            // Ya está reproduciendo justo esta URL: no la recarga de nuevo
            return
        }

        // 1. Generación nueva para invalidar cualquier evento, callback o reintento de canales anteriores
        val generation = ++state.generationId
        state.cancelAllCallbacks()

        state.currentUrl = url
        state.lastUrl = url
        state.lastName = name
        state.retryCount = 0
        state.bufferingReconnectCount = 0
        state.yaForzoSoftware = false
        state.isBuffering = false
        _playbackError.value = null
        _videoQuality.value = null
        state.lastPosition = -1L
        state.lastProgressTime = System.currentTimeMillis()

        _stats.value = _stats.value.copy(channelName = name, isLive = isLive)

        val oldMedia = state.currentMedia
        state.currentMedia = null

        // 2. Matar el stream anterior de inmediato
        try {
            player.stop()
        } catch (e: Throwable) {
            // Ignorar errores al detener
        }

        // 3. Desvincular el media anterior antes de liberarlo para evitar bloqueos en el motor C de VLC
        try {
            player.media = null
        } catch (e: Throwable) {
            // Ignorar
        }

        // 4. Liberar el media anterior en background executor para no frenar el hilo principal ni 1ms
        if (oldMedia != null) {
            releaseExecutor.execute {
                try {
                    if (!oldMedia.isReleased) {
                        oldMedia.release()
                    }
                } catch (e: Throwable) {
                    // Ignorar
                }
            }
        }

        // 5. Cargar nuevo stream con búfer ultrabajo (300ms) para arranque instantáneo
        try {
            val cachingMs = if (isLive) "300" else "1500"
            val media = Media(libVLC, android.net.Uri.parse(url))
            media.addOption(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            media.addOption(":network-caching=$cachingMs")
            media.addOption(":live-caching=$cachingMs")
            media.addOption(":http-reconnect=true")
            media.addOption(":http-continuous=1")
            media.addOption(":clock-jitter=0")
            media.addOption(":no-sub-autodetect-file")

            // Si durante la creación del Media el usuario cambió rápidamente a otro canal, descartar
            if (state.generationId != generation) {
                releaseExecutor.execute {
                    try { if (!media.isReleased) media.release() } catch (t: Throwable) {}
                }
                return
            }

            player.media = media
            state.currentMedia = media
            player.play()
        } catch (e: Throwable) {
            if (state.generationId == generation) {
                _playbackError.value = "No se pudo reproducir \"$name\"."
            }
        }
    }

    fun release() {
        try { livePlayer.stop() } catch (e: Throwable) { /* ignorar */ }
        try { vodPlayer.stop() } catch (e: Throwable) { /* ignorar */ }
        try { livePlayer.media = null } catch (e: Throwable) { /* ignorar */ }
        try { vodPlayer.media = null } catch (e: Throwable) { /* ignorar */ }
        try { liveState.currentMedia?.release() } catch (e: Throwable) { /* ignorar */ }
        try { vodState.currentMedia?.release() } catch (e: Throwable) { /* ignorar */ }
        try { livePlayer.release() } catch (e: Throwable) { /* ignorar */ }
        try { vodPlayer.release() } catch (e: Throwable) { /* ignorar */ }
        try { libVLC.release() } catch (e: Throwable) { /* ignorar */ }
        try { releaseExecutor.shutdown() } catch (e: Throwable) { /* ignorar */ }
    }

    /** Pausa ambos reproductores — se usa cuando la app pasa a segundo plano */
    fun pauseAll() {
        try { if (livePlayer.isPlaying) livePlayer.pause() } catch (e: Throwable) { /* ignorar */ }
        try { if (vodPlayer.isPlaying) vodPlayer.pause() } catch (e: Throwable) { /* ignorar */ }
    }

    /** Reanuda ambos reproductores (el que no tenía nada cargado simplemente no hace nada) */
    fun playAll() {
        try { livePlayer.play() } catch (e: Throwable) { /* ignorar */ }
        try { vodPlayer.play() } catch (e: Throwable) { /* ignorar */ }
    }

    /** Detiene ambos por completo — usado al cerrar sesión o desconectar */
    fun stopAll() {
        try { livePlayer.stop() } catch (e: Throwable) { /* ignorar */ }
        try { vodPlayer.stop() } catch (e: Throwable) { /* ignorar */ }
        try { livePlayer.media = null } catch (e: Throwable) { /* ignorar */ }
        try { vodPlayer.media = null } catch (e: Throwable) { /* ignorar */ }
        try { liveState.cancelAllCallbacks() } catch (e: Throwable) { /* ignorar */ }
        try { vodState.cancelAllCallbacks() } catch (e: Throwable) { /* ignorar */ }
    }

    // ---- Controles para Película/Serie ----

    /** Posición actual de reproducción, en milisegundos */
    fun currentPositionMs(): Long = try { vodPlayer.time } catch (e: Throwable) { 0L }

    /** Duración total del contenido actual, en milisegundos (0 si aún no se sabe) */
    fun durationMs(): Long = try { vodPlayer.length.coerceAtLeast(0) } catch (e: Throwable) { 0L }

    /** Salta a una posición específica */
    fun seekTo(positionMs: Long) {
        try { vodPlayer.time = positionMs } catch (e: Throwable) { /* ignorar */ }
    }

    fun togglePlayPause() {
        try {
            if (vodPlayer.isPlaying) vodPlayer.pause() else vodPlayer.play()
        } catch (e: Throwable) { /* ignorar */ }
    }

    fun seekForward(ms: Long = 10_000) {
        seekTo((currentPositionMs() + ms).coerceAtMost(durationMs()))
    }

    fun seekBackward(ms: Long = 10_000) {
        seekTo((currentPositionMs() - ms).coerceAtLeast(0))
    }

    /** Pistas de audio disponibles en el contenido actual */
    fun getAudioTracks(): List<TrackOption> {
        return try {
            val current = vodPlayer.audioTrack
            vodPlayer.audioTracks?.map { track ->
                TrackOption(track.id, track.name ?: "Pista de audio", track.id == current)
            } ?: emptyList()
        } catch (e: Throwable) { emptyList() }
    }

    /** Pistas de subtítulos disponibles en el contenido actual */
    fun getSubtitleTracks(): List<TrackOption> {
        return try {
            val current = vodPlayer.spuTrack
            vodPlayer.spuTracks?.map { track ->
                TrackOption(track.id, track.name ?: "Subtítulo", track.id == current)
            } ?: emptyList()
        } catch (e: Throwable) { emptyList() }
    }

    fun selectTrack(option: TrackOption) {
        try { vodPlayer.setAudioTrack(option.trackId) } catch (e: Throwable) { /* ignorar */ }
    }

    /** Apaga los subtítulos por completo */
    fun disableSubtitles() {
        try { vodPlayer.setSpuTrack(-1) } catch (e: Throwable) { /* ignorar */ }
    }

    fun selectSubtitleTrack(option: TrackOption) {
        try { vodPlayer.setSpuTrack(option.trackId) } catch (e: Throwable) { /* ignorar */ }
    }
}
