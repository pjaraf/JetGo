package com.jetgo.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.jetgo.tv.data.model.PlaybackStats

/**
 * Encapsula un único ExoPlayer reutilizable para el panel de reproducción en vivo,
 * exponiendo bitrate/estado como State de Compose para actualizar el overlay
 * (equivalente al indicador "4Kb/s" que se ve en la captura de referencia).
 */
class PlayerManager(context: Context) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _stats = mutableStateOf(PlaybackStats())
    val stats: State<PlaybackStats> get() = _stats

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

    /** Salta a una posición específica (usado para "Seguir viendo") */
    fun seekTo(positionMs: Long) {
        try { exoPlayer.seekTo(positionMs) } catch (e: Exception) { /* ignorar */ }
    }
}
