package com.jktv.offline.cast

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

@Composable
internal fun TvVideo(uri: String, position: Long, playing: Boolean, brightness: Float) {
    AndroidView(factory = { VideoTexture(it) },
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = brightness.coerceIn(0.1f, 1f) },
        update = { it.update(uri, position, playing) }, onRelease = { it.release() })
}

/** Center-crop using the decoded aspect ratio; never stretches the video to the TV aspect. */
private class VideoTexture(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var uri = ""
    private var prepared = false
    private var position = 0L
    private var playing = false
    private var lastSeek = 0L
    init { surfaceTextureListener = this }
    fun update(next: String, position: Long, playing: Boolean) {
        this.position = position; this.playing = playing
        if (uri != next) { uri = next; open() }
        sync()
    }
    private fun open() {
        player?.release(); player = null; prepared = false
        if (surface == null || uri.isEmpty()) return
        val media = MediaPlayer()
        player = media
        runCatching {
            media.setSurface(surface); media.setDataSource(context, Uri.parse(uri)); media.setVolume(0f, 0f)
            media.isLooping = true
            media.setOnPreparedListener { if (player === it) { prepared = true; crop(); sync() } }
            media.setOnVideoSizeChangedListener { _, _, _ -> crop() }
            media.setOnErrorListener { _, _, _ -> prepared = false; true }
            media.prepareAsync()
        }.onFailure { media.release(); player = null }
    }
    private fun sync() {
        val media = player ?: return
        if (!prepared) return
        runCatching {
            val target = (position % media.duration.coerceAtLeast(1)).toInt()
            val now = android.os.SystemClock.elapsedRealtime()
            if (position >= 0 && abs(media.currentPosition - target) > 300 && now - lastSeek > 500) {
                media.seekTo(target.toLong(), MediaPlayer.SEEK_CLOSEST); lastSeek = now
            }
            if (playing && !media.isPlaying) media.start()
            if (!playing && media.isPlaying) media.pause()
        }
    }
    private fun crop() {
        val media = player ?: return
        if (width <= 0 || height <= 0 || media.videoWidth <= 0 || media.videoHeight <= 0) return
        val ratio = (media.videoWidth.toFloat() / media.videoHeight) / (width.toFloat() / height)
        setTransform(Matrix().apply { setScale(maxOf(1f, ratio), maxOf(1f, 1f / ratio), width / 2f, height / 2f) })
    }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) { surface = Surface(texture); open() }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = crop()
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { release(); return true }
    fun release() { player?.release(); player = null; prepared = false; surface?.release(); surface = null }
}
