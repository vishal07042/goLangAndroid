package com.example.goenforcer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Leader(val name: String, val streak: Int, val accuracy: Int)

@Composable
fun LeaderboardScreen() {
    val leaders = listOf(
        Leader("EliteGopher", 42, 98),
        Leader("ConcurrencyKing", 38, 95),
        Leader("SliceMaster", 31, 99),
        Leader("DeferDude", 24, 92),
        Leader("MapMaker", 19, 88)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                text = "Global Leaders",
                style = MaterialTheme.typography.titleLarge,
                color = GoColors.TextPrimary
            )
            Text(
                text = "The most dedicated Go enforcers.",
                style = MaterialTheme.typography.bodyMedium,
                color = GoColors.TextMuted
            )
            
            Spacer(Modifier.height(24.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(leaders) { index, leader ->
                    LeaderRow(index + 1, leader)
                }
            }
        }
    }
}

@Composable
fun LeaderRow(rank: Int, leader: Leader) {
    PremiumCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (rank <= 3) GoColors.AccentGold.copy(alpha = 0.2f) else GoColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) GoColors.AccentGold else GoColors.TextMuted
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = leader.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = GoColors.TextPrimary
                )
                Text(
                    text = "${leader.accuracy}% accuracy",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoColors.TextFaint
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = GoColors.Streak,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${leader.streak}d",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoColors.Streak
                )
            }
        }
    }
}
