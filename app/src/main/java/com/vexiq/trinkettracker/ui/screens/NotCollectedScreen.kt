package com.vexiq.trinkettracker.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vexiq.trinkettracker.data.Team
import com.vexiq.trinkettracker.ui.theme.*
import com.vexiq.trinkettracker.viewmodel.ProgressState
import com.vexiq.trinkettracker.viewmodel.TeamViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotCollectedScreen(
    viewModel: TeamViewModel,
    progress: ProgressState
) {
    val context = LocalContext.current
    val teams by viewModel.notCollectedTeams.collectAsState()
    val searchQuery by viewModel.notCollectedSearch.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Track which team is pending camera capture
    var pendingTeam by remember { mutableStateOf<Team?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingTeam != null) {
            val file = photoFile
            val team = pendingTeam
            if (file != null && team != null && file.exists()) {
                viewModel.collectTeam(team.teamNumber, file.absolutePath)
            }
        }
        pendingTeam = null
        photoUri = null
        photoFile = null
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val team = pendingTeam
            if (team != null) {
                val (uri, file) = createPhotoFile(context, team.teamNumber)
                photoUri = uri
                photoFile = file
                if (uri != null) cameraLauncher.launch(uri)
            }
        } else {
            pendingTeam = null
        }
    }

    // File picker launcher for XLS upload
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                viewModel.loadTeamsFromFile(inputStream)
            }
        }
    }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error, uiState.successMessage) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearMessage()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessage()
        }
    }

    fun launchCamera(team: Team) {
        pendingTeam = team
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> {
                val (uri, file) = createPhotoFile(context, team.teamNumber)
                photoUri = uri
                photoFile = file
                if (uri != null) cameraLauncher.launch(uri)
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Progress header
            ProgressHeader(progress = progress)

            // Search bar + refresh row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setNotCollectedSearch(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search teams…", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setNotCollectedSearch("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VexRed,
                        unfocusedBorderColor = DividerColor,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    )
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Upload/Refresh button - now opens file picker
                FilledTonalButton(
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            )
                        )
                    },
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VexRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.FileOpen, contentDescription = "Upload XLS", modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Team count info bar
            AnimatedVisibility(visible = teams.isNotEmpty() || searchQuery.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "${teams.size} teams remaining"
                        else "${teams.size} results for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Team list
            when {
                uiState.isLoading && teams.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = VexRed)
                            Spacer(Modifier.height(12.dp))
                            Text("Loading teams…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                teams.isEmpty() && progress.total == 0 -> {
                    EmptyState(
                        icon = Icons.Default.UploadFile,
                        title = "No teams loaded",
                        subtitle = "Tap the upload button to load teams from a .xls file"
                    )
                }

                teams.isEmpty() && searchQuery.isNotEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No results",
                        subtitle = "No teams match \"$searchQuery\""
                    )
                }

                teams.isEmpty() && progress.total > 0 -> {
                    EmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = "All done! 🎉",
                        subtitle = "You've collected trinkets from all ${progress.total} teams!"
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(teams, key = { it.teamNumber }) { team ->
                            TeamListItem(
                                team = team,
                                onClick = { launchCamera(team) }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun TeamListItem(team: Team, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = team.teamNumber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = team.teamName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Take photo",
                modifier = Modifier.size(24.dp),
                tint = VexRed
            )
        }
    }
}

private fun createPhotoFile(context: Context, teamNumber: String): Pair<Uri?, File?> {
    val storageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "trinkets")
    if (!storageDir.exists()) storageDir.mkdirs()

    val photoFile = File.createTempFile(
        "trinket_${teamNumber}_",
        ".jpg",
        storageDir
    )

    val photoUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )

    return Pair(photoUri, photoFile)
}

@Composable
fun EmptyState(icon: androidx.compose.material.icons.Icons, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
