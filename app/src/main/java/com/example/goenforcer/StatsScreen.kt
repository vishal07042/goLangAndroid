package com.example.goenforcer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val streak = Store.streak(context)
    val words = Store.totalWords(context)
    val accuracy = (Store.accuracy(context) * 100).toInt()

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Header
            GopherMascot(mood = MascotMood.Success, skin = Color(0xFF569CD6))
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Pro Gopher",
                style = MaterialTheme.typography.titleLarge,
                color = GoColors.TextPrimary
            )
            Text(
                text = "Level 12 Architect",
                style = MaterialTheme.typography.bodyMedium,
                color = GoColors.TextMuted
            )
            
            Spacer(Modifier.height(32.dp))
            
            // Stats Grid
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    icon = Icons.Filled.LocalFireDepartment,
                    value = "$streak",
                    label = "Day Streak",
                    color = GoColors.Streak,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.TaskAlt,
                    value = "$accuracy%",
                    label = "Accuracy",
                    color = GoColors.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            StatCard(
                icon = Icons.Filled.Speed,
                value = "$words",
                label = "Total Words Enforced",
                color = GoColors.AccentBlue,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(32.dp))
            
            TactileButton(
                text = "Upgrade to Pro",
                onClick = { /* Upgrade logic */ },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    PremiumCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoColors.TextPrimary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = GoColors.TextFaint
                )
            }
        }
    }
}
