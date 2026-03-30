package com.vexiq.trinkettracker.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vexiq.trinkettracker.data.Team
import com.vexiq.trinkettracker.ui.theme.*
import com.vexiq.trinkettracker.viewmodel.ProgressState
import com.vexiq.trinkettracker.viewmodel.TeamViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectedScreen(
    viewModel: TeamViewModel,
    progress: ProgressState
) {
    val context = LocalContext.current
    val teams by viewModel.collectedTeams.collectAsState()
    val searchQuery by viewModel.collectedSearch.collectAsState()

    var selectedTeam by remember { mutableStateOf<Team?>(null) }

    // Full-screen photo dialog
    selectedTeam?.let { team ->
        PhotoViewerDialog(
            team = team,
            onDismiss = { selectedTeam = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Progress header
        ProgressHeader(progress = progress)

        // Search bar + folder link row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setCollectedSearch(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search collected…", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setCollectedSearch("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SuccessGreen,
                    unfocusedBorderColor = DividerColor,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Open folder button
            FilledTonalButton(
                onClick = { openTrinketFolder(context) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = SuccessGreen,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Open folder", modifier = Modifier.size(20.dp))
            }
        }

        // Count label
        AnimatedVisibility(visible = teams.isNotEmpty() || searchQuery.isNotEmpty()) {
            Text(
                text = if (searchQuery.isBlank()) "${teams.size} trinkets collected"
                else "${teams.size} results for \"$searchQuery\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 6.dp)
            )
        }

        // Content
        when {
            teams.isEmpty() && searchQuery.isNotEmpty() -> {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No results",
                    subtitle = "No collected teams match \"$searchQuery\""
                )
            }

            teams.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.PhotoCamera,
                    title = "No trinkets yet",
                    subtitle = "Tap on a team in the \"Not Collected\" tab and take a photo to get started!"
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(teams, key = { it.teamNumber }) { team ->
                        CollectedTeamItem(
                            team = team,
                            onClick = { selectedTeam = team }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun CollectedTeamItem(team: Team, onClick: () -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val photoFile = team.photoPath?.let { File(it) }
                if (photoFile != null && photoFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Trinket photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = team.teamName.ifBlank { "Unknown Team" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Team ${team.teamNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                team.collectedAt?.let { ts ->
                    val timeStr = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(ts))
                    Text(
                        text = "Collected $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreenLight,
                        fontSize = 11.sp
                    )
                }
            }

            // Checkmark badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Collected",
                    tint = SuccessGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun PhotoViewerDialog(team: Team, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = team.teamName.ifBlank { "Unknown Team" },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Team ${team.teamNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Full-screen photo
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val photoFile = team.photoPath?.let { File(it) }
                    if (photoFile != null && photoFile.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photoFile)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Trinket photo for team ${team.teamNumber}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Photo not found",
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Bottom bar with date and path info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    team.collectedAt?.let { ts ->
                        Text(
                            text = "Captured ${SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.US).format(Date(ts))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    team.photoPath?.let { path ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = File(path).name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

fun openTrinketFolder(context: Context) {
    try {
        val picturesDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "TrinketTracker"
        )
        if (!picturesDir.exists()) picturesDir.mkdirs()

        // Try to open via a files app intent
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(picturesDir.toURI().toString()), "resource/folder")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        // Fallback: open the generic file manager
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Show path via share
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Trinket photos are saved to:\n${picturesDir.absolutePath}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Photos saved to").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    } catch (e: Exception) {
        // Silently handle
    }
}
