package com.example.goenforcer

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TactileButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    topColor: Color = GoColors.Primary,
    bottomColor: Color = GoColors.PrimaryPress,
    edgeColor: Color = Color(0xFF3A7D01),
    contentColor: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    
    val translationY by animateDpAsState(
        targetValue = if (pressed) 4.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonPress"
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .drawBehind {
                // Bottom shadow/edge
                drawRoundRect(
                    color = edgeColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                )
            }
            .padding(bottom = if (pressed) 0.dp else 4.dp)
            .graphicsLayer { this.translationY = translationY.toPx() }
            .background(topColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is androidx.compose.foundation.interaction.PressInteraction.Press -> pressed = true
                    is androidx.compose.foundation.interaction.PressInteraction.Release -> pressed = false
                    is androidx.compose.foundation.interaction.PressInteraction.Cancel -> pressed = false
                }
            }
        }
        
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = contentColor,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 4.dp else 12.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(28.dp))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White, Color.White.copy(alpha = 0.1f)),
                    start = Offset(0f, 0f),
                    end = Offset(500f, 500f)
                ),
                RoundedCornerShape(28.dp)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(24.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val brush = Brush.radialGradient(
                    colors = listOf(
                        GoColors.AccentBlue.copy(alpha = 0.1f),
                        GoColors.AccentTeal.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(
                        size.width * (0.5f + 0.3f * kotlin.math.cos(time)),
                        size.height * (0.5f + 0.3f * kotlin.math.sin(time))
                    ),
                    radius = size.minDimension
                )
                drawRect(brush)
            }
    )
}
