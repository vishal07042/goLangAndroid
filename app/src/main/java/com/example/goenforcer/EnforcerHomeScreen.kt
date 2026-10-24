package com.example.goenforcer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.SelfImprovement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ─── Enforcer dashboard ────────────────────────────────────────────────
// The app's only entry surface: Affirmation Enforcer + Reading Enforcement.
// The legacy Go screens still exist in the codebase (Learn / Sprint / Path /
// Leader / Stats / Challenge) but are intentionally unreachable — this
// dashboard never routes to them.

@Composable
fun EnforcerHomeScreen() {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val home by vm.state.collectAsStateWithLifecycle()

    var docCount by remember { mutableStateOf(0) }
    var startedReads by remember { mutableStateOf(0) }

    // Refresh on every resume: returning from either enforcer immediately
    // shows the new state (affirmation windows, reading progress).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.refresh()
            val docs = ReadingStore.documents(context)
            docCount = docs.size
            startedReads = docs.count { ReadingStore.readWords(context, it.id) > 0 }
        }
    }

    // Keep tomorrow's 7 AM window armed (idempotent).
    LaunchedEffect(Unit) { AffirmationScheduler.schedule(context) }

    val todayLabel = SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date())
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    Scaffold(containerColor = GoColors.Background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AuroraBackground()

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = greeting.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = GoColors.AccentGold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Enforcer",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 34.sp,
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
                }

                item {
                    AffirmationEnforcerCard(
                        home = home,
                        onWrite = {
                            val pm = home.activePm == true
                            context.startActivity(
                                Intent(context, AffirmationActivity::class.java)
                                    .putExtra(AffirmationScheduler.EXTRA_PM, pm)
                            )
                        }
                    )
                }

                item {
                    ReadingEnforcerCard(
                        docCount = docCount,
                        startedReads = startedReads,
                        onOpen = {
                            context.startActivity(Intent(context, ReadingActivity::class.java))
                        }
                    )
                }

                item {
                    Text(
                        text = "Two habits. Every day. No shortcuts.",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoColors.TextFaint,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = GoColors.TextFaint,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun AffirmationEnforcerCard(home: HomeUiState, onWrite: () -> Unit) {
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("AFFIRMATION ENFORCER", GoColors.AccentGold)
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.SelfImprovement,
                    contentDescription = null,
                    tint = GoColors.AccentGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (home.activePm == null) "Today is complete" else "Your next session",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoColors.TextPrimary
                    )
                    Text(
                        text = home.windowLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = GoColors.TextFaint
                    )
                }
                if (home.activePm == null) {
                    Icon(Icons.Filled.CheckCircle, null, tint = GoColors.Success)
                }
            }

            if (home.affirmationText.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GoColors.Surface)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "\u201C${home.affirmationText}\u201D",
                        style = MaterialTheme.typography.bodyLarge,
                        color = GoColors.TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (home.activePm == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = GoColors.Success)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Both sessions written today",
                        color = GoColors.Success,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                TactileButton(
                    text = "Write affirmation now",
                    onClick = onWrite,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WindowPill(label = "7 AM", done = home.morningDone)
                    WindowPill(label = "7 PM", done = home.eveningDone)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = home.enforcementStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = GoColors.TextFaint
                )
            }
        }
    }
}

@Composable
private fun ReadingEnforcerCard(docCount: Int, startedReads: Int, onOpen: () -> Unit) {
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("READING ENFORCEMENT", GoColors.AccentTeal)
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = GoColors.AccentTeal,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Read in 30-word chunks",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoColors.TextPrimary
                    )
                    Text(
                        text = when {
                            docCount == 0 -> "Loading library\u2026"
                            startedReads == 0 -> "$docCount reads ready"
                            else -> "$docCount reads \u00B7 $startedReads started"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = GoColors.TextFaint
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Retype, read aloud, slide, drag & drop, or flash (RSVP). " +
                    "Every 30-word chunk is verified before you move on.",
                style = MaterialTheme.typography.bodyMedium,
                color = GoColors.TextMuted
            )
            Spacer(Modifier.height(18.dp))
            TactileButton(
                text = "Open reader",
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                topColor = GoColors.AccentTeal,
                bottomColor = Color(0xFF007A70),
                edgeColor = Color(0xFF007A70)
            )
        }
    }
}

@Composable
private fun WindowPill(label: String, done: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (done) GoColors.Success.copy(alpha = 0.15f) else GoColors.Surface)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Lock,
            contentDescription = null,
            tint = if (done) GoColors.Success else GoColors.TextFaint,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (done) "$label done" else label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (done) GoColors.Success else GoColors.TextMuted
        )
    }
}
