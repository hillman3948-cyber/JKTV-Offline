package com.jktv.offline.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jktv.offline.data.SongEntity
import com.jktv.offline.playback.AudioMixerConfig
import com.jktv.offline.playback.KaraokeMidiParser
import com.jktv.offline.playback.LyricLine
import com.jktv.offline.playback.LyricSyllable
import com.jktv.offline.playback.LyricWord
import com.jktv.offline.playback.PlaybackState
import com.jktv.offline.playback.SyncConfidence
import com.jktv.offline.playback.SyncSourceType
import com.jktv.offline.remote.QrCodeMatrix
import com.jktv.offline.remote.RemoteServerState
import com.jktv.offline.scoring.FinalScoreCard
import com.jktv.offline.scoring.LiveScoreState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class LyricColorPalette(
    val id: String,
    val label: String,
    val activeFill: Color,
    val activeGlow: Color,
    val unsungFill: Color,
    val nextLineFill: Color,
    val outlineColor: Color = Color.Black,
    val isRainbow: Boolean = false
)

val LyricPalettes = listOf(
    LyricColorPalette(
        id = "CLASSIC_KARAOKE",
        label = "Classic Karaoke (White to Blue)",
        activeFill = Color(0xFF2979FF),
        activeGlow = Color(0xFF00B0FF),
        unsungFill = Color(0xFFFFFFFF),
        nextLineFill = Color(0xFFB0BEC5),
        outlineColor = Color(0xFF001538)
    ),
    LyricColorPalette(
        id = "GOLDEN_PERFORMANCE",
        label = "Golden Performance (White to Gold)",
        activeFill = Color(0xFFFFD700),
        activeGlow = Color(0xFFFF9100),
        unsungFill = Color(0xFFFFFDE7),
        nextLineFill = Color(0xFFCFD8DC),
        outlineColor = Color(0xFF3E2723)
    ),
    LyricColorPalette(
        id = "NEON_PARTY",
        label = "Neon Party (White to Electric Pink)",
        activeFill = Color(0xFFFF1493),
        activeGlow = Color(0xFFE040FB),
        unsungFill = Color(0xFFFFFFFF),
        nextLineFill = Color(0xFFF8BBD0),
        outlineColor = Color(0xFF2A0825)
    ),
    LyricColorPalette(
        id = "OCEAN_WAVE",
        label = "Ocean Wave (Cyan to Deep Blue)",
        activeFill = Color(0xFF00E5FF),
        activeGlow = Color(0xFF2979FF),
        unsungFill = Color(0xFFE0F7FA),
        nextLineFill = Color(0xFF80DEEA),
        outlineColor = Color(0xFF002244)
    ),
    LyricColorPalette(
        id = "FIRE_STAGE",
        label = "Fire Stage (Yellow to Orange/Red)",
        activeFill = Color(0xFFFF3D00),
        activeGlow = Color(0xFFFFD600),
        unsungFill = Color(0xFFFFF9C4),
        nextLineFill = Color(0xFFFFCC80),
        outlineColor = Color(0xFF330B00)
    ),
    LyricColorPalette(
        id = "EMERALD_GLOW",
        label = "Emerald Glow (White to Neon Green)",
        activeFill = Color(0xFF00E676),
        activeGlow = Color(0xFF1DE9B6),
        unsungFill = Color(0xFFE8F5E9),
        nextLineFill = Color(0xFFA5D6A7),
        outlineColor = Color(0xFF002812)
    ),
    LyricColorPalette(
        id = "PURPLE_DREAM",
        label = "Purple Dream (Lavender to Purple)",
        activeFill = Color(0xFFD500F9),
        activeGlow = Color(0xFF7C4DFF),
        unsungFill = Color(0xFFF3E5F5),
        nextLineFill = Color(0xFFCE93D8),
        outlineColor = Color(0xFF240038)
    ),
    LyricColorPalette(
        id = "RAINBOW_KARAOKE",
        label = "Rainbow Karaoke (Animated Rainbow)",
        activeFill = Color(0xFFFF0055),
        activeGlow = Color(0xFF00F0FF),
        unsungFill = Color(0xFFFFFFFF),
        nextLineFill = Color(0xFFFFD54F),
        outlineColor = Color(0xFF110022),
        isRainbow = true
    ),
    LyricColorPalette(
        id = "ICE_CRYSTAL",
        label = "Ice Crystal (White to Ice Blue)",
        activeFill = Color(0xFF80D8FF),
        activeGlow = Color(0xFF00E5FF),
        unsungFill = Color(0xFFF5FDFF),
        nextLineFill = Color(0xFFB3E5FC),
        outlineColor = Color(0xFF001B2E)
    ),
    LyricColorPalette(
        id = "RETRO_KARAOKE",
        label = "Retro Karaoke (Yellow to Red)",
        activeFill = Color(0xFFFF1744),
        activeGlow = Color(0xFFFFEA00),
        unsungFill = Color(0xFFFFF9C4),
        nextLineFill = Color(0xFFFF8A80),
        outlineColor = Color(0xFF3A000A)
    ),
    LyricColorPalette(
        id = "CYBERPUNK",
        label = "Cyberpunk (Cyan to Magenta)",
        activeFill = Color(0xFF00F0FF),
        activeGlow = Color(0xFFFF007F),
        unsungFill = Color(0xFFF0F8FF),
        nextLineFill = Color(0xFF80D8FF),
        outlineColor = Color(0xFF05051E)
    ),
    LyricColorPalette(
        id = "PREMIUM_GOLD",
        label = "Premium Gold (Silver to Metallic Gold)",
        activeFill = Color(0xFFFFC107),
        activeGlow = Color(0xFFFF6F00),
        unsungFill = Color(0xFFECEFF1),
        nextLineFill = Color(0xFFFFE082),
        outlineColor = Color(0xFF2E2200)
    ),
    LyricColorPalette(
        id = "SUNSET_GLOW",
        label = "Sunset Glow (Pink to Orange)",
        activeFill = Color(0xFFFF6D00),
        activeGlow = Color(0xFFFF4081),
        unsungFill = Color(0xFFFCE4EC),
        nextLineFill = Color(0xFFFFAB91),
        outlineColor = Color(0xFF3E1100)
    ),
    LyricColorPalette(
        id = "ELECTRIC_VIOLET",
        label = "Electric Violet (White to Violet)",
        activeFill = Color(0xFF651FFF),
        activeGlow = Color(0xFFB388FF),
        unsungFill = Color(0xFFEDE7F6),
        nextLineFill = Color(0xFFD1C4E9),
        outlineColor = Color(0xFF15003E)
    ),
    LyricColorPalette(
        id = "CUSTOM_PALETTE",
        label = "Custom Palette (User Configured)",
        activeFill = Color(0xFF00F0FF),
        activeGlow = Color(0xFFFFD600),
        unsungFill = Color(0xFFFFFFFF),
        nextLineFill = Color(0xFFB5C7E8),
        outlineColor = Color(0xFF000000)
    )
)

fun resolveLyricPalette(name: String): LyricColorPalette {
    val mappedId = when (name) {
        "CYAN_GOLD" -> "CYBERPUNK"
        "NEON_MAGENTA" -> "NEON_PARTY"
        "EMERALD_CYBER" -> "EMERALD_GLOW"
        "SUNSET_FLAME" -> "FIRE_STAGE"
        else -> name
    }
    return LyricPalettes.firstOrNull { it.id == mappedId } ?: LyricPalettes.first()
}

