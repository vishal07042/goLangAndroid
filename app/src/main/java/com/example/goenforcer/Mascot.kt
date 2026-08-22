package com.example.goenforcer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class MascotMood {
    Idle, Typing, Error, Success, Thinking
}

@Composable
fun GopherMascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
    skin: Color = Color(0xFFC4A484) // Default Brown
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascotIdle")
    
    // Idle bounce
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    // Digging/Typing vibration
    val vibration by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vibration"
    )

    // Eye blinking
    val blink by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 9000
                1f at 0
                1f at 6800
                0.1f at 6900
                1f at 8800
            }
        ),
        label = "blink"
    )

    Canvas(modifier = modifier.size(60.dp)) {
        val w = size.width
        val h = size.height
        
        val moodOffset = when (mood) {
            MascotMood.Idle -> bounce
            MascotMood.Typing -> vibration
            MascotMood.Error -> 0f
            MascotMood.Success -> -bounce * 2
            MascotMood.Thinking -> bounce * 0.5f
        }

        // Body
        val bodyPath = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = w * 0.1f,
                    top = h * 0.2f + moodOffset,
                    right = w * 0.9f,
                    bottom = h * 0.95f + moodOffset,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.4f)
                )
            )
        }
        drawPath(bodyPath, skin)

        // Ears
        drawCircle(
            color = skin,
            radius = w * 0.1f,
            center = Offset(w * 0.2f, h * 0.25f + moodOffset)
        )
        drawCircle(
            color = skin,
            radius = w * 0.1f,
            center = Offset(w * 0.8f, h * 0.25f + moodOffset)
        )

        // Eyes
        val eyeY = h * 0.45f + moodOffset
        val eyeWidth = w * 0.22f
        val eyeHeight = eyeWidth * blink
        
        drawOval(
            color = Color.White,
            topLeft = Offset(w * 0.25f - eyeWidth / 2, eyeY - eyeHeight / 2),
            size = Size(eyeWidth, eyeHeight)
        )
        drawOval(
            color = Color.White,
            topLeft = Offset(w * 0.75f - eyeWidth / 2, eyeY - eyeHeight / 2),
            size = Size(eyeWidth, eyeHeight)
        )

        // Pupils
        if (blink > 0.5f) {
            val pupilSize = w * 0.08f
            val pupilOffset = if (mood == MascotMood.Error) vibration else 0f
            drawCircle(
                color = Color.Black,
                radius = pupilSize,
                center = Offset(w * 0.25f + pupilOffset, eyeY)
            )
            drawCircle(
                color = Color.Black,
                radius = pupilSize,
                center = Offset(w * 0.75f + pupilOffset, eyeY)
            )
        }

        // Nose
        drawCircle(
            color = Color(0xFF4A3728),
            radius = w * 0.05f,
            center = Offset(w * 0.5f, h * 0.55f + moodOffset)
        )

        // Teeth
        val toothW = w * 0.08f
        val toothH = h * 0.08f
        drawRect(
            color = Color.White,
            topLeft = Offset(w * 0.42f, h * 0.6f + moodOffset),
            size = Size(toothW, toothH)
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(w * 0.5f, h * 0.6f + moodOffset),
            size = Size(toothW, toothH)
        )

        // Nerd Skin (Glasses)
        if (skin == Color(0xFF569CD6)) { // Blue skin = Nerd/Pro
            drawRect(
                color = Color.Black.copy(alpha = 0.8f),
                topLeft = Offset(w * 0.15f, h * 0.42f + moodOffset),
                size = Size(w * 0.7f, h * 0.05f)
            )
        }
        
        // Success (Happy blush)
        if (mood == MascotMood.Success) {
            drawCircle(
                color = Color(0xFFFFB6C1).copy(alpha = 0.6f),
                radius = w * 0.07f,
                center = Offset(w * 0.2f, h * 0.55f + moodOffset)
            )
            drawCircle(
                color = Color(0xFFFFB6C1).copy(alpha = 0.6f),
                radius = w * 0.07f,
                center = Offset(w * 0.8f, h * 0.55f + moodOffset)
            )
        }
    }
}
