package com.ilukezippo.grabbit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object G {
    val bg = Color(0xFF0E0D14)
    val surface = Color(0xFF17151F)
    val surface2 = Color(0xFF211E2C)
    val stroke = Color(0xFF2E2A3B)
    val text = Color(0xFFF4F2FA)
    val muted = Color(0xFF9C97AE)
    val violet = Color(0xFF8B5CF6)
    val pink = Color(0xFFEC4899)
    val amber = Color(0xFFF59E0B)
    val green = Color(0xFF34D399)
    val red = Color(0xFFF43F5E)
    val gradient = Brush.linearGradient(listOf(violet, pink, amber))
    val gradientSoft = Brush.linearGradient(listOf(violet.copy(alpha = .28f), pink.copy(alpha = .18f), amber.copy(alpha = .10f)))
}

private val scheme = darkColorScheme(
    primary = G.violet, onPrimary = Color.White, secondary = G.pink, tertiary = G.amber,
    background = G.bg, onBackground = G.text, surface = G.surface, onSurface = G.text,
    surfaceVariant = G.surface2, onSurfaceVariant = G.muted, outline = G.stroke, error = G.red,
    surfaceContainer = G.surface, surfaceContainerHigh = G.surface2, surfaceContainerLow = G.surface,
)

@Composable
fun GrabbitTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = scheme,
        typography = base.copy(
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
        content = { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides G.text, content = content) },
    )
}

/** Pill button filled with the brand gradient. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .defaultMinSize(minHeight = 54.dp)
            .alpha(if (enabled) 1f else .45f)
            .background(G.gradient, shape),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick, enabled = enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent, contentColor = Color.White, disabledContentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
            modifier = Modifier.defaultMinSize(minHeight = 54.dp),
            content = content,
        )
    }
}

val gradientTitle = TextStyle(brush = G.gradient, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-1).sp)