val VisualizerStyles = listOf(
    "AMBIENT_GLOW" to "Ambient Edge Glow (Soft & Elegant)",
    "LIGHT_RAYS" to "Elegant Light Rays (Distraction-Free)",
    "AUDIO_WAVE" to "Fluid Audio Wave",
    "FLOATING_PARTICLES" to "Floating Music Particles",
    "NEON_EDGE" to "Neon Edge Lighting",
    "CINEMATIC_STAGE" to "Cinematic Ambient Stage",
    "NEON_BARS" to "Neon Spectrum Bars",
    "CIRCULAR_SPECTRUM" to "Circular Audio Spectrum",
    "ELECTRIC_WAVEFORM" to "Electric Waveform",
    "CONCERT_LASERS" to "Concert Laser Lights",
    "RGB_EQUALIZER" to "RGB Equalizer",
    "DIGITAL_TUNNEL" to "Futuristic Digital Tunnel",
    "GALAXY_PARTICLES" to "Galaxy Audio Particles",
    "CYBERPUNK_GRID" to "Cyberpunk Neon Grid",
    "DANCING_BEAMS" to "Dancing Light Beams",
    "DYNAMIC_SPOTLIGHT" to "Dynamic Spotlight Stage",
    "AURORA_WAVES" to "Aurora Waves",
    "HOLOGRAPHIC_RINGS" to "Holographic Audio Rings",
    "BASS_PULSE_GLOW" to "Bass Pulse Glow",
    "RETRO_DISCO" to "Retro Disco Equalizer",
    "RAINBOW_ENERGY" to "Rainbow Energy Waves",
    "LIGHTNING_PULSE" to "Electric Lightning Pulse",
    "SPECTRUM_3D" to "3D-Style Audio Spectrum",
    "CONCERT_STAGE" to "Premium Concert Stage",
    "CUSTOM_VISUALIZER" to "Custom Visualizer"
)

fun resolveFontFamily(name: String, weightChoice: String = "BLACK"): FontFamily {
    val weightVal = when (weightChoice.uppercase()) {
        "LIGHT" -> 300
        "NORMAL" -> 400
        "MEDIUM" -> 500
        "SEMI_BOLD" -> 600
        "BOLD" -> 700
        "EXTRA_BOLD" -> 800
        "BLACK" -> 900
        else -> 900
    }
    val isBold = weightVal >= 700

    val typeface = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        val base = when (name.uppercase()) {
            "SERIF" -> android.graphics.Typeface.SERIF
            "MONOSPACE" -> android.graphics.Typeface.MONOSPACE
            "CONDENSED" -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
            "CASUAL", "CURSIVE" -> android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL)
            else -> android.graphics.Typeface.SANS_SERIF
        }
        android.graphics.Typeface.create(base, weightVal, false)
    } else {
        val style = if (isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        when (name.uppercase()) {
            "SERIF" -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, style)
            "MONOSPACE" -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, style)
            "CONDENSED" -> android.graphics.Typeface.create("sans-serif-condensed", style)
            "CASUAL", "CURSIVE" -> android.graphics.Typeface.create("casual", style)
            else -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, style)
        }
    }
    return FontFamily(typeface)
}

fun resolveFontFamily(name: String): FontFamily = resolveFontFamily(name, "BLACK")

fun resolveFontWeight(name: String): FontWeight = when (name.uppercase()) {
    "LIGHT" -> FontWeight.Light
    "NORMAL" -> FontWeight.Normal
    "MEDIUM" -> FontWeight.Medium
    "SEMI_BOLD" -> FontWeight.SemiBold
    "BOLD" -> FontWeight.Bold
    "EXTRA_BOLD" -> FontWeight.ExtraBold
    "BLACK" -> FontWeight.Black
    else -> FontWeight.Black
}

/** Feature flag: set to true to re-enable the Stage Visualizer menu and rendering. */
const val STAGE_VISUALIZER_ENABLED = false

/**
 * 25 High-Performance Animated Procedural Stage Visualizers.
 * Driven by actual 16-channel MIDI playback energy or synthetic harmonic beats when idle/previewing.
 * Includes a dedicated Lyrics Safe Zone to prevent background visual competition with lyrics.
 */
