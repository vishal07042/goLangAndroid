package com.example.goenforcer

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.intellij.lang.annotations.Language

val CodeFont = FontFamily.Monospace

// ─── AGSL Shaders (Premium 2026 Visuals) ─────────────────────────────────
@Language("AGSL")
const val FROSTED_GLASS_SHADER = """
    uniform float2 size;
    uniform shader content;
    uniform float blurAmount;

    half4 main(float2 fragCoord) {
        half4 color = content.eval(fragCoord);
        // Simple approximation of blur/frosting for demonstration
        // Real premium implementation would use a multi-pass approach or RenderEffect
        return color * 0.9 + half4(0.05, 0.05, 0.05, 0.1);
    }
"""

@Language("AGSL")
const val AURORA_SHADER = """
    uniform float time;
    uniform float2 resolution;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution.xy;
        float3 col = 0.5 + 0.5 * cos(time + uv.xyx + float3(0, 2, 4));
        return half4(col, 0.05); // Subtle aurora overlay
    }
"""

// ─── Light, bright palette (Duolingo-inspired surfaces + accent system) ──
object GoColors {
    // Surfaces — crisp white canvas
    val Background   = Color(0xFFFFFFFF)
    val Surface      = Color(0xFFF4F7FA)
    val Card         = Color(0xFFFFFFFF)
    val CardRaised   = Color(0xFFEDF1F4)
    val Border       = Color(0xFFE5EAEF)
    val TextPrimary  = Color(0xFF334155)
    val TextMuted    = Color(0xFF64748B)
    val TextFaint    = Color(0xFF94A3B8)

    // Brand / functional accents
    val Primary      = Color(0xFF58CC02)   // green: correct / progress / CTA
    val PrimaryLight = Color(0xFF78E000)
    val PrimaryPress = Color(0xFF3A7D01)
    val Success      = Primary
    val AccentBlue   = Color(0xFF1CB0F6)   // info / progress
    val AccentTeal   = Color(0xFF00A598)
    val Streak       = Color(0xFFFF9600)   // fire / warm
    val AccentGold   = Color(0xFFFFC800)   // rewards
    val Error        = Color(0xFFFF4B4B)
    val ErrorBg      = Color(0xFFFFE7E6)

    // Code chips (light "syntax" look)
    val ChipCurrent      = Color(0xFFD4EAFC)   // darker current-highlight blue
    val ChipCurrentText  = Color(0xFF0B5FA8)
    val ChipDone         = Color(0xFFFFF1B8)   // yellow bg for completed words
    val ChipDoneText     = Color(0xFF1E8A3C)   // green text on yellow
    val FutureBorder     = Color(0xFF4C9E1F)   // green outline for pending words
    val Keyword          = Color(0xFF1B78C8)   // bright blue
    val Type             = Color(0xFF0096A8)   // teal
    val PlainText        = Color(0xFF3F4A57)
    val Comment          = Color(0xFF8A97A3)
}

private val ColorScheme = lightColorScheme(
    primary = GoColors.Primary,
    onPrimary = Color.White,
    secondary = GoColors.AccentBlue,
    onSecondary = Color.White,
    tertiary = GoColors.AccentTeal,
    onTertiary = Color.White,
    background = GoColors.Background,
    onBackground = GoColors.TextPrimary,
    surface = GoColors.Surface,
    onSurface = GoColors.TextPrimary,
    surfaceVariant = GoColors.Card,
    onSurfaceVariant = GoColors.TextMuted,
    outline = GoColors.Border,
    error = GoColors.Error,
    onError = Color.White
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun GoEnforcerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = AppTypography,
        content = content
    )
}