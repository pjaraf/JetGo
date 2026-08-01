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

    /** Se dispara cuando el contenido actual termina de reproducirse por completo (fin de capítulo/película) */
    var onPlaybackEnded: (() -> Unit)? = null

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
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
        })
    }

    fun playChannel(url: String, name: String) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        val mediaItem = MediaItem.fromUri(url)
        val isHls = url.contains(".m3u8")

        val mediaSource = if (isHls) {
            HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
        } else {
            androidx.media3.exoplayer.source.ProgressiveMediaSource
                .Factory(httpDataSourceFactory)
                .createMediaSource(mediaItem)
        }

        _stats.value = _stats.value.copy(channelName = name, isLive = true)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
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