@Composable
fun NeonStageVisualizer(
    style: String,
    channelLevels: List<Float>,
    playing: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    sensitivity: Float = 1.0f,
    speedMultiplier: Float = 1.0f,
    brightness: Float = 1.0f,
    lyricsSafeZone: Boolean = true,
    safeZoneIntensity: Float = 0.85f,
    glowStrength: Float = 1.0f,
    performanceMode: Boolean = false
) {
    // Stage Visualizer feature is disabled globally (phone stage, previews and TV cast).
    if (!STAGE_VISUALIZER_ENABLED) return
    val infinite = rememberInfiniteTransition(label = "stage_vis")
    val duration = (5000 / speedMultiplier.coerceIn(0.5f, 2.5f)).toInt()
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val hasRealAudio = playing && channelLevels.any { it > 0.01f }
        val alphaBase = brightness.coerceIn(0.1f, 1.0f)

        fun getLevel(i: Int): Float {
            val raw = if (hasRealAudio) channelLevels.getOrElse(i % 16) { 0f }
            else (sin(phase * 1.8f + i * 0.45f) * 0.22f + 0.32f).coerceIn(0.05f, 1f)
            return (raw * sensitivity).coerceIn(0.04f, 1.0f)
        }

        val avgEnergy = (0 until 16).sumOf { getLevel(it).toDouble() }.toFloat() / 16f
        val resolvedStyle = when (style) {
            "CYBER_ORBIT" -> "CIRCULAR_SPECTRUM"
            "STARFIELD" -> "GALAXY_PARTICLES"
            "MINIMAL" -> "BASS_PULSE_GLOW"
            else -> style
        }

        when (resolvedStyle) {
            "AMBIENT_GLOW" -> {
                // Soft edge glow around screen borders & corners (lyrics center remains pure and calm)
                val glowAlpha = (0.42f * alphaBase * (0.75f + 0.25f * avgEnergy) * glowStrength).coerceIn(0f, 1f)
                // Top-Left corner glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = w * 0.45f
                    ),
                    center = Offset(0f, 0f),
                    radius = w * 0.45f
                )
                // Top-Right corner glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryColor.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(w, 0f),
                        radius = w * 0.45f
                    ),
                    center = Offset(w, 0f),
                    radius = w * 0.45f
                )
                // Bottom edge glow wash
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, primaryColor.copy(alpha = glowAlpha * 0.85f)),
                        startY = h * 0.75f,
                        endY = h
                    ),
                    topLeft = Offset(0f, h * 0.75f),
                    size = Size(w, h * 0.25f)
                )
            }
            "LIGHT_RAYS" -> {
                // Smooth moving stage light beams from top corners sweeping outward (away from lyrics)
                val rayAlpha = (0.35f * alphaBase * (0.8f + 0.4f * avgEnergy) * glowStrength).coerceIn(0f, 0.85f)
                val sweepL = sin(phase * 1.2f) * (w * 0.12f)
                val sweepR = cos(phase * 1.2f) * (w * 0.12f)

                // Left beam
                val pathL = Path().apply {
                    moveTo(w * 0.05f, 0f)
                    lineTo(w * 0.12f, 0f)
                    lineTo((w * 0.35f + sweepL).coerceAtMost(w * 0.40f), h)
                    lineTo((-w * 0.10f + sweepL), h)
                    close()
                }
                drawPath(
                    pathL,
                    brush = Brush.verticalGradient(
                        listOf(primaryColor.copy(alpha = rayAlpha), Color.Transparent)
                    )
                )

                // Right beam
                val pathR = Path().apply {
                    moveTo(w * 0.88f, 0f)
                    lineTo(w * 0.95f, 0f)
                    lineTo(w * 1.10f + sweepR, h)
                    lineTo((w * 0.65f + sweepR).coerceAtLeast(w * 0.60f), h)
                    close()
                }
                drawPath(
                    pathR,
                    brush = Brush.verticalGradient(
                        listOf(secondaryColor.copy(alpha = rayAlpha), Color.Transparent)
                    )
                )
            }
            "AUDIO_WAVE" -> {
                // Clean fluid dual audio wave along the bottom
                val waveY = h * 0.88f
                val waveAlpha = (0.75f * alphaBase * glowStrength).coerceIn(0f, 1f)
                val path = Path()
                path.moveTo(0f, waveY)
                val steps = if (performanceMode) 16 else 32
                val dx = w / steps
                for (s in 0..steps) {
                    val lvl = getLevel(s % 16)
                    val dy = sin(phase * 2.0f + s * 0.4f) * (18.dp.toPx() * lvl)
                    path.lineTo(s * dx, waveY + dy)
                }
                drawPath(
                    path,
                    color = primaryColor.copy(alpha = waveAlpha),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                val path2 = Path()
                path2.moveTo(0f, waveY + 4.dp.toPx())
                for (s in 0..steps) {
                    val lvl = getLevel((s + 4) % 16)
                    val dy = cos(phase * 1.8f + s * 0.45f) * (14.dp.toPx() * lvl)
                    path2.lineTo(s * dx, waveY + 4.dp.toPx() + dy)
                }
                drawPath(
                    path2,
                    color = secondaryColor.copy(alpha = waveAlpha * 0.7f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            "NEON_EDGE" -> {
                // Premium neon perimeter frame pulsing smoothly with music
                val edgeAlpha = (0.65f * alphaBase * (0.75f + 0.35f * avgEnergy) * glowStrength).coerceIn(0f, 1f)
                val inset = 8.dp.toPx()
                val cornerR = 16.dp.toPx()
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(primaryColor.copy(alpha = edgeAlpha * 0.4f), secondaryColor.copy(alpha = edgeAlpha * 0.4f))
                    ),
                    topLeft = Offset(inset - 2f, inset - 2f),
                    size = Size(w - (inset - 2f) * 2, h - (inset - 2f) * 2),
                    cornerRadius = CornerRadius(cornerR + 2f, cornerR + 2f),
                    style = Stroke(width = 6.dp.toPx())
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(primaryColor.copy(alpha = edgeAlpha), secondaryColor.copy(alpha = edgeAlpha))
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(w - inset * 2, h - inset * 2),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
            "CINEMATIC_STAGE" -> {
                // Soft atmospheric stage lighting with gentle corner spots
                val stageAlpha = (0.40f * alphaBase * (0.8f + 0.3f * avgEnergy) * glowStrength).coerceIn(0f, 0.85f)
                drawLine(
                    color = Color.White.copy(alpha = 0.25f * alphaBase),
                    start = Offset(0f, 12.dp.toPx()),
                    end = Offset(w, 12.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(primaryColor.copy(alpha = stageAlpha), Color.Transparent),
                        center = Offset(w * 0.15f, h * 0.95f),
                        radius = w * 0.35f
                    ),
                    center = Offset(w * 0.15f, h * 0.95f),
                    radius = w * 0.35f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(secondaryColor.copy(alpha = stageAlpha), Color.Transparent),
                        center = Offset(w * 0.85f, h * 0.95f),
                        radius = w * 0.35f
                    ),
                    center = Offset(w * 0.85f, h * 0.95f),
                    radius = w * 0.35f
                )
            }
            "FLOATING_PARTICLES" -> {
                // Serene floating music particles along screen flanks (away from center lyrics)
                val particleCount = if (performanceMode) 14 else 26
                val partAlpha = (0.60f * alphaBase * glowStrength).coerceIn(0f, 1f)
                for (i in 0 until particleCount) {
                    val lvl = getLevel(i % 16)
                    val isLeft = (i % 2 == 0)
                    val baseX = if (isLeft) w * 0.10f else w * 0.90f
                    val xSpread = (sin(phase + i * 1.3f) * (w * 0.08f))
                    val x = baseX + xSpread
                    val speed = 0.35f + (i % 5) * 0.12f
                    val progressY = ((phase * speed + (i * 0.23f)) % (2f * PI).toFloat()) / (2f * PI).toFloat()
                    val y = h - (progressY * h)
                    val radius = (3.dp.toPx() + (lvl * 4.dp.toPx()))

                    val col = if (i % 3 == 0) secondaryColor else primaryColor
                    drawCircle(
                        color = col.copy(alpha = partAlpha * (0.3f + 0.7f * lvl)),
                        radius = radius,
                        center = Offset(x, y)
                    )
                }
            }
            "NEON_BARS" -> {
                val barCount = 16
                val totalPadding = w * 0.10f
                val usableW = w - totalPadding
                val slotW = usableW / barCount
                val barW = slotW * 0.68f
                val startX = totalPadding / 2f
                val maxBarH = h * 0.38f
                for (i in 0 until barCount) {
                    val lvl = getLevel(i)
                    val barH = maxBarH * lvl
                    val x = startX + i * slotW
                    val y = h - barH - 8.dp.toPx()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.85f * alphaBase),
                                secondaryColor.copy(alpha = 0.45f * alphaBase),
                                Color.Transparent
                            ),
                            startY = y,
                            endY = h
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    // Peak indicator dot
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f * alphaBase),
                        radius = 2.5.dp.toPx(),
                        center = Offset(x + barW / 2f, y - 4.dp.toPx())
                    )
                }
            }
            "CIRCULAR_SPECTRUM" -> {
                val center = Offset(w * 0.5f, h * 0.48f)
                val baseRadius = minOf(w, h) * (0.20f + avgEnergy * 0.14f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.30f * alphaBase + avgEnergy * 0.3f),
                            secondaryColor.copy(alpha = 0.10f * alphaBase),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * 2.2f
                    ),
                    radius = baseRadius * 2.2f,
                    center = center
                )
                for (ring in 1..3) {
                    val r = baseRadius * (0.65f + ring * 0.35f)
                    drawCircle(
                        color = if (ring % 2 == 1) primaryColor.copy(alpha = 0.35f * alphaBase) else secondaryColor.copy(alpha = 0.25f * alphaBase),
                        radius = r,
                        center = center,
                        style = Stroke(width = (3.5f - ring * 0.7f).dp.toPx())
                    )
                }
                for (i in 0 until 24) {
                    val lvl = getLevel(i % 16)
                    val angle = phase + (i * PI * 2.0 / 24.0).toFloat()
                    val distInner = baseRadius * 0.9f
                    val distOuter = baseRadius * (1.1f + lvl * 0.65f)
                    drawLine(
                        color = if (i % 2 == 0) primaryColor.copy(alpha = 0.9f * alphaBase) else secondaryColor.copy(alpha = 0.9f * alphaBase),
                        start = Offset(center.x + cos(angle) * distInner, center.y + sin(angle) * distInner),
                        end = Offset(center.x + cos(angle) * distOuter, center.y + sin(angle) * distOuter),
                        strokeWidth = (3f + lvl * 4f).dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            "ELECTRIC_WAVEFORM" -> {
                val wavePath = Path()
                val midY = h * 0.5f
                val points = 32
                for (i in 0..points) {
                    val x = (w * i) / points
                    val lvl = getLevel(i % 16)
                    val waveAmp = (h * 0.18f) * lvl
                    val y = midY + sin(phase * 3f + i * 0.35f) * waveAmp
                    if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                }
                drawPath(
                    path = wavePath,
                    color = primaryColor.copy(alpha = 0.85f * alphaBase),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = wavePath,
                    color = secondaryColor.copy(alpha = 0.35f * alphaBase),
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            "CONCERT_LASERS" -> {
                val laserCount = 8
                for (i in 0 until laserCount) {
                    val lvl = getLevel(i * 2)
                    val sweepAngle = sin(phase * 1.5f + i * 0.6f) * 0.6f
                    val startLeft = Offset(0f, 0f)
                    val endLeft = Offset(w * (0.3f + sweepAngle + i * 0.08f), h)
                    drawLine(
                        color = primaryColor.copy(alpha = (0.35f + lvl * 0.55f) * alphaBase),
                        start = startLeft,
                        end = endLeft,
                        strokeWidth = (2f + lvl * 3.5f).dp.toPx()
                    )
                    val startRight = Offset(w, 0f)
                    val endRight = Offset(w * (0.7f - sweepAngle - i * 0.08f), h)
                    drawLine(
                        color = secondaryColor.copy(alpha = (0.35f + lvl * 0.55f) * alphaBase),
                        start = startRight,
                        end = endRight,
                        strokeWidth = (2f + lvl * 3.5f).dp.toPx()
                    )
                }
            }
            "RGB_EQUALIZER" -> {
                val colCount = 12
                val slotW = w / (colCount + 1)
                val blockW = slotW * 0.72f
                val blockH = 8.dp.toPx()
                val gap = 4.dp.toPx()
                for (col in 0 until colCount) {
                    val lvl = getLevel(col)
                    val maxBlocks = 14
                    val activeBlocks = (maxBlocks * lvl).toInt().coerceIn(1, maxBlocks)
                    val x = slotW * 0.5f + col * slotW
                    for (b in 0 until activeBlocks) {
                        val y = h - (b + 1) * (blockH + gap) - 10.dp.toPx()
                        val color = when {
                            b > 10 -> Color(0xFFFF2A2A)
                            b > 6 -> Color(0xFFFFD600)
                            else -> Color(0xFF00FF9D)
                        }
                        drawRoundRect(
                            color = color.copy(alpha = 0.9f * alphaBase),
                            topLeft = Offset(x, y),
                            size = Size(blockW, blockH),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
            }
            "DIGITAL_TUNNEL" -> {
                val center = Offset(w * 0.5f, h * 0.5f)
                val ringCount = 6
                for (r in 0 until ringCount) {
                    val progress = ((phase / (2f * PI) + r.toFloat() / ringCount) % 1.0f).toFloat()
                    val radiusX = (w * 0.65f) * progress
                    val radiusY = (h * 0.55f) * progress
                    val lvl = getLevel(r)
                    drawRoundRect(
                        color = if (r % 2 == 0) primaryColor.copy(alpha = progress * 0.7f * alphaBase) else secondaryColor.copy(alpha = progress * 0.7f * alphaBase),
                        topLeft = Offset(center.x - radiusX / 2f, center.y - radiusY / 2f),
                        size = Size(radiusX, radiusY),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = (1.5f + lvl * 3.5f).dp.toPx())
                    )
                }
            }
            "GALAXY_PARTICLES" -> {
                for (i in 0 until 36) {
                    val seed = (i * 73 + 19) % 100 / 100f
                    val x = ((seed * w) + cos(phase + i) * 32f).coerceIn(0f, w)
                    val y = (((i * 37 % 100) / 100f * h + phase / (2f * PI.toFloat()) * h) % h)
                    val lvl = getLevel(i % 16)
                    drawCircle(
                        color = if (i % 3 == 0) primaryColor.copy(alpha = 0.6f * alphaBase) else secondaryColor.copy(alpha = 0.5f * alphaBase),
                        radius = (2.5f + lvl * 8f).dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            "CYBERPUNK_GRID" -> {
                val horizonY = h * 0.55f
                // Horizon Glow
                drawLine(
                    color = primaryColor.copy(alpha = 0.75f * alphaBase),
                    start = Offset(0f, horizonY),
                    end = Offset(w, horizonY),
                    strokeWidth = 3.dp.toPx()
                )
                // Perspective Grid Lines
                for (i in 0..10) {
                    val bottomX = (w * i) / 10f
                    val topX = (w * 0.5f) + (bottomX - w * 0.5f) * 0.15f
                    drawLine(
                        color = secondaryColor.copy(alpha = 0.4f * alphaBase),
                        start = Offset(topX, horizonY),
                        end = Offset(bottomX, h),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
                // Horizontal scan lines with audio ripple
                for (j in 1..6) {
                    val lvl = getLevel(j)
                    val y = horizonY + (h - horizonY) * (j * j / 36f)
                    drawLine(
                        color = primaryColor.copy(alpha = (0.25f + lvl * 0.45f) * alphaBase),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = (1.5f + lvl * 2f).dp.toPx()
                    )
                }
            }
            "DANCING_BEAMS" -> {
                val beamCount = 5
                for (i in 0 until beamCount) {
                    val lvl = getLevel(i * 3)
                    val originX = w * (0.2f + i * 0.15f)
                    val targetX = w * (0.5f + sin(phase * 2f + i * 1.2f) * 0.45f)
                    val beamPath = Path().apply {
                        moveTo(originX - 12f, 0f)
                        lineTo(originX + 12f, 0f)
                        lineTo(targetX + 60f + lvl * 40f, h)
                        lineTo(targetX - 60f - lvl * 40f, h)
                        close()
                    }
                    drawPath(
                        path = beamPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (i % 2 == 0) primaryColor.copy(alpha = 0.45f * alphaBase) else secondaryColor.copy(alpha = 0.45f * alphaBase),
                                Color.Transparent
                            )
                        )
                    )
                }
            }
            "DYNAMIC_SPOTLIGHT" -> {
                val center = Offset(w * 0.5f, h * 0.5f)
                val spotRadius = (minOf(w, h) * 0.35f) * (0.85f + avgEnergy * 0.35f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = (0.45f + avgEnergy * 0.4f) * alphaBase),
                            secondaryColor.copy(alpha = 0.15f * alphaBase),
                            Color.Transparent
                        ),
                        center = center,
                        radius = spotRadius
                    ),
                    radius = spotRadius,
                    center = center
                )
            }
            "AURORA_WAVES" -> {
                for (wave in 0..2) {
                    val path = Path()
                    val baseY = h * (0.35f + wave * 0.14f)
                    val waveColor = if (wave == 0) primaryColor else if (wave == 1) secondaryColor else Color(0xFF00FFD5)
                    path.moveTo(0f, baseY)
                    for (x in 0..w.toInt() step 30) {
                        val lvl = getLevel((x / 60) % 16)
                        val y = baseY + sin(phase * 1.8f + x * 0.012f + wave) * (35f + lvl * 45f)
                        path.lineTo(x.toFloat(), y)
                    }
                    path.lineTo(w, h)
                    path.lineTo(0f, h)
                    path.close()
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                waveColor.copy(alpha = 0.35f * alphaBase),
                                Color.Transparent
                            ),
                            startY = baseY - 40f,
                            endY = baseY + 120f
                        )
                    )
                }
            }
            "HOLOGRAPHIC_RINGS" -> {
                val center = Offset(w * 0.5f, h * 0.5f)
                for (i in 1..5) {
                    val lvl = getLevel(i * 2)
                    val r = (i * 32.dp.toPx()) * (0.9f + lvl * 0.35f)
                    drawCircle(
                        color = primaryColor.copy(alpha = (0.2f + (5 - i) * 0.12f) * alphaBase),
                        radius = r,
                        center = center,
                        style = Stroke(width = (2f + lvl * 3f).dp.toPx())
                    )
                }
            }
            "BASS_PULSE_GLOW" -> {
                val pulse = (avgEnergy * 1.5f).coerceIn(0f, 1f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = pulse * 0.35f * alphaBase),
                            secondaryColor.copy(alpha = pulse * 0.55f * alphaBase)
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = maxOf(w, h) * 0.65f
                    )
                )
            }
            "RETRO_DISCO" -> {
                val cols = 6
                val rows = 4
                val cellW = w / cols
                val cellH = (h * 0.4f) / rows
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val lvl = getLevel((r * cols + c) % 16)
                        val color = when ((r + c + (phase * 2f).toInt()) % 4) {
                            0 -> primaryColor
                            1 -> secondaryColor
                            2 -> Color(0xFFFFD600)
                            else -> Color(0xFF00FFD5)
                        }
                        drawRoundRect(
                            color = color.copy(alpha = (0.15f + lvl * 0.65f) * alphaBase),
                            topLeft = Offset(c * cellW + 3f, h - (rows - r) * cellH + 3f),
                            size = Size(cellW - 6f, cellH - 6f),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }
            "RAINBOW_ENERGY" -> {
                val rainbowColors = listOf(
                    Color.Red, Color(0xFFFF7F00), Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color(0xFF8B00FF)
                )
                val waveCount = 5
                for (wIdx in 0 until waveCount) {
                    val path = Path()
                    val yAnchor = h * (0.3f + wIdx * 0.12f)
                    path.moveTo(0f, yAnchor)
                    for (x in 0..w.toInt() step 20) {
                        val lvl = getLevel((x / 50) % 16)
                        val y = yAnchor + sin(phase * 2.5f + x * 0.015f + wIdx * 0.8f) * (20f + lvl * 30f)
                        path.lineTo(x.toFloat(), y)
                    }
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(rainbowColors.map { it.copy(alpha = 0.65f * alphaBase) }),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            "LIGHTNING_PULSE" -> {
                if (avgEnergy > 0.25f || !hasRealAudio) {
                    val arcPath = Path()
                    var curPt = Offset(w * 0.15f, h * 0.5f)
                    arcPath.moveTo(curPt.x, curPt.y)
                    for (seg in 1..8) {
                        val nextX = w * (0.15f + seg * 0.09f)
                        val jitterY = sin(phase * 6f + seg * 1.8f) * (h * 0.25f * avgEnergy)
                        val nextY = h * 0.5f + jitterY
                        curPt = Offset(nextX, nextY)
                        arcPath.lineTo(curPt.x, curPt.y)
                    }
                    drawPath(
                        path = arcPath,
                        color = Color.White.copy(alpha = 0.95f * alphaBase),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = arcPath,
                        color = primaryColor.copy(alpha = 0.45f * alphaBase),
                        style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            "SPECTRUM_3D" -> {
                val barCount = 12
                val slotW = w / (barCount + 2)
                for (i in 0 until barCount) {
                    val lvl = getLevel(i)
                    val barH = h * 0.35f * lvl
                    val x = slotW * (i + 1)
                    val y = h - barH - 12.dp.toPx()
                    val barW = slotW * 0.7f
                    // Front Face
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(primaryColor.copy(alpha = 0.8f * alphaBase), primaryColor.copy(alpha = 0.2f * alphaBase))
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barW, barH)
                    )
                    // Top 3D cap
                    val capPath = Path().apply {
                        moveTo(x, y)
                        lineTo(x + 10f, y - 8f)
                        lineTo(x + barW + 10f, y - 8f)
                        lineTo(x + barW, y)
                        close()
                    }
                    drawPath(capPath, color = secondaryColor.copy(alpha = 0.9f * alphaBase))
                }
            }
            "CONCERT_STAGE" -> {
                // Overhead Truss Line
                drawLine(
                    color = Color.White.copy(alpha = 0.4f * alphaBase),
                    start = Offset(0f, 15.dp.toPx()),
                    end = Offset(w, 15.dp.toPx()),
                    strokeWidth = 3.dp.toPx()
                )
                // Stage floor wash
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, primaryColor.copy(alpha = 0.35f * alphaBase * avgEnergy)),
                        startY = h * 0.65f,
                        endY = h
                    ),
                    topLeft = Offset(0f, h * 0.65f),
                    size = Size(w, h * 0.35f)
                )
            }
            else -> {
                // CUSTOM_VISUALIZER: Balanced blend of neon bars and orbit particles
                val count = 16
                val slotW = w / count
                for (i in 0 until count) {
                    val lvl = getLevel(i)
                    val barH = (h * 0.3f) * lvl
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(primaryColor.copy(alpha = 0.75f * alphaBase), secondaryColor.copy(alpha = 0.3f * alphaBase))
                        ),
                        topLeft = Offset(i * slotW + 2f, h - barH - 10.dp.toPx()),
                        size = Size(slotW - 4f, barH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        if (lyricsSafeZone) {
            val safeTop = h * 0.28f
            val safeBottom = h * 0.72f
            val safeHeight = safeBottom - safeTop
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF040816).copy(alpha = (0.50f * safeZoneIntensity).coerceIn(0f, 0.85f)),
                        Color(0xFF040816).copy(alpha = (0.65f * safeZoneIntensity).coerceIn(0f, 0.85f)),
                        Color(0xFF040816).copy(alpha = (0.50f * safeZoneIntensity).coerceIn(0f, 0.85f)),
                        Color.Transparent
                    ),
                    startY = safeTop - 20.dp.toPx(),
                    endY = safeBottom + 20.dp.toPx()
                ),
                topLeft = Offset(0f, safeTop - 20.dp.toPx()),
                size = Size(w, safeHeight + 40.dp.toPx())
            )
        }
    }
}

