package com.example.goenforcer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun HomeScreen(onChallenge: () -> Unit) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val home by vm.state.collectAsStateWithLifecycle()

    // Refresh every time the dashboard resumes — returning from the
    // AffirmationScreen (or any app) always shows the current state.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() }
    }

    val streak = home.streak
    val snippets = home.snippets
    val words = home.words
    val accuracy = home.accuracy
    val doneToday = home.doneToday
    val todayLabel = SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date())

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    val dailySnippet = remember { ContentStore.getDailySnippet(context) }
    val todayTitle = dailySnippet.title
    val preview = remember(dailySnippet) {
        Core.decodeSegments(dailySnippet.rawCode)
            .filter { !it.comment && !it.boilerplate }.joinToString("") { it.text }
            .lineSequence().firstOrNull { it.trim().isNotEmpty() } ?: "func main"
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            // ── Premium Header ──
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greeting.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = GoColors.AccentGold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Hello, Gopher",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = GoColors.TextPrimary
                        )
                        Text(
                            text = todayLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoColors.TextMuted
                        )
                    }
                    GopherMascot(mood = MascotMood.Idle, skin = Color(0xFF569CD6))
                }
            }

            // ── Bento Grid Section 1: Momentum ──
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PremiumCard(modifier = Modifier.weight(1.2f)) {
                        Column {
                            Icon(
                                imageVector = Icons.Filled.Whatshot,
                                contentDescription = null,
                                tint = GoColors.Streak,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (streak > 0) "$streak Day Streak" else "Elite Status",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoColors.TextPrimary
                            )
                            Text(
                                text = "Your daily cognitive edge.",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoColors.TextFaint
                            )
                        }
                    }
                    
                    PremiumCard(modifier = Modifier.weight(0.8f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Pill(
                                text = if (doneToday) "ACTIVE" else "LOCKED",
                                icon = if (doneToday) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                color = if (doneToday) GoColors.Success else GoColors.Error
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Access",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoColors.TextFaint
                            )
                        }
                    }
                }
            }

            // ── Today's Mission (Centerpiece) ──
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(600)) + slideInVertically(initialOffsetY = { 40 })
                ) {
                    PremiumCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(GoColors.AccentGold)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "PRIORITY MISSION",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoColors.TextFaint,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = todayTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = GoColors.TextPrimary
                            )
                            Spacer(Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(GoColors.Surface.copy(alpha = 0.5f))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = preview,
                                    fontFamily = CodeFont,
                                    fontSize = 14.sp,
                                    color = GoColors.AccentTeal
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            if (doneToday) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = GoColors.Success)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Mission Accomplished", color = GoColors.Success, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                TactileButton(
                                    text = "Initialize Sequence",
                                    onClick = onChallenge,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // ── Affirmation Enforcer (7:00 AM enforcement window) ──
            item {
                // Keep tomorrow's 7 AM exact alarm armed (idempotent).
                LaunchedEffect(Unit) { AffirmationScheduler.schedule(context) }

                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(GoColors.AccentGold)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "AFFIRMATION ENFORCER \u00B7 7:00 AM",
                                style = MaterialTheme.typography.labelMedium,
                                color = GoColors.TextFaint,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = home.affirmationText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = GoColors.TextPrimary
                        )
                        Spacer(Modifier.height(12.dp))
                        if (home.affirmationDone) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, tint = GoColors.Success)
                                Spacer(Modifier.width(8.dp))
                                Text("Affirmation written today", color = GoColors.Success, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            TactileButton(
                                text = "Write affirmation now",
                                onClick = {
                                    context.startActivity(
                                        android.content.Intent(context, AffirmationActivity::class.java)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = home.enforcementStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = GoColors.TextFaint
                            )
                        }
                    }
                }
            }

            // ── Intelligence Row (Stats) ──
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBentoItem(
                        label = "ACCURACY",
                        value = "${(accuracy * 100).toInt()}%",
                        color = GoColors.Primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatBentoItem(
                        label = "SNIPPETS",
                        value = "$snippets",
                        color = GoColors.AccentBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatBentoItem(
                        label = "WORDS",
                        value = "$words",
                        color = GoColors.AccentTeal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Concepts Teaser (Elite Access) ──
            item {
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "MASTER BLUEPRINT",
                            style = MaterialTheme.typography.labelMedium,
                            color = GoColors.TextFaint,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        val concepts = listOf("Concurrency", "Pipelines", "Context", "Channels")
                        concepts.forEach { concept ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Lock, null, tint = GoColors.TextFaint, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(concept, style = MaterialTheme.typography.bodyLarge, color = GoColors.TextMuted)
                                Spacer(Modifier.weight(1f))
                                Text("Premium", style = MaterialTheme.typography.labelSmall, color = GoColors.AccentGold)
                            }
                            HorizontalDivider(color = GoColors.Border.copy(alpha = 0.5f))
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Unlock the full Go architecture with Enforcer Pro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoColors.TextFaint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBentoItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    PremiumCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = GoColors.TextFaint)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun Pill(text: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun StreakCard(streak: Int, best: Int) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = GoColors.Card),
        border = BorderStroke(1.dp, GoColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GoColors.Streak.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Whatshot,
                    contentDescription = null,
                    tint = GoColors.Streak,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = if (streak > 0) "$streak-day streak" else "Start your streak",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoColors.TextPrimary
                )
                Text(
                    text = if (streak > 0)
                        "One snippet a day keeps your keyboard free"
                    else "Complete today's snippet to begin",
                    fontSize = 13.sp,
                    color = GoColors.TextMuted
                )
            }
            Spacer(Modifier.weight(1f))
            if (best > 1) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$best",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoColors.AccentGold
                    )
                    Text(text = "best", fontSize = 11.sp, color = GoColors.TextFaint)
                }
            }
        }
    }
}

