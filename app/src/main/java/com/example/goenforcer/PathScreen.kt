package com.example.goenforcer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
fun PathScreen(onSnippetSelected: (SnippetContent) -> Unit) {
    val scrollState = rememberScrollState()
    
    val pathSteps = remember {
        ContentStore.LESSONS.flatMap { lesson ->
            lesson.snippets.mapIndexed { sIdx, snippet ->
                PathStep(
                    lessonTitle = if (sIdx == 0) lesson.title else null,
                    topic = lesson.topic,
                    snippet = snippet
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "The Gopher's Journey",
                style = MaterialTheme.typography.titleLarge,
                color = GoColors.TextPrimary
            )
            Text(
                text = "Master the art of Go, one step at a time.",
                style = MaterialTheme.typography.bodyMedium,
                color = GoColors.TextMuted
            )
            
            Spacer(Modifier.height(60.dp))
            
            Box(contentAlignment = Alignment.TopCenter) {
                PathLine(stepsCount = pathSteps.size)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    pathSteps.forEachIndexed { index, step ->
                        val xOffset = sin(index * 1.2f) * 80f 
                        
                        if (step.lessonTitle != null) {
                            Text(
                                text = step.lessonTitle.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = GoColors.AccentGold,
                                modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .offset(x = xOffset.dp),
                                letterSpacing = 1.sp
                            )
                        }

                        PathNode(
                            title = step.snippet.title,
                            icon = when(step.topic) {
                                Topic.Basics -> Icons.Filled.Star
                                Topic.Concurrency -> Icons.Filled.Bolt
                                Topic.Advanced -> Icons.Filled.Lock
                            },
                            isUnlocked = true,
                            progress = if (index == 0) 1f else 0f,
                            modifier = Modifier.offset(x = xOffset.dp),
                            onClick = { onSnippetSelected(step.snippet) }
                        )
                        
                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(120.dp))
        }
    }
}

data class PathStep(
    val lessonTitle: String?,
    val topic: Topic,
    val snippet: SnippetContent
)

@Composable
fun PathLine(stepsCount: Int) {
    Canvas(modifier = Modifier.fillMaxWidth().height((stepsCount * 180).dp)) {
        val strokeWidth = 8.dp.toPx()
        val stepHeight = 172.dp.toPx()
        
        var prevX = size.width / 2 + sin(0 * 1.2f) * 80.dp.toPx()
        var prevY = 60.dp.toPx()

        for (i in 1 until stepsCount) {
            val curX = size.width / 2 + sin(i * 1.2f) * 80.dp.toPx()
            val curY = prevY + stepHeight
            
            drawLine(
                color = GoColors.Border.copy(alpha = 0.4f),
                start = Offset(prevX, prevY),
                end = Offset(curX, curY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            prevX = curX
            prevY = curY
        }
    }
}

@Composable
fun PathNode(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isUnlocked: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(if (isUnlocked) 12.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(if (isUnlocked) Color.White else GoColors.Surface)
                .border(
                    width = 4.dp,
                    brush = if (isUnlocked) {
                        Brush.sweepGradient(listOf(GoColors.Primary, GoColors.PrimaryLight, GoColors.Primary))
                    } else {
                        Brush.linearGradient(listOf(GoColors.Border, GoColors.Border))
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isUnlocked) GoColors.Primary else GoColors.TextFaint,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = if (isUnlocked) GoColors.TextPrimary else GoColors.TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(100.dp)
        )
    }
}