/**
 * Renders an unsung or upcoming karaoke line with matching typography, outline, and text shadow.
 */
@Composable
fun UnsungStaticLine(
    text: String,
    color: Color,
    fontSizeSp: Float,
    fontFamilyChoice: String = "DEFAULT",
    fontWeightChoice: String = "BLACK",
    letterSpacingSp: Float = 0f,
    outlineThicknessDp: Float = 1.5f,
    shadowIntensity: Float = 1.0f,
    textAlignment: String = "CENTER",
    modifier: Modifier = Modifier,
    outlineColor: Color = Color.Black
) {
    val fFamily = resolveFontFamily(fontFamilyChoice, fontWeightChoice)
    val fWeight = resolveFontWeight(fontWeightChoice)
    val textAlign = if (textAlignment == "LEFT") TextAlign.Start else TextAlign.Center

    val baseStyle = TextStyle(
        fontSize = fontSizeSp.sp,
        fontWeight = fWeight,
        fontFamily = fFamily,
        letterSpacing = letterSpacingSp.sp,
        textAlign = textAlign,
        shadow = if (shadowIntensity > 0f) {
            Shadow(
                color = Color.Black.copy(alpha = (0.45f * shadowIntensity).coerceIn(0f, .65f)),
                offset = Offset(0f, 1f),
                blurRadius = (2f * shadowIntensity).coerceIn(0f, 4f)
            )
        } else null
    )

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = if (textAlignment == "LEFT") Alignment.CenterStart else Alignment.Center) {
        if (outlineThicknessDp > 0f) {
            // Outline shadow pass
            Text(
                text = text,
                color = outlineColor,
                style = baseStyle.copy(
                    shadow = null,
                    drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = outlineThicknessDp * androidx.compose.ui.platform.LocalDensity.current.density)
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = text,
            color = color,
            style = baseStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Syllable-by-syllable color-wiping karaoke stage display.
 * Provides permanently rock-solid lyrics positioning (countdown & interlude indicators are in an
 * independent overlay layer and NEVER shift or reflow lyrics), 1-Line / 2-Line / 4-Line display modes,
 * customizable typography, and precision monotonic audio clock synchronization.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyllableKaraokeStage(
    playback: PlaybackState, palette: LyricColorPalette, fontScale: Float, karaokeMode: String,
    activeSinger: String, duetPartner: String, onSkipInterlude: () -> Unit,
    twoLineMode: Boolean = true, displayMode: String = if (twoLineMode) "TWO_LINE" else "FOUR_LINE",
    fontFamilyChoice: String = "DEFAULT", fontWeightChoice: String = "BLACK",
    letterSpacingSp: Float = 0f, lineSpacingDp: Float = 14f, textAlignment: String = "CENTER",
    stagePosition: String = "CENTER", outlineThicknessDp: Float = 1.5f, shadowIntensity: Float = 1f,
    onOpenSyncEditor: (() -> Unit)? = null, lyricsEnabled: Boolean = true, countdownEnabled: Boolean = true,
    wipeEnabled: Boolean = true, singerInfoEnabled: Boolean = true, modifier: Modifier = Modifier
) {
    val latest by androidx.compose.runtime.rememberUpdatedState(playback)
    var position by remember { mutableLongStateOf(playback.syncedLyricPositionMs()) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { position = latest.syncedLyricPositionMs(it) } } }
    val lines = playback.lyricLines
    val active = findActiveLineIndex(lines, position)
    val mode = displayMode.ifBlank { if (twoLineMode) "TWO_LINE" else "FOUR_LINE" }
    val slots = when (mode) {
        "ONE_LINE" -> listOf(active)
        "TWO_LINE" -> if (active % 2 == 0) listOf(active, active + 1) else listOf(active + 1, active)
        else -> listOf(active, active + 1, active + 2, active + 3)
    }
    val longest = remember(lines) { lines.maxOfOrNull { it.text.length } ?: 1 }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val base = (25f * fontScale * if (maxWidth.value > 720) 1.12f else 1f)
        val size = minOf(base, (maxWidth.value - 32f) / (longest.coerceAtLeast(1) * .65f)).coerceIn(12f, 58f)
        val block = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 16.dp)
        if (lyricsEnabled) Column(block,
            horizontalAlignment = if (textAlignment == "LEFT") Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(lineSpacingDp.dp)) {
            if (lines.isEmpty()) {
                Text(if (playback.file == null) "SELECT A SONG TO LIGHT UP THE STAGE" else "INSTRUMENTAL — NO TIMED LYRICS",
                    color = palette.unsungFill, fontSize = size.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (playback.file != null && onOpenSyncEditor != null) OutlinedButton(onClick = onOpenSyncEditor) { Text("Lyrics Sync Editor") }
            } else slots.forEach { index ->
                val line = lines.getOrNull(index)
                Box(Modifier.fillMaxWidth().heightIn(min = (size * 1.45f).dp), contentAlignment = Alignment.Center) {
                    if (line != null && index == active && position in line.startMs..line.endMs) {
                        SyllableWipeLine(line, position, palette, size, fontFamilyChoice, fontWeightChoice,
                            letterSpacingSp, outlineThicknessDp, shadowIntensity, textAlignment, wipeEnabled = wipeEnabled)
                    } else if (line != null) UnsungStaticLine(line.text,
                        if (index == active) palette.unsungFill else palette.nextLineFill, size, fontFamilyChoice,
                        fontWeightChoice, letterSpacingSp, outlineThicknessDp, shadowIntensity, textAlignment, outlineColor = palette.outlineColor)
                }
            }
        }
        if (singerInfoEnabled && karaokeMode == "DUET") {
            Text(if (lines.getOrNull(active)?.index?.rem(2) == 1) duetPartner.ifBlank { "Partner" } else activeSinger,
                color = palette.nextLineFill, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart))
        }
        if (countdownEnabled) {
            val current = lines.getOrNull(active)
            val next = lines.getOrNull(active + 1)
            val vocal = when {
                current != null && current.startMs - position in 200L..3800L -> current.startMs
                next != null && current != null && position > current.endMs && next.startMs - current.endMs >= 3500 &&
                    next.startMs - position in 200L..3800L -> next.startMs
                else -> null
            }
            val skip = playback.interludeSkipTargetMs
            Row(Modifier.align(Alignment.TopCenter).height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vocal != null) Text("READY IN " + kotlin.math.ceil((vocal-position)/1000.0).toInt(),
                    color = palette.activeGlow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                else if (skip != null) OutlinedButton(onClick = onSkipInterlude) { Text("Skip instrumental") }
            }
        }
    }
}

