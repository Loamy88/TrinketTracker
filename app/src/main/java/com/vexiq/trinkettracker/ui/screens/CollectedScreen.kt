package com.vexiq.trinkettracker.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.core.content.ContextCompat
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

    // ── Photo viewer ────────────────────────────────────────────────────────────
    var viewerTeam by remember { mutableStateOf<Team?>(null) }

    // ── Retake camera (from viewer dialog) ───────────────────────────────────────
    var retakeTeam by remember { mutableStateOf<Team?>(null) }
    var retakePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var retakePhotoFile by remember { mutableStateOf<File?>(null) }

    val retakeCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val file = retakePhotoFile
            val team = retakeTeam
            if (file != null && team != null && file.exists()) {
                viewModel.retakeTeamPhoto(team.teamNumber, file.absolutePath)
            }
        }
        retakeTeam = null
        retakePhotoUri = null
        retakePhotoFile = null
    }

    val retakePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val team = retakeTeam ?: return@rememberLauncherForActivityResult
            val (uri, file) = createPhotoFile(context, team.teamNumber)
            retakePhotoUri = uri
            retakePhotoFile = file
            if (uri != null) retakeCameraLauncher.launch(uri)
        } else {
            retakeTeam = null
        }
    }

    fun launchRetakeCamera(team: Team) {
        retakeTeam = team
        viewerTeam = null   // close viewer first
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> {
                val (uri, file) = createPhotoFile(context, team.teamNumber)
                retakePhotoUri = uri
                retakePhotoFile = file
                if (uri != null) retakeCameraLauncher.launch(uri)
            }
            else -> retakePermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Selection mode ───────────────────────────────────────────────────────────
    var selectionMode by remember { mutableStateOf(false) }
    var selectedNumbers by remember { mutableStateOf(setOf<String>()) }

    // Exit selection mode whenever teams list changes to avoid stale selection
    val teamNumbers = teams.map { it.teamNumber }.toSet()
    LaunchedEffect(teamNumbers) {
        selectedNumbers = selectedNumbers.intersect(teamNumbers)
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedNumbers = emptySet()
    }

    fun toggleSelection(teamNumber: String) {
        selectedNumbers = if (selectedNumbers.contains(teamNumber))
            selectedNumbers - teamNumber
        else
            selectedNumbers + teamNumber
    }

    // ── Remove confirmation dialog ────────────────────────────────────────────────
    var showRemoveDialog by remember { mutableStateOf(false) }
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorRed) },
            title = { Text("Remove ${selectedNumbers.size} team${if (selectedNumbers.size != 1) "s" else ""}?") },
            text = {
                Text(
                    "The selected teams will be moved back to Not Collected. " +
                            "Their photos will remain on the device but must be retaken.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeTeams(selectedNumbers.toList())
                        exitSelectionMode()
                        showRemoveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Remove") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Photo viewer dialog ───────────────────────────────────────────────────────
    viewerTeam?.let { team ->
        PhotoViewerDialog(
            team = team,
            onDismiss = { viewerTeam = null },
            onRetake = { launchRetakeCamera(team) }
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Progress header
            ProgressHeader(progress = progress)

            // ── Toolbar row (search + folder button + select toggle) ─────────────
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
                    placeholder = {
                        Text(
                            "Search collected…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
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

                Spacer(modifier = Modifier.width(8.dp))

                // Open folder
                FilledTonalIconButton(
                    onClick = { openTrinketFolder(context) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = SuccessGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Open photos folder")
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Select toggle
                FilledTonalIconButton(
                    onClick = {
                        if (selectionMode) exitSelectionMode()
                        else selectionMode = true
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (selectionMode) VexRed else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (selectionMode) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (selectionMode) Icons.Default.Close else Icons.Default.PlaylistAddCheck,
                        contentDescription = if (selectionMode) "Exit selection" else "Select teams"
                    )
                }
            }

            // ── Selection mode action bar ─────────────────────────────────────────
            AnimatedVisibility(
                visible = selectionMode,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Selection count
                        Text(
                            text = "${selectedNumbers.size} of ${teams.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Select All
                            OutlinedButton(
                                onClick = { selectedNumbers = teams.map { it.teamNumber }.toSet() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f))
                            ) {
                                Icon(
                                    Icons.Default.SelectAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("All", fontSize = 12.sp)
                            }

                            // Deselect All
                            OutlinedButton(
                                onClick = { selectedNumbers = emptySet() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f))
                            ) {
                                Icon(
                                    Icons.Default.Deselect,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("None", fontSize = 12.sp)
                            }

                            // Remove
                            Button(
                                onClick = {
                                    if (selectedNumbers.isNotEmpty()) showRemoveDialog = true
                                },
                                enabled = selectedNumbers.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ErrorRed,
                                    contentColor = Color.White,
                                    disabledContainerColor = ErrorRed.copy(alpha = 0.35f),
                                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Remove", fontSize = 12.sp)
                            }

                            // Close
                            OutlinedButton(
                                onClick = { exitSelectionMode() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f))
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Close", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── Count label ───────────────────────────────────────────────────────
            AnimatedVisibility(visible = teams.isNotEmpty() || searchQuery.isNotEmpty()) {
                Text(
                    text = if (searchQuery.isBlank()) "${teams.size} trinkets collected"
                    else "${teams.size} results for \"$searchQuery\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp)
                )
            }

            // ── Content ───────────────────────────────────────────────────────────
            when {
                teams.isEmpty() && searchQuery.isNotEmpty() -> EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No results",
                    subtitle = "No collected teams match \"$searchQuery\""
                )

                teams.isEmpty() -> EmptyState(
                    icon = Icons.Default.PhotoCamera,
                    title = "No trinkets yet",
                    subtitle = "Tap a team in \"Not Collected\" and take a photo to get started!"
                )

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(teams, key = { it.teamNumber }) { team ->
                            val isSelected = selectedNumbers.contains(team.teamNumber)
                            CollectedTeamItem(
                                team = team,
                                selectionMode = selectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (selectionMode) toggleSelection(team.teamNumber)
                                    else viewerTeam = team
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedNumbers = setOf(team.teamNumber)
                                    }
                                }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Collected team card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectedTeamItem(
    team: Team,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) SuccessGreen else Color.Transparent,
        animationSpec = tween(150),
        label = "border"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.97f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                SuccessGreen.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail or selection checkbox
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Selection overlay
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isSelected) SuccessGreen.copy(alpha = 0.55f)
                                else Color.Black.copy(alpha = 0.35f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle
                            else Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
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
                    Text(
                        text = "Collected ${
                            SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(ts))
                        }",
                        fontSize = 11.sp,
                        color = SuccessGreenLight
                    )
                }
            }

            // Right icon — checkmark in normal mode, nothing extra in selection mode
            if (!selectionMode) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Collected",
                        tint = SuccessGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Full-screen photo viewer dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PhotoViewerDialog(
    team: Team,
    onDismiss: () -> Unit,
    onRetake: () -> Unit
) {
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
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Top bar ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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

                // ── Photo ─────────────────────────────────────────────────────
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
                            contentDescription = "Trinket for team ${team.teamNumber}",
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
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Photo not found",
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // ── Bottom bar — metadata + retake button ─────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    team.collectedAt?.let { ts ->
                        Text(
                            text = "Captured ${
                                SimpleDateFormat(
                                    "MMMM d, yyyy 'at' h:mm a",
                                    Locale.US
                                ).format(Date(ts))
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                    team.photoPath?.let { path ->
                        Text(
                            text = File(path).name,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.35f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Retake button
                    Button(
                        onClick = onRetake,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Retake Photo", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

fun openTrinketFolder(context: Context) {
    try {
        val picturesDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "TrinketTracker"
        )
        if (!picturesDir.exists()) picturesDir.mkdirs()

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(picturesDir.toURI().toString()), "resource/folder")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Trinket photos are saved to:\n${picturesDir.absolutePath}"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Photos location").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    } catch (_: Exception) { }
}
