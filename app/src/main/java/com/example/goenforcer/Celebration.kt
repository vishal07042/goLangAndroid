package com.example.goenforcer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val GRAVITY = 0.35f
private const val DRAG = 0.996f

private class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val size: Float,
    val color: Color,
    var rotation: Float,
    val rotSpeed: Float,
    var life: Float,
    val maxLife: Float
)

private fun spawnParticles(w: Float, h: Float, count: Int = 150): List<Particle> {
    val rnd = Random(System.currentTimeMillis())
    val colors = listOf(
        GoColors.AccentGold, GoColors.AccentBlue, GoColors.AccentTeal,
        Color(0xFFFF7B72), Color(0xFFD2A8FF), Color(0xFF79C0FF)
    )
    return List(count) {
        val maxLife = 1600f + rnd.nextFloat() * 1400f
        val angle = rnd.nextFloat() * (Math.PI.toFloat() * 2f)
        val speed = 0.18f + rnd.nextFloat() * 0.35f
        Particle(
            x = w * (0.1f + rnd.nextFloat() * 0.8f),
            y = h * (0.35f + rnd.nextFloat() * 0.55f),
            vx = cos(angle) * speed,
            vy = -speed * (0.7f + rnd.nextFloat() * 0.6f),
            size = 4f + rnd.nextFloat() * 7f,
            color = colors[rnd.nextInt(colors.size)],
            rotation = rnd.nextFloat() * 360f,
            rotSpeed = (rnd.nextFloat() - 0.5f) * 0.6f,
            life = maxLife,
            maxLife = maxLife
        )
    }
}

/** Full-screen confetti burst. Calls [onFinished] once all particles die. */
@Composable
fun CelebrationOverlay(onFinished: () -> Unit) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
    ) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val particles = remember(w, h) { spawnParticles(w, h) }
        var frame by remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last).toFloat() / 1_000_000f).coerceAtMost(40f)
                last = now
                var anyAlive = false
                for (p in particles) {
                    p.life -= dt
                    if (p.life <= 0f) continue
                    anyAlive = true
                    p.vy += GRAVITY * dt
                    p.vx *= DRAG
                    p.vy *= DRAG
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.rotation += p.rotSpeed * dt
                }
                frame++
                if (!anyAlive) {
                    onFinished()
                    break
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            frame // read snapshot state so each frame invalidates the draw
            for (p in particles) {
                if (p.life <= 0f) continue
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                rotate(
                    degrees = p.rotation,
                    pivot = Offset(p.x + p.size / 2f, p.y + p.size / 2f)
                ) {
                    drawRect(
                        color = p.color.copy(alpha = alpha * 0.9f),
                        topLeft = Offset(p.x, p.y),
                        size = Size(p.size, p.size * 0.6f)
                    )
                }
            }
        }
    }
}