@Composable
private fun TodayCard(
    title: String,
    preview: String,
    done: Boolean,
    onChallenge: () -> Unit
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = GoColors.Card),
        border = BorderStroke(
            1.dp,
            if (done) GoColors.Success.copy(alpha = 0.6f) else GoColors.Border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "TODAY'S SNIPPET",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = GoColors.TextFaint
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = GoColors.AccentGold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = preview,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = GoColors.AccentTeal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GoColors.Surface)
                    .padding(12.dp)
            )
            Spacer(Modifier.height(14.dp))
            if (done) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = GoColors.Success,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Completed today \u2014 keyboard unlocked",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GoColors.Success
                    )
                }
            } else {
                TactileButton(
                    text = "Start today's snippet",
                    onClick = onChallenge,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = GoColors.Card),
        border = BorderStroke(1.dp, GoColors.Border),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = GoColors.TextPrimary
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = GoColors.TextFaint
            )
        }
    }
}

@Composable
private fun StatsRow(snippets: Int, words: Int, accuracy: Float, best: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox(Icons.Filled.Code, "$snippets", "snippets", GoColors.AccentBlue, Modifier.weight(1f))
        StatBox(Icons.Filled.Keyboard, "$words", "words", GoColors.AccentTeal, Modifier.weight(1f))
        StatBox(Icons.Filled.GpsFixed, "${(accuracy * 100).toInt()}%", "accuracy", GoColors.AccentGold, Modifier.weight(1f))
        StatBox(Icons.Filled.EmojiEvents, "$best", "best streak", GoColors.Streak, Modifier.weight(1f))
    }
}

@Composable
private fun ConceptsCard() {
    val concepts = listOf("goroutines", "channels", "defer", "structs", "interfaces", "errors", "slices", "maps")
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = GoColors.Card),
        border = BorderStroke(1.dp, GoColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "MASTER THESE NEXT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = GoColors.TextFaint
            )
            Spacer(Modifier.height(10.dp))
            val chunked = remember(concepts) { concepts.chunked(4) }
            for (row in chunked) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in row) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoColors.Surface)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = GoColors.TextFaint,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = c,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = GoColors.TextMuted
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Deep-dive lessons unlock with the Pro plan.",
                fontSize = 12.sp,
                color = GoColors.TextFaint
            )
        }
    }
}
