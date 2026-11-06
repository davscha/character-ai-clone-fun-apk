package de.davscha.characterfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.davscha.characterfun.data.Character
import java.io.File

private val avatarColors = listOf(
    Color(0xFF6D3FD6), Color(0xFF2E7D6B), Color(0xFFB4533A), Color(0xFF3A6FB4),
    Color(0xFF9C3A7A), Color(0xFF6B7D2E), Color(0xFF7A5A3A), Color(0xFF3A8C9C),
)

/** Runder Avatar: Bild, sonst Emoji, sonst Anfangsbuchstabe auf farbigem Grund. */
@Composable
fun Avatar(character: Character, imageFile: File?, size: Dp = 40.dp) {
    val bg = avatarColors[(character.name.hashCode() and 0x7fffffff) % avatarColors.size]
    Box(
        Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        when {
            imageFile != null -> AsyncImage(
                model = imageFile,
                contentDescription = character.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
            character.emoji.isNotBlank() -> Text(character.emoji, fontSize = (size.value * 0.5f).sp)
            else -> Text(
                character.name.trim().take(1).uppercase().ifEmpty { "?" },
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