private fun findActiveLineIndex(lines: List<LyricLine>, posMs: Long): Int =
    com.jktv.offline.stage.StageTimeline.activeLineIndex(lines, posMs)

private fun adaptiveFontSizeForLine(charCount: Int, baseSp: Float): Float {
    val lengthScale = when {
        charCount > 38 -> 0.84f
        charCount > 32 -> 0.90f
        charCount > 26 -> 0.95f
        else -> 1.0f
    }
    return (baseSp * lengthScale).coerceIn(16f, 44f)
}

/**
 * Renders a synchronized karaoke line where reconstructed [LyricWord]s are kept intact as
 * single contiguous word views, with horizontal syllable-by-syllable wipe and typography support.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyllableWipeLine(
    line: LyricLine,
    positionMs: Long,
    palette: LyricColorPalette,
    fontSizeSp: Float,
    fontFamilyChoice: String = "DEFAULT",
    fontWeightChoice: String = "BLACK",
    letterSpacingSp: Float = 0f,
    outlineThicknessDp: Float = 1.5f,
    shadowIntensity: Float = 1.0f,
    textAlignment: String = "CENTER",
    modifier: Modifier = Modifier,
    wipeEnabled: Boolean = true
) {
    if (line.syllables.isEmpty()) {
        Text(
            text = line.text,
            color = palette.activeFill,
            fontSize = fontSizeSp.sp,
            fontWeight = resolveFontWeight(fontWeightChoice),
            fontFamily = resolveFontFamily(fontFamilyChoice),
            textAlign = if (textAlignment == "LEFT") TextAlign.Start else TextAlign.Center,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    val words = remember(line) {
        if (line.words.isNotEmpty()) {
            line.words
        } else {
            KaraokeMidiParser.reconstructWordsFromSyllables(line.text, line.syllables)
        }
    }
    val wordSpacingDp = (fontSizeSp * 0.28f).coerceIn(5f, 12f).dp

    FlowRow(
        horizontalArrangement = if (textAlignment == "LEFT") Arrangement.spacedBy(wordSpacingDp, Alignment.Start)
        else Arrangement.spacedBy(wordSpacingDp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        for (word in words) {
            WordWipeBox(
                word = word,
                positionMs = positionMs,
                palette = palette,
                fontSizeSp = fontSizeSp,
                fontFamilyChoice = fontFamilyChoice,
                fontWeightChoice = fontWeightChoice,
                letterSpacingSp = letterSpacingSp,
                outlineThicknessDp = outlineThicknessDp,
                shadowIntensity = shadowIntensity,
                wipeEnabled = wipeEnabled
            )
        }
    }
}

@Composable
private fun WordWipeBox(
    word: LyricWord,
    positionMs: Long,
    palette: LyricColorPalette,
    fontSizeSp: Float,
    fontFamilyChoice: String = "DEFAULT",
    fontWeightChoice: String = "BLACK",
    letterSpacingSp: Float = 0f,
    outlineThicknessDp: Float = 1.5f,
    shadowIntensity: Float = 1.0f,
    wipeEnabled: Boolean = true
) {
    var layoutResult by remember(word.text, fontSizeSp, fontFamilyChoice, fontWeightChoice, letterSpacingSp) { mutableStateOf<TextLayoutResult?>(null) }

    val isCurrentlySinging = positionMs in word.startMs..word.endMs &&
        word.syllables.any { positionMs in it.startMs..it.endMs }
    val wipeFraction = if (wipeEnabled) computeWordSyllableWipeFraction(word, positionMs, layoutResult) else 0f

    val fFamily = resolveFontFamily(fontFamilyChoice, fontWeightChoice)
    val fWeight = resolveFontWeight(fontWeightChoice)

    val textStyle = TextStyle(
        fontSize = fontSizeSp.sp,
        fontWeight = fWeight,
        fontFamily = fFamily,
        letterSpacing = letterSpacingSp.sp,
        shadow = if (shadowIntensity > 0f) {
            Shadow(
                color = Color.Black.copy(alpha = (0.45f * shadowIntensity).coerceIn(0f, .65f)),
                offset = Offset(0f, 1f),
                blurRadius = (2f * shadowIntensity).coerceIn(0f, 4f)
            )
        } else null
    )

    Box(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        // Outline pass for superior contrast against video backgrounds
        if (outlineThicknessDp > 0f) {
            Text(
                text = word.text,
                color = palette.outlineColor.copy(alpha = 0.95f),
                style = textStyle.copy(
                    shadow = null,
                    drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = outlineThicknessDp * androidx.compose.ui.platform.LocalDensity.current.density)
                ),
                maxLines = 1,
                softWrap = false
            )
        }

        // Base unsung word layer
        Text(
            text = word.text,
            color = palette.unsungFill,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { layoutResult = it }
        )

        // Horizontal syllable-synchronized wipe overlay layer
        if (wipeFraction > 0f) {
            val rainbowTransition = rememberInfiniteTransition(label = "rainbow_wipe")
            val rainbowOffset by rainbowTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rainbowOffset"
            )

            val wipeModifier = Modifier.drawWithContent {
                clipRect(right = size.width * wipeFraction) {
                    this@drawWithContent.drawContent()
                }
            }

            if (palette.isRainbow) {
                val rainbowBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Red, Color(0xFFFF7F00), Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color(0xFF8B00FF), Color.Red
                    ),
                    start = Offset(layoutResult?.size?.width?.toFloat()?.times(rainbowOffset) ?: 0f, 0f),
                    end = Offset((layoutResult?.size?.width?.toFloat() ?: 200f) + (layoutResult?.size?.width?.toFloat()?.times(rainbowOffset) ?: 0f), 0f)
                )
                Text(
                    text = word.text,
                    style = textStyle.copy(brush = rainbowBrush),
                    maxLines = 1,
                    softWrap = false,
                    modifier = wipeModifier
                )
            } else {
                Text(
                    text = word.text,
                    color = if (isCurrentlySinging) palette.activeGlow else palette.activeFill,
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = wipeModifier
                )
            }
        }
    }
}

/**
 * Live Interactive Karaoke Preview Box inside Settings.
 * Displays sample lyrics with realistic timing in an animated continuous loop.
 */
