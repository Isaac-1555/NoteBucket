package com.example.notebucket.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.notebucket.R
import com.example.notebucket.data.NoteBucketRepository
import com.example.notebucket.sort.Folder
import com.example.notebucket.sort.Note
import com.example.notebucket.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderDetailUiState(
    val folder: Folder? = null,
    val notes: List<Note> = emptyList(),
    val isWallpaperFolder: Boolean = false
)

@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    private val repo: NoteBucketRepository,
    savedState: SavedStateHandle
) : ViewModel() {

    private val folderId: String = savedState.get<String>(Routes.FOLDER_DETAIL_ARG).orEmpty()

    val state: StateFlow<FolderDetailUiState> =
        combine(repo.observeFolder(folderId), repo.observeNotesByFolder(folderId)) { folder, notes ->
            FolderDetailUiState(
                folder = folder,
                notes = notes,
                isWallpaperFolder = detectWallpaper(folder?.name, notes)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderDetailUiState())

    fun rename(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repo.renameFolder(folderId, newName.trim()) }
    }

    private fun detectWallpaper(name: String?, notes: List<Note>): Boolean {
        val nameHit = name?.let {
            Regex("(?i)wallpaper|image|photo|pic").containsMatchIn(it)
        } ?: false
        if (nameHit) return true
        if (notes.isEmpty()) return false
        val imageHits = notes.count { n ->
            Regex("(?i)(https?://\\S+\\.(png|jpe?g|webp|gif|bmp)|wallpaper)").containsMatchIn(n.text)
        }
        return imageHits * 2 >= notes.size
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(navController: NavHostController, folderId: String) {
    val vm: FolderDetailViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.folder?.name ?: "",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.folder_detail_rename))
                    }
                }
            )
        }
    ) { padding ->
        if (state.notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.folder_detail_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (state.isWallpaperFolder) {
            WallpaperGrid(
                notes = state.notes,
                contentPadding = padding,
                onClick = { id -> navController.navigate(Routes.noteDetail(id)) }
            )
        } else {
            NotesList(
                notes = state.notes,
                contentPadding = padding,
                onClick = { id -> navController.navigate(Routes.noteDetail(id)) }
            )
        }
    }

    if (showRenameDialog) {
        RenameFolderDialog(
            initial = state.folder?.name ?: "",
            onDismiss = { showRenameDialog = false },
            onConfirm = {
                vm.rename(it)
                showRenameDialog = false
            }
        )
    }
}

@Composable
private fun NotesList(
    notes: List<Note>,
    contentPadding: PaddingValues,
    onClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listItems(notes, key = { it.id }) { note ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onClick(note.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = note.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTimestamp(note.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        item { Box(modifier = Modifier.padding(16.dp)) {} }
    }
}

@Composable
private fun WallpaperGrid(
    notes: List<Note>,
    contentPadding: PaddingValues,
    onClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        gridItems(notes, key = { it.id }) { note ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(note.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = note.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameFolderDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_detail_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && text != initial
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

internal fun formatTimestamp(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}
