package com.example.goenforcer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SprintScreen(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = GoColors.AccentGold,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Syntax Sprint",
                style = MaterialTheme.typography.titleLarge,
                color = GoColors.TextPrimary
            )
            Text(
                text = "Type one complex line as fast as possible.",
                style = MaterialTheme.typography.bodyMedium,
                color = GoColors.TextMuted,
                modifier = Modifier.padding(horizontal = 40.dp),
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(40.dp))
            
            TactileButton(
                text = "Start Sprint",
                onClick = { /* Sprint Logic */ },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
