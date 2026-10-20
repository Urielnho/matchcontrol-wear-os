package com.matchcontrol.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*

object MatchColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF151515)
    val Raised = Color(0xFF222222)
    val Text = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFAAAAAA)
    val Accent = Color(0xFFB8F35A)
    val Live = Color(0xFFFF5252)
    val Warning = Color(0xFFFFC76B)
}
object MatchSpace {
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val Touch = 48.dp
    val CardRadius = 24.dp
}
object MatchType {
    val Score = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp, fontFeatureSettings = "tnum")
    val HeroScore = Score.copy(fontSize = 35.sp, lineHeight = 40.sp)
    val DetailScore = Score.copy(fontSize = 44.sp, lineHeight = 49.sp)
    val Title = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 13.sp, lineHeight = 17.sp)
    val Label = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
    val Micro = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
}
private val colors = ColorScheme(
    primary = MatchColors.Accent, primaryDim = Color(0xFF94C944),
    primaryContainer = Color(0xFF28331B), onPrimary = Color.Black,
    onPrimaryContainer = MatchColors.Accent, secondary = Color.White,
    secondaryContainer = MatchColors.Raised, onSecondaryContainer = Color.White,
    surfaceContainerLow = MatchColors.Surface, surfaceContainer = MatchColors.Surface,
    surfaceContainerHigh = MatchColors.Raised, onSurface = Color.White,
    onSurfaceVariant = MatchColors.Secondary, background = Color.Black,
    onBackground = Color.White, error = MatchColors.Live,
)
@Composable fun MatchControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(
        titleMedium = MatchType.Title, bodyMedium = MatchType.Body,
        labelMedium = MatchType.Label, numeralLarge = MatchType.HeroScore,
    ), content = content)
}
