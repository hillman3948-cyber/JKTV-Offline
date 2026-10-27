package com.jktv.offline.cast

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jktv.offline.remote.WifiRemoteServer
import com.jktv.offline.ui.NeonStageVisualizer
import com.jktv.offline.ui.STAGE_VISUALIZER_ENABLED
import com.jktv.offline.ui.SyllableKaraokeStage
import com.jktv.offline.ui.resolveLyricPalette

/** This Activity deliberately has no reference to JktvApp or its navigation state. */
class TvReceiverActivity : ComponentActivity() {
    private var receiver: TvReceiver? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immerse()
    }
    // Receiver only advertises while visible; leaving the TV app revokes its audio lease.
    override fun onStart() {
        super.onStart()
        receiver = TvReceiver(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val active = receiver ?: return@MaterialTheme
                val stage by active.state.collectAsState()
                Surface(Modifier.fillMaxSize()) {
                    if (stage.session) TvStage(stage) else PairingScreen(active.pairingKey, stage)
                }
            }
        }
    }
    override fun onStop() { receiver?.close(); receiver = null; super.onStop() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) immerse() }
    private fun immerse() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun PairingScreen(key: String, stage: TvStageState) {
    val address = remember(stage.port) {
        // No server is started; enumerate the active local interfaces for manual pairing.
        java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<java.net.Inet4Address>().firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }?.hostAddress ?: "unavailable"
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF060913)).padding(48.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("JKTV • TV Stage", fontSize = 32.sp)
        Spacer(Modifier.height(24.dp))
        Text("On your phone, open Cast to TV and choose this receiver.", fontSize = 20.sp)
        Text("Both devices must be on the same local network.", fontSize = 18.sp)
        Spacer(Modifier.height(24.dp))
        Text("Pairing key", fontSize = 18.sp)
        Text(key.chunked(4).joinToString(" "), fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Manual address: $address:${stage.port}", fontSize = 18.sp)
        Text(stage.message, fontSize = 16.sp)
    }
}

@Composable
private fun TvStage(stage: TvStageState) {
    val s = stage.library
    val p = stage.playback
    val base = resolveLyricPalette(s.lyricPaletteName)
    fun color(hex: String, fallback: Color) = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
    val palette = if (s.lyricPaletteName == "CUSTOM_PALETTE") base.copy(
        activeFill = color(s.customActiveFillHex, base.activeFill), activeGlow = color(s.customActiveGlowHex, base.activeGlow),
        unsungFill = color(s.customUnsungFillHex, base.unsungFill), nextLineFill = color(s.customNextLineFillHex, base.nextLineFill)) else base
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF060913))) {
        val stageHeight=maxHeight
        val guestQr=s.stageGuestQrEnabled && s.stageGuestJoinUrl.isNotBlank()
        s.backgroundVideoUri?.let { uri ->
            if (s.backgroundIsImage) androidx.compose.ui.viewinterop.AndroidView(
                factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = s.backgroundBrightness },
                update = { if (it.tag != uri) { it.tag = uri; it.setImageURI(android.net.Uri.parse(uri)) } })
            else TvVideo(uri, p.positionMs, p.playing, s.backgroundBrightness)
        }
        if (STAGE_VISUALIZER_ENABLED) {
            NeonStageVisualizer(style = s.visualizerStyle, channelLevels = p.channelLevels, playing = p.playing,
                primaryColor = palette.activeFill, secondaryColor = Color(0xFFFF2A85),
                sensitivity = s.visualizerSensitivity, speedMultiplier = s.visualizerSpeed,
                brightness = s.visualizerBrightness, lyricsSafeZone = s.visualizerLyricsSafeZone,
                glowStrength = s.visualizerGlowStrength, performanceMode = s.visualizerPerformanceMode,
                modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.lyricsScrimAlpha.coerceIn(0f, 0.95f))))
        Box(Modifier.fillMaxSize().padding(horizontal = maxWidth * 0.05f, vertical = maxHeight * 0.05f), contentAlignment = when (s.lyricStagePosition) {
            "TOP" -> Alignment.TopCenter
            "BOTTOM" -> Alignment.BottomCenter
            else -> Alignment.Center
        }) {
            SyllableKaraokeStage(lyricsEnabled = s.lyricsEnabled, countdownEnabled = s.countdownEnabled, wipeEnabled = s.lyricWipeEnabled, singerInfoEnabled = s.singerInfoEnabled, playback = p.copy(interludeSkipTargetMs = null), palette = palette,
                fontScale = s.lyricFontSizeScale, karaokeMode = s.karaokeMode, activeSinger = s.activeSingerName,
                duetPartner = s.activeDuetPartner, onSkipInterlude = {}, twoLineMode = s.twoLineKaraokeLayout,
                displayMode = s.lyricDisplayMode, fontFamilyChoice = s.lyricFontFamily, fontWeightChoice = s.lyricFontWeight,
                letterSpacingSp = s.lyricLetterSpacing, lineSpacingDp = s.lyricLineSpacing,
                textAlignment = s.lyricTextAlignment, stagePosition = s.lyricStagePosition,
                outlineThicknessDp = s.lyricOutlineThickness, shadowIntensity = s.lyricShadowIntensity,
                modifier = Modifier.fillMaxWidth().padding(top=if(guestQr && s.lyricStagePosition=="TOP") stageHeight*.17f else 0.dp))
            if (s.singerInfoEnabled) Text(s.activeSingerName, color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomStart))
            if (s.songInfoEnabled) Text(stage.title, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(if(guestQr) .72f else 1f))
        }
        if(guestQr)com.jktv.offline.remote.GuestStageQr(s.stageGuestJoinUrl,maxHeight*.14f,
            Modifier.align(Alignment.TopEnd).padding(top=maxHeight*.03f,end=maxWidth*.03f))
    }
}