@Composable
fun LiveLyricsPreviewBox(
    palette: LyricColorPalette,
    fontScale: Float,
    displayMode: String,
    fontFamilyChoice: String,
    fontWeightChoice: String,
    letterSpacingSp: Float,
    lineSpacingDp: Float,
    textAlignment: String,
    stagePosition: String,
    outlineThicknessDp: Float,
    shadowIntensity: Float,
    visualizerStyle: String = "NEON_BARS",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sample_preview")
    val previewProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 7000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "previewProgress"
    )

    val sampleLines = remember {
        listOf(
            LyricLine(
                index = 0,
                startMs = 600L,
                endMs = 3400L,
                text = "Somewhere over the rainbow skies are blue",
                syllables = listOf(
                    LyricSyllable("Some", 600L, 950L, 0, 4, endsWord = false),
                    LyricSyllable("where", 950L, 1300L, 4, 9, endsWord = true),
                    LyricSyllable("o", 1350L, 1600L, 10, 11, endsWord = false),
                    LyricSyllable("ver", 1600L, 1850L, 11, 14, endsWord = true),
                    LyricSyllable("the", 1900L, 2150L, 15, 18, endsWord = true),
                    LyricSyllable("rain", 2200L, 2550L, 19, 23, endsWord = false),
                    LyricSyllable("bow", 2550L, 2800L, 23, 26, endsWord = true),
                    LyricSyllable("skies", 2850L, 3050L, 27, 32, endsWord = true),
                    LyricSyllable("are", 3050L, 3200L, 33, 36, endsWord = true),
                    LyricSyllable("blue", 3200L, 3400L, 37, 41, endsWord = true)
                )
            ),
            LyricLine(
                index = 1,
                startMs = 3800L,
                endMs = 6600L,
                text = "And the dreams that you dare to dream really do come true",
                syllables = listOf(
                    LyricSyllable("And", 3800L, 4050L, 0, 3, endsWord = true),
                    LyricSyllable("the", 4050L, 4250L, 4, 7, endsWord = true),
                    LyricSyllable("dreams", 4300L, 4700L, 8, 14, endsWord = true),
                    LyricSyllable("that", 4750L, 4950L, 15, 19, endsWord = true),
                    LyricSyllable("you", 5000L, 5200L, 20, 23, endsWord = true),
                    LyricSyllable("dare", 5250L, 5500L, 24, 28, endsWord = true),
                    LyricSyllable("to", 5550L, 5700L, 29, 31, endsWord = true),
                    LyricSyllable("dream", 5750L, 6050L, 32, 37, endsWord = true),
                    LyricSyllable("real", 6100L, 6250L, 38, 42, endsWord = false),
                    LyricSyllable("ly", 6250L, 6380L, 42, 44, endsWord = true),
                    LyricSyllable("do", 6380L, 6480L, 45, 47, endsWord = true),
                    LyricSyllable("come", 6480L, 6550L, 48, 52, endsWord = true),
                    LyricSyllable("true", 6550L, 6600L, 53, 57, endsWord = true)
                )
            )
        )
    }

    val simulatedPlayback = PlaybackState(
        playing = true,
        positionMs = previewProgress.toLong(),
        durationMs = 7000L,
        lyricLines = sampleLines,
        channelLevels = List(16) { 0.45f }
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF040816),
        border = BorderStroke(1.5.dp, palette.activeFill.copy(alpha = 0.6f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NeonStageVisualizer(
                style = visualizerStyle,
                channelLevels = emptyList(),
                playing = true,
                primaryColor = palette.activeFill,
                secondaryColor = palette.activeGlow,
                modifier = Modifier.fillMaxSize(),
                brightness = 0.55f
            )

            SyllableKaraokeStage(
                playback = simulatedPlayback,
                palette = palette,
                fontScale = fontScale,
                karaokeMode = "SOLO",
                activeSinger = "Stage Singer",
                duetPartner = "",
                onSkipInterlude = {},
                twoLineMode = (displayMode == "TWO_LINE"),
                displayMode = displayMode,
                fontFamilyChoice = fontFamilyChoice,
                fontWeightChoice = fontWeightChoice,
                letterSpacingSp = letterSpacingSp,
                lineSpacingDp = lineSpacingDp,
                textAlignment = textAlignment,
                stagePosition = stagePosition,
                outlineThicknessDp = outlineThicknessDp,
                shadowIntensity = shadowIntensity,
                modifier = Modifier.fillMaxSize()
            )

            // Header Tag Badge
            Surface(
                color = Color(0xCC000000),
                shape = RoundedCornerShape(bottomEnd = 12.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "LIVE KARAOKE PREVIEW",
                    color = palette.activeFill,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Computes the exact `[0f..1f]` horizontal wipe progress across a [LyricWord] at [positionMs].
 */
private fun computeWordSyllableWipeFraction(
    word: LyricWord,
    positionMs: Long,
    layoutResult: TextLayoutResult?
): Float {
    val syllables = word.syllables
    if (syllables.isEmpty() || word.text.isEmpty()) return 0f
    if (positionMs <= word.startMs) return 0f
    if (positionMs >= word.endMs) return 1f

    if (syllables.size == 1) {
        val syl = syllables[0]
        val dur = (syl.endMs - syl.startMs).coerceAtLeast(1L)
        return ((positionMs - syl.startMs).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    }

    val totalChars = word.text.length.coerceAtLeast(1)
    val totalWidthPx = layoutResult?.size?.width?.toFloat() ?: 0f

    fun charIndexToFraction(charIdx: Int): Float {
        val clamped = charIdx.coerceIn(0, totalChars)
        if (clamped <= 0) return 0f
        if (clamped >= totalChars) return 1f
        if (layoutResult != null && totalWidthPx > 1f) {
            val px = runCatching {
                layoutResult.getHorizontalPosition(clamped, usePrimaryDirection = true)
            }.getOrDefault(0f)
            if (px > 0f && px <= totalWidthPx) {
                return (px / totalWidthPx).coerceIn(0f, 1f)
            }
        }
        return clamped.toFloat() / totalChars.toFloat()
    }

    var localCharCursor = 0
    var currentFraction = 0f

    for (syl in syllables) {
        val sylLen = syl.text.length.coerceAtLeast(1)
        val sylCharStart = localCharCursor.coerceIn(0, totalChars)
        val sylCharEnd = (localCharCursor + sylLen).coerceIn(sylCharStart, totalChars)
        localCharCursor += sylLen

        val xStart = charIndexToFraction(sylCharStart)
        val xEnd = charIndexToFraction(sylCharEnd)

        if (positionMs <= syl.startMs) {
            return currentFraction.coerceIn(0f, 1f)
        }
        if (positionMs < syl.endMs) {
            val dur = (syl.endMs - syl.startMs).coerceAtLeast(1L)
            val sylProgress = ((positionMs - syl.startMs).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            return (xStart + (xEnd - xStart) * sylProgress).coerceIn(0f, 1f)
        }
        currentFraction = xEnd
    }

    return currentFraction.coerceIn(0f, 1f)
}

/**
 * Live Microphone Pitch Guide & Scoring HUD Bar for Practice & Live Scoring modes.
 */
@Composable
fun LivePitchScoringBar(
    scoreState: LiveScoreState,
    primaryColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xCC0D1428),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (scoreState.active) primaryColor else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = scoreState.feedbackText,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    if (scoreState.comboStreak >= 3) {
                        Text(
                            text = "${scoreState.comboStreak}x STREAK 🔥",
                            color = Color(0xFFFFD600),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PITCH ${scoreState.pitchScore}%",
                        color = primaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "SCORE ${scoreState.currentScore}",
                        color = Color(0xFFFFD600),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Visual Pitch Offset Gauge (-6 semitones .. 0 Target .. +6 semitones)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF060A15))
            ) {
                val w = size.width
                val h = size.height
                val center = w * 0.5f
                // Target sweet-spot zone in center
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.24f),
                    topLeft = Offset(center - w * 0.12f, 0f),
                    size = Size(w * 0.24f, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(center, 0f),
                    end = Offset(center, h),
                    strokeWidth = 2.dp.toPx()
                )
                if (scoreState.detectedMidiNote > 0 && scoreState.targetMidiNote > 0) {
                    val normalized = (scoreState.semitoneOffset / 6f).coerceIn(-1f, 1f)
                    val markerX = (center + normalized * (w * 0.44f)).coerceIn(8f, w - 8f)
                    drawCircle(
                        color = if (kotlin.math.abs(scoreState.semitoneOffset) <= 1.6f) Color(0xFF00FF9D) else accentColor,
                        radius = h * 0.45f,
                        center = Offset(markerX, h * 0.5f)
                    )
                }
            }
        }

    }
}

/**
 * Advanced 8-Channel Real-Time Audio Mixer Dialog.
 */
@Composable
fun AudioMixerDialog(
    config: AudioMixerConfig,
    melodyEnabled: Boolean,
    onMelodyToggle: (Boolean) -> Unit,
    onUpdateConfig: (AudioMixerConfig) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1428),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFDCE7FF),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Equalizer, contentDescription = null, tint = Color(0xFF00F0FF))
                    Text("Pro Studio Audio Mixer", fontWeight = FontWeight.Black)
                }
                TextButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lead Vocal Melody Guide", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Mute or solo the MIDI vocal guide channel", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = melodyEnabled, onCheckedChange = onMelodyToggle)
                }

                MixerSliderRow(
                    label = "Master Synthesizer Gain",
                    value = config.masterGain,
                    range = 0.1f..2.2f,
                    display = "${(config.masterGain * 100).roundToInt()}%",
                    onValueChange = { onUpdateConfig(config.copy(masterGain = it)) }
                )
                MixerSliderRow(
                    label = "Melody Guide Volume",
                    value = config.melodyVolume,
                    range = 0f..1.5f,
                    display = "${(config.melodyVolume * 100).roundToInt()}%",
                    onValueChange = { onUpdateConfig(config.copy(melodyVolume = it)) }
                )
                MixerSliderRow(
                    label = "Drums & Percussion",
                    value = config.drumsVolume,
                    range = 0f..1.5f,
                    display = "${(config.drumsVolume * 100).roundToInt()}%",
                    onValueChange = { onUpdateConfig(config.copy(drumsVolume = it)) }
                )
                MixerSliderRow(
                    label = "Bass Guitar / Synth Bass",
                    value = config.bassVolume,
                    range = 0f..1.5f,
                    display = "${(config.bassVolume * 100).roundToInt()}%",
                    onValueChange = { onUpdateConfig(config.copy(bassVolume = it)) }
                )
                MixerSliderRow(
                    label = "Accompaniment (Keys/Strings/Guitar)",
                    value = config.accompanimentVolume,
                    range = 0f..1.5f,
                    display = "${(config.accompanimentVolume * 100).roundToInt()}%",
                    onValueChange = { onUpdateConfig(config.copy(accompanimentVolume = it)) }
                )
                MixerSliderRow(
                    label = "Concert Hall Reverb Level",
                    value = config.reverbLevel,
                    range = 0f..1.0f,
                    display = "${(config.reverbLevel * 100).roundToInt()}%",
                    onValueChange = { onUpdateConfig(config.copy(reverbLevel = it)) }
                )
                MixerSliderRow(
                    label = "Reverb Room Size",
                    value = config.reverbRoomSize,
                    range = 0.1f..1.0f,
                    display = "${(config.reverbRoomSize * 100).roundToInt()}%",
                    onValueChange = { onUpdateConfig(config.copy(reverbRoomSize = it)) }
                )
                MixerSliderRow(
                    label = "Stereo Chorus Depth",
                    value = config.chorusLevel,
                    range = 0f..1.0f,
                    display = "${(config.chorusLevel * 100).roundToInt()}%",
                    onValueChange = { onUpdateConfig(config.copy(chorusLevel = it)) }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun MixerSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(display, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00F0FF))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00F0FF),
                activeTrackColor = Color(0xFF00F0FF)
            )
        )
    }
}

