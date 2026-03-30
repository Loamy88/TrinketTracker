package com.vexiq.trinkettracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vexiq.trinkettracker.ui.screens.CollectedScreen
import com.vexiq.trinkettracker.ui.screens.NotCollectedScreen
import com.vexiq.trinkettracker.ui.theme.TrinketTrackerTheme
import com.vexiq.trinkettracker.ui.theme.VexGold
import com.vexiq.trinkettracker.ui.theme.VexRed
import com.vexiq.trinkettracker.viewmodel.TeamViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrinketTrackerTheme {
                TrinketTrackerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrinketTrackerApp() {
    val viewModel: TeamViewModel = viewModel()
    val progress by viewModel.progress.collectAsState()
    val collectedTeams by viewModel.collectedTeams.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        TabItem(
            title = "Not Collected",
            selectedIcon = Icons.Filled.RadioButtonUnchecked,
            unselectedIcon = Icons.Outlined.RadioButtonUnchecked,
        ),
        TabItem(
            title = "Collected",
            selectedIcon = Icons.Filled.CheckCircle,
            unselectedIcon = Icons.Outlined.CheckCircle,
            badge = if (collectedTeams.isNotEmpty()) collectedTeams.size.toString() else null
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Trinket Tracker",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "VEX IQ 2026 MS Worlds",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VexRed,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            if (tab.badge != null) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = VexGold,
                                            contentColor = Color.Black
                                        ) {
                                            Text(
                                                text = tab.badge,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VexRed,
                            selectedTextColor = VexRed,
                            indicatorColor = VexRed.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> NotCollectedScreen(viewModel = viewModel, progress = progress)
                1 -> CollectedScreen(viewModel = viewModel, progress = progress)
            }
        }
    }
}

data class TabItem(
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val badge: String? = null
)
