package com.vexiq.trinkettracker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vexiq.trinkettracker.ui.theme.VexGold
import com.vexiq.trinkettracker.ui.theme.VexRed
import com.vexiq.trinkettracker.ui.theme.VexRedDark
import com.vexiq.trinkettracker.viewmodel.ProgressState

@Composable
fun ProgressHeader(progress: ProgressState) {
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 600),
        label = "progress_animation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(VexRed, VexRedDark)
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Progress label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${progress.percentage}% Completed",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${progress.collected}/${progress.total} Teams",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress bar track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f))
        ) {
            // Filled portion
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(VexGold, VexGold.copy(alpha = 0.8f))
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (progress.total == 0) "Refresh to load teams" else "${progress.total - progress.collected} remaining",
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp
        )
    }
}
