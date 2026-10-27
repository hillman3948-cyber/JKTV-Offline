package com.jktv.offline.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class DesignStyleOption(
    val id: String,
    val label: String,
    val description: String,
    val background: Color,
    val surface: Color,
    val card: Color,
    val border: Color
)

data class ColorStyleOption(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val glow: Color
)

object AppThemeStyles {
    val DesignStyles = listOf(
        DesignStyleOption(
            id = "JKTV_SIGNATURE",
            label = "JKTV Signature",
            description = "Signature AMOLED black with vibrant cyber accents",
            background = Color(0xFF060913),
            surface = Color(0xFF0C1224),
            card = Color(0xFF131D36),
            border = Color(0xFF1E2D4E)
        ),
        DesignStyleOption(
            id = "AMOLED_BLACK",
            label = "AMOLED Black",
            description = "100% pitch black for battery saving & max contrast",
            background = Color(0xFF000000),
            surface = Color(0xFF080808),
            card = Color(0xFF101010),
            border = Color(0xFF222222)
        ),
        DesignStyleOption(
            id = "MODERN_MINIMAL",
            label = "Modern Minimal",
            description = "Matte charcoal with subtle, elegant borders",
            background = Color(0xFF101114),
            surface = Color(0xFF16171C),
            card = Color(0xFF1D1F26),
            border = Color(0xFF2B2E38)
        ),
        DesignStyleOption(
            id = "CYBER_NEON",
            label = "Cyber Neon",
            description = "Electric cyberpunk night with intense contrast",
            background = Color(0xFF04050E),
            surface = Color(0xFF090A1A),
            card = Color(0xFF10122E),
            border = Color(0xFF1F2459)
        ),
        DesignStyleOption(
            id = "ROYAL_PURPLE",
            label = "Royal Purple",
            description = "Deep royal indigo with amethyst and violet aura",
            background = Color(0xFF070410),
            surface = Color(0xFF0F0922),
            card = Color(0xFF191036),
            border = Color(0xFF2C1E5C)
        ),
        DesignStyleOption(
            id = "OCEAN_BLUE",
            label = "Ocean Blue",
            description = "Deep abyssal sapphire with cyan-teal highlights",
            background = Color(0xFF030814),
            surface = Color(0xFF071228),
            card = Color(0xFF0D1D3E),
            border = Color(0xFF163266)
        ),
        DesignStyleOption(
            id = "LIGHT_ELEGANCE",
            label = "Light Elegance",
            description = "Modern dark slate and luxury titanium styling",
            background = Color(0xFF12141A),
            surface = Color(0xFF1A1D26),
            card = Color(0xFF242834),
            border = Color(0xFF383E50)
        )
    )

    val ColorStyles = listOf(
        ColorStyleOption(
            id = "CYAN_BLUE",
            label = "Cyan Blue",
            primary = Color(0xFF00F0FF),
            secondary = Color(0xFFFF2A85),
            glow = Color(0xFF80D8FF)
        ),
        ColorStyleOption(
            id = "NEON_PINK",
            label = "Neon Pink",
            primary = Color(0xFFFF2A85),
            secondary = Color(0xFF00F0FF),
            glow = Color(0xFFFF66AA)
        ),
        ColorStyleOption(
            id = "ELECTRIC_PURPLE",
            label = "Electric Purple",
            primary = Color(0xFF9D4EDD),
            secondary = Color(0xFF00F0FF),
            glow = Color(0xFFC77DFF)
        ),
        ColorStyleOption(
            id = "EMERALD_GREEN",
            label = "Emerald Green",
            primary = Color(0xFF00E676),
            secondary = Color(0xFF00F0FF),
            glow = Color(0xFF69F0AE)
        ),
        ColorStyleOption(
            id = "SUNSET_ORANGE",
            label = "Sunset Orange",
            primary = Color(0xFFFF6D00),
            secondary = Color(0xFFFFD600),
            glow = Color(0xFFFFAB40)
        ),
        ColorStyleOption(
            id = "RUBY_RED",
            label = "Ruby Red",
            primary = Color(0xFFFF1744),
            secondary = Color(0xFFFF9100),
            glow = Color(0xFFFF5252)
        ),
        ColorStyleOption(
            id = "GOLDEN_YELLOW",
            label = "Golden Yellow",
            primary = Color(0xFFFFD600),
            secondary = Color(0xFFFF6D00),
            glow = Color(0xFFFFE57F)
        ),
        ColorStyleOption(
            id = "CUSTOM",
            label = "Custom Color Picker",
            primary = Color(0xFF00F0FF),
            secondary = Color(0xFFFF2A85),
            glow = Color(0xFF80D8FF)
        )
    )

    fun resolveDesignStyle(id: String): DesignStyleOption {
        return DesignStyles.firstOrNull { it.id == id } ?: DesignStyles.first()
    }

    fun resolveColorStyle(
        id: String,
        customPrimaryHex: String = "#00F0FF",
        customSecondaryHex: String = "#FF2A85"
    ): ColorStyleOption {
        if (id == "CUSTOM") {
            val primary = parseSafeHex(customPrimaryHex, Color(0xFF00F0FF))
            val secondary = parseSafeHex(customSecondaryHex, Color(0xFFFF2A85))
            return ColorStyleOption(
                id = "CUSTOM",
                label = "Custom Color Picker",
                primary = primary,
                secondary = secondary,
                glow = primary
            )
        }
        return ColorStyles.firstOrNull { it.id == id } ?: ColorStyles.first()
    }

    fun buildColorScheme(
        designStyleId: String,
        colorStyleId: String,
        customPrimaryHex: String,
        customSecondaryHex: String
    ): ColorScheme {
        val design = resolveDesignStyle(designStyleId)
        val color = resolveColorStyle(colorStyleId, customPrimaryHex, customSecondaryHex)

        return darkColorScheme(
            primary = color.primary,
            onPrimary = if (isLightColor(color.primary)) Color(0xFF040711) else Color.White,
            primaryContainer = design.card,
            onPrimaryContainer = color.glow,
            secondary = color.secondary,
            onSecondary = if (isLightColor(color.secondary)) Color(0xFF040711) else Color.White,
            secondaryContainer = design.surface,
            onSecondaryContainer = Color.White,
            tertiary = color.glow,
            background = design.background,
            onBackground = Color(0xFFF2F6FF),
            surface = design.surface,
            onSurface = Color(0xFFF2F6FF),
            surfaceVariant = design.card,
            onSurfaceVariant = Color(0xFF9FB2D9),
            outline = design.border
        )
    }

    private fun isLightColor(color: Color): Boolean {
        val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return luminance > 0.5f
    }

    private fun parseSafeHex(hex: String, fallback: Color): Color {
        val clean = hex.trim().removePrefix("#")
        return try {
            when (clean.length) {
                6 -> Color(android.graphics.Color.parseColor("#$clean"))
                8 -> Color(android.graphics.Color.parseColor("#$clean"))
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
