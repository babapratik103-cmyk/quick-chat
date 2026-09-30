package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.QuickChatPrimary

val AvatarColorPresets = listOf(
    "#F59E0B", // Amber
    "#10B981", // Emerald
    "#6366F1", // Indigo
    "#EC4899", // Rose
    "#06B6D4"  // Cyan
)

fun parseHexColor(hex: String, fallback: Color = QuickChatPrimary): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorLong = clean.toLong(16)
        if (clean.length == 6) {
            Color(colorLong or 0x00000000FF000000)
        } else {
            Color(colorLong)
        }
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    colorHex: String = "#F59E0B",
    fontSize: Int = 16
) {
    val initial = name.trim().take(1).uppercase().ifEmpty { "?" }
    val accentColor = parseHexColor(colorHex)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DarkBackground)
            .border(1.5.dp, accentColor, CircleShape)
    ) {
        Text(
            text = initial,
            color = accentColor,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