/**
 * Videoke Digital Song Number Keypad Modal (#00001 - #99999).
 */
@Composable
fun SongNumberKeypadDialog(
    songs: List<SongEntity>,
    onLookupMatches: (String) -> Unit,
    onConfirmCode: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var digits by remember { mutableStateOf("") }
    val padded = digits.padStart(5, '0')
    val previewSong = remember(digits, songs) {
        if (digits.isEmpty()) null
        else {
            val numId = digits.toLongOrNull() ?: -1L
            songs.firstOrNull {
                it.songNumber == digits || it.songNumber == padded || it.id == numId || it.displayNumber == padded
            } ?: songs.firstOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B1022),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFDCE7FF),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Videoke Song Number Keypad", fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF050812),
                    border = BorderStroke(1.5.dp, Color(0xFF00F0FF))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (digits.isEmpty()) "# _ _ _ _ _" else "# ${digits.padStart(5, '0')}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            color = Color(0xFF00F0FF)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = previewSong?.let { "${it.title} — ${it.artist.ifBlank { "Karaoke" }}" }
                                ?: if (digits.isEmpty()) "Punch in a 1 to 5 digit song code" else "Searching song #$padded…",
                            color = if (previewSong != null) Color(0xFFFFD600) else Color(0xFF9FB0D6),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("CLR", "0", "DEL")
                )
                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (key in row) {
                            FilledTonalButton(
                                onClick = {
                                    when (key) {
                                        "CLR" -> {
                                            digits = ""
                                            onLookupMatches("")
                                        }
                                        "DEL" -> {
                                            if (digits.isNotEmpty()) {
                                                digits = digits.dropLast(1)
                                                onLookupMatches(digits)
                                            }
                                        }
                                        else -> {
                                            if (digits.length < 5) {
                                                digits += key
                                                onLookupMatches(digits)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (key == "DEL") {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                                } else {
                                    Text(key, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (digits.isNotBlank()) {
                            onConfirmCode(digits, false)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = digits.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+ Queue", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(
                    onClick = {
                        if (digits.isNotBlank()) {
                            onConfirmCode(digits, true)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = digits.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sing Now", fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    )
}

/**
 * Wi-Fi Remote Control QR-Code Pairing Dialog.
 */
@Composable
fun WifiRemoteQrDialog(
    remoteState: RemoteServerState,
    onToggleServer: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0C1224),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFDCE7FF),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color(0xFF00F0FF))
                Text("Wi-Fi Smartphone QR Remote", fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Local Wi-Fi Web Remote", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Phones on the same Wi-Fi or Hotspot can scan to search & reserve songs", fontSize = 12.sp)
                    }
                    Switch(checked = remoteState.running, onCheckedChange = onToggleServer)
                }

                if (remoteState.running && remoteState.pairingUrl.isNotBlank()) {
                    val matrix = remember(remoteState.pairingUrl) {
                        QrCodeMatrix.encodeUrl(remoteState.pairingUrl)
                    }
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val n = matrix.size.coerceAtLeast(1)
                            val cell = size.width / n
                            for (r in 0 until n) {
                                for (c in 0 until n) {
                                    if (matrix[r][c]) {
                                        drawRect(
                                            color = Color(0xFF060A17),
                                            topLeft = Offset(c * cell, r * cell),
                                            size = Size(cell + 0.6f, cell + 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = remoteState.pairingUrl,
                        color = Color(0xFF00F0FF),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "PIN: ${remoteState.pin}",
                            color = Color(0xFFFFD600),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• ${remoteState.connectedClientsCount} Guest(s) Connected",
                            color = Color(0xFF00FF9D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    if (remoteState.lastRemoteAction.isNotBlank()) {
                        Text(
                            text = "Last activity: ${remoteState.lastRemoteAction}",
                            fontSize = 12.sp,
                            color = Color(0xFFB5C7E8)
                        )
                    }
                } else {
                    Text(
                        text = "Turn on the toggle above to generate a live QR code for guests to scan with their phone camera.",
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * Celebratory Karaoke Performance Score Card Dialog.
 */
@Composable
fun ScoreCardDialog(
    card: FinalScoreCard,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1329),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE5EEFF),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD600),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(card.badge, fontWeight = FontWeight.Black, color = Color(0xFFFFD600), fontSize = 20.sp)
                Text(card.songTitle, fontSize = 14.sp, color = Color(0xFF00F0FF), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${card.totalScore}",
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Sung by ${card.singerName}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB5C7E8)
                )
                Text(
                    text = card.compliment,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                ScoreBreakdownBar("Pitch Accuracy", card.pitchScore, Color(0xFF00F0FF))
                ScoreBreakdownBar("Rhythm & Timing", card.timingScore, Color(0xFF00FF9D))
                ScoreBreakdownBar("Vocal Stability", card.stabilityScore, Color(0xFFFF2A85))
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF), contentColor = Color(0xFF050914))
            ) {
                Text("Awesome!", fontWeight = FontWeight.Black)
            }
        }
    )
}

@Composable
private fun ScoreBreakdownBar(label: String, score: Int, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("$score / 100", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
    }
}
