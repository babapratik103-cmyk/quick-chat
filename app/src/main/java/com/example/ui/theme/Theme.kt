package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val QuickChatDarkColorScheme = darkColorScheme(
    primary = QuickChatPrimary,
    onPrimary = DarkBackground,
    primaryContainer = QuickChatPrimaryContainer,
    onPrimaryContainer = QuickChatPrimary,
    secondary = QuickChatPrimaryDark,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    outlineVariant = BubbleBorderRecipient,
    error = StatusError,
    onError = TextPrimary
)

@Composable
fun QuickChatTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = QuickChatDarkColorScheme,
        typography = Typography,
        shapes = QuickChatShapes,
        content = content
    )
}
