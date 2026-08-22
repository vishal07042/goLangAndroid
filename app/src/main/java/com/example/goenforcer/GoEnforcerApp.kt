package com.example.goenforcer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ChallengeData(
    val segments: List<Segment>,
    val session: Session
)

enum class NavTab(val icon: ImageVector, val label: String) {
    Learn(Icons.Filled.Code, "Learn"),
    Sprint(Icons.Filled.Bolt, "Sprint"),
    Path(Icons.Filled.GpsFixed, "Path"),
    Leader(Icons.Filled.EmojiEvents, "Leader"),
    Stats(Icons.Filled.Person, "Stats")
}

@Composable
fun GoEnforcerApp() {
    GoEnforcerTheme {
        var screen by rememberSaveable { mutableStateOf("main") }
        var currentTab by rememberSaveable { mutableStateOf(NavTab.Learn) }
        var selectedSnippet by remember { mutableStateOf<SnippetContent?>(null) }
        val context = LocalContext.current

        val dailySnippet = remember { ContentStore.getDailySnippet(context) }
        
        val activeSnippet = selectedSnippet ?: dailySnippet
        val challenge = remember(activeSnippet) {
            val segments = Core.decodeSegments(activeSnippet.rawCode)
            ChallengeData(segments, Core.newSession(segments))
        }

        if (screen == "challenge") {
            ChallengeScreen(
                segments = challenge.segments,
                initialSession = challenge.session,
                onComplete = { correct, wrong ->
                    Store.complete(
                        context,
                        snippetWords = challenge.session.words.size,
                        correctChars = correct,
                        wrongKeys = wrong
                    )
                },
                onBack = { 
                    screen = "main"
                    selectedSnippet = null
                }
            )
        } else {
            Scaffold(
                containerColor = GoColors.Background,
                bottomBar = {
                    BottomNavBar(
                        selectedTab = currentTab,
                        onTabSelected = { currentTab = it }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "tabTransition"
                    ) { targetTab ->
                        when (targetTab) {
                            NavTab.Learn -> HomeScreen(onChallenge = { screen = "challenge" })
                            NavTab.Sprint -> SprintScreen(onBack = { currentTab = NavTab.Learn })
                            NavTab.Path -> PathScreen(onSnippetSelected = { 
                                selectedSnippet = it
                                screen = "challenge"
                            })
                            NavTab.Leader -> LeaderboardScreen()
                            NavTab.Stats -> StatsScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.2f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "iconScale"
            )

            val color = if (selected) GoColors.Primary else GoColors.TextFaint
            val bgColor = if (selected) GoColors.Primary.copy(alpha = 0.1f) else Color.Transparent

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bgColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (!selected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTabSelected(tab)
                            }
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = color,
                    modifier = Modifier.size(24.dp).scale(scale)
                )
                
                if (selected) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
