package com.jetgo.tv.player

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.jetgo.tv.data.model.PlaybackStats
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
 * Serie — para que cualquier ajuste que se necesite en uno (ej. forzar decodificador por
 * software en algún TV Box puntual) se pueda aplicar sin que le toque nada al otro. Cada uno
 * tiene su propia lógica de reconexión/reintento, así ninguno depende del estado del otro.
 *
 * Reemplaza por completo a Media3/ExoPlayer — no queda ninguna referencia a él en la app.
 */
class PlayerManager(context: Context) {

    /** Motor de VLC — optimizado para TV Boxes y hardware limitado */
    private val libVLC: LibVLC by lazy {
        try {
            LibVLC(
                context,
                arrayListOf(
                    "--network-caching=3000",
                    "--clock-jitter=0",
                    "--clock-synchro=0",
                    "--drop-late-frames",
                    "--skip-frames",
                    "--rtsp-tcp"
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("JetGo_Player", "Error al iniciar LibVLC con opciones personalizadas", e)
            LibVLC(context)
        }
    }

    // =====================================================================================
    // REPRODUCTOR DE VIVO — configuración simple, sin ningún ajuste especial de decodificador.
    // No se debe tocar esta parte para probar cosas nuevas: para eso está el reproductor de
    // Película/Serie de más abajo, que es completamente independiente.
    // =====================================================================================
    val livePlayer: MediaPlayer by lazy { MediaPlayer(libVLC) }

    // =====================================================================================
    // REPRODUCTOR DE PELÍCULA/SERIE — separado por completo del de Vivo. Acá es donde se
    // puede forzar decodificación por software si el hardware de un TV Box puntual falla,
    // sin ningún riesgo de que afecte a Vivo (es otro objeto MediaPlayer distinto).
    // =====================================================================================
    val vodPlayer: MediaPlayer by lazy { MediaPlayer(libVLC) }

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

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Agrupa todo el estado de reconexión/reintento que necesita CADA reproductor por
     *  separado — así el de Vivo y el de Película/Serie nunca se pisan entre sí. */
    private inner class PlayerState(val player: MediaPlayer, val isLive: Boolean) {
        var retryCount = 0
        var bufferingReconnectCount = 0
        var lastUrl: String? = null
        var lastName: String = ""
        var currentUrl: String? = null
        var currentMedia: Media? = null
        var bufferingTimeoutRunnable: Runnable? = null
        /** true mientras VLC avisa que está cargando (buffering < 100%) */
        var isBuffering = false
        /** true una vez que ya se reintentó ESTE contenido forzando decodificación por
         *  software — para no entrar en un bucle si vuelve a fallar igual. */
        var yaForzoSoftware = false

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
                if (isBuffering && bufferingReconnectCount < 4) {
                    bufferingReconnectCount++
                    val url = lastUrl
                    if (url != null) {
                        try {
                            player.stop()
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

    private val liveState = PlayerState(livePlayer, isLive = true)
    private val vodState = PlayerState(vodPlayer, isLive = false)

    init {
        setupPlayer(livePlayer, liveState)
        setupPlayer(vodPlayer, vodState)
    }

    private fun setupPlayer(player: MediaPlayer, state: PlayerState) {
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    state.isBuffering = false
                    _isPlaying.value = true
                    _playbackError.value = null
                    state.retryCount = 0; state.bufferingReconnectCount = 0
                    state.cancelBufferingTimeout()
                }
                MediaPlayer.Event.Paused -> {
                    _isPlaying.value = false
                }
                MediaPlayer.Event.Stopped -> {
                    _isPlaying.value = false
                    state.cancelBufferingTimeout()
                }
                MediaPlayer.Event.EndReached -> {
                    onPlaybackEnded?.invoke()
                }
                MediaPlayer.Event.Buffering -> {
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
                    handlePlaybackError(player, state)
                }
                MediaPlayer.Event.Vout -> {
                    updateVideoQuality(player)
                }
                else -> { /* otros eventos (posición, tiempo, etc.) no necesitan acción acá */ }
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
        } catch (e: Exception) { /* ignorar */ }
    }

    private fun handlePlaybackError(player: MediaPlayer, state: PlayerState) {
        // Si en PELÍCULA/SERIE la reproducción falla y todavía no se probó forzando
        // decodificación por software para este contenido puntual, se reintenta UNA vez así
        // — algunos TV Box (chips MediaTek entre otros) fallan con el decodificador de
        // hardware en ciertos videos, pero funcionan bien por software. Nunca se hace esto
        // de forma preventiva ni en Vivo, solo como reintento reactivo tras una falla real.
        if (!state.isLive && !state.yaForzoSoftware) {
            state.yaForzoSoftware = true
            val url = state.lastUrl
            if (url != null && reloadWithSoftwareDecoder(player, url)) {
                return
            }
        }

        val url = state.lastUrl
        if (url != null && state.retryCount < 2) {
            // Reintenta un par de veces solo (cortes momentáneos de red/servidor),
            // antes de mostrarle un error al usuario.
            state.retryCount++
            try {
                player.play()
            } catch (e: Exception) { /* ignorar, se maneja abajo si vuelve a fallar */ }
        } else {
            _playbackError.value = "No se pudo reproducir \"${state.lastName}\"."
        }
    }

    /** Vuelve a cargar el mismo contenido, esta vez forzando que VLC use un decodificador
     *  por software en vez del de hardware del chip — devuelve true si se pudo armar. */
    private fun reloadWithSoftwareDecoder(player: MediaPlayer, url: String): Boolean {
        return try {
            player.stop()
            val state = if (player == livePlayer) liveState else vodState
            state.currentMedia?.release()
            val media = Media(libVLC, android.net.Uri.parse(url))
            media.addOption(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            media.addOption(":network-caching=3000")
            // Fuerza a NO usar el decodificador de hardware del chip para este contenido.
            media.setHWDecoderEnabled(false, true)
            player.media = media
            state.currentMedia = media
            player.play()
            true
        } catch (e: Exception) {
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
        state.currentUrl = url
        state.lastUrl = url
        state.lastName = name
        state.retryCount = 0; state.bufferingReconnectCount = 0
        state.yaForzoSoftware = false
        state.isBuffering = false
        _playbackError.value = null
        _videoQuality.value = null
        state.cancelBufferingTimeout()

        _stats.value = _stats.value.copy(channelName = name, isLive = isLive)

        val oldMedia = state.currentMedia

        try {
            val media = Media(libVLC, android.net.Uri.parse(url))
            media.addOption(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            media.addOption(":network-caching=3000")
            player.media = media
            state.currentMedia = media
            player.play()
            try { oldMedia?.release() } catch (e: Exception) {}
        } catch (e: Exception) {
            _playbackError.value = "No se pudo reproducir \"$name\"."
        }
    }

    fun release() {
        try { livePlayer.stop() } catch (e: Exception) { /* ignorar */ }
        try { vodPlayer.stop() } catch (e: Exception) { /* ignorar */ }
        liveState.currentMedia?.release()
        vodState.currentMedia?.release()
        livePlayer.release()
        vodPlayer.release()
        libVLC.release()
    }

    /** Pausa ambos reproductores — se usa cuando la app pasa a segundo plano */
    fun pauseAll() {
        try { if (livePlayer.isPlaying) livePlayer.pause() } catch (e: Exception) { /* ignorar */ }
        try { if (vodPlayer.isPlaying) vodPlayer.pause() } catch (e: Exception) { /* ignorar */ }
    }

    /** Reanuda ambos reproductores (el que no tenía nada cargado simplemente no hace nada) */
    fun playAll() {
        try { livePlayer.play() } catch (e: Exception) { /* ignorar */ }
        try { vodPlayer.play() } catch (e: Exception) { /* ignorar */ }
    }

    /** Detiene ambos por completo — usado al cerrar sesión o desconectar */
    fun stopAll() {
        try { livePlayer.stop() } catch (e: Exception) { /* ignorar */ }
        try { vodPlayer.stop() } catch (e: Exception) { /* ignorar */ }
    }

    // ---- Todo lo de acá para abajo es SOLO para Película/Serie (el reproductor de Vivo no
    // usa nada de esto: no tiene controles de avance/retroceso ni pistas seleccionables) ----

    /** Posición actual de reproducción, en milisegundos */
    fun currentPositionMs(): Long = try { vodPlayer.time } catch (e: Exception) { 0L }

    /** Duración total del contenido actual, en milisegundos (0 si aún no se sabe) */
    fun durationMs(): Long = try { vodPlayer.length.coerceAtLeast(0) } catch (e: Exception) { 0L }

    /** Salta a una posición específica (usado para "Seguir viendo" y la barra de progreso) */
    fun seekTo(positionMs: Long) {
        try { vodPlayer.time = positionMs } catch (e: Exception) { /* ignorar */ }
    }

    fun togglePlayPause() {
        try {
            if (vodPlayer.isPlaying) vodPlayer.pause() else vodPlayer.play()
        } catch (e: Exception) { /* ignorar */ }
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
        } catch (e: Exception) { emptyList() }
    }

    /** Pistas de subtítulos disponibles en el contenido actual */
    fun getSubtitleTracks(): List<TrackOption> {
        return try {
            val current = vodPlayer.spuTrack
            vodPlayer.spuTracks?.map { track ->
                TrackOption(track.id, track.name ?: "Subtítulo", track.id == current)
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun selectTrack(option: TrackOption) {
        try { vodPlayer.setAudioTrack(option.trackId) } catch (e: Exception) { /* ignorar */ }
    }

    /** Apaga los subtítulos por completo */
    fun disableSubtitles() {
        try { vodPlayer.setSpuTrack(-1) } catch (e: Exception) { /* ignorar */ }
    }

    fun selectSubtitleTrack(option: TrackOption) {
        try { vodPlayer.setSpuTrack(option.trackId) } catch (e: Exception) { /* ignorar */ }
    }
}
