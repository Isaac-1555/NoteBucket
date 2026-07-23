package com.example.notebucket.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.notebucket.sort.FolderRouter
import com.example.notebucket.sort.Note
import com.example.notebucket.ui.nav.Routes
import com.example.notebucket.ui.theme.FolderPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val router: FolderRouter,
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

    val allFolders: StateFlow<List<Folder>> =
        repo.observeVisibleFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    fun rename(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { router.renameFolder(folderId, newName.trim()) }
    }

    fun updateColor(color: String) {
        viewModelScope.launch { repo.updateFolderColor(folderId, color) }
    }

    fun enterSelectMode() { _isSelectMode.value = true }
    fun exitSelectMode() { _isSelectMode.value = false; _selectedIds.value = emptySet() }
    fun toggleSelection(id: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().also {
            if (!it.add(id)) it.remove(id)
        }
    }
    fun selectAll(notes: List<Note>) { _selectedIds.value = notes.map { it.id }.toSet() }

    fun moveSelected(toFolderId: String, onDone: () -> Unit) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) { onDone(); return }
        viewModelScope.launch {
            router.bulkMove(ids, toFolderId)
            exitSelectMode()
            onDone()
        }
    }

    fun deleteSelected(onDone: () -> Unit) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) { onDone(); return }
        viewModelScope.launch {
            router.bulkDelete(ids)
            exitSelectMode()
            onDone()
        }
    }

    fun deleteFolder(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteFolder(folderId)
            onDone()
        }
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
    val isSelectMode by vm.isSelectMode.collectAsState()
    val selectedIds by vm.selectedIds.collectAsState()
    val allFolders by vm.allFolders.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val folderColor = remember(state.folder?.color, isDark) {
        FolderPalette.resolve(state.folder?.color ?: "teal", isDark)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSelectMode) {
                            stringResource(R.string.folder_detail_selected_count, selectedIds.size)
                        } else {
                            state.folder?.name ?: ""
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectMode) vm.exitSelectMode() else navController.popBackStack()
                    }) {
                        Icon(
                            if (isSelectMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (isSelectMode) {
                        IconButton(onClick = { vm.selectAll(state.notes) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.folder_detail_select_all))
                        }
                        IconButton(
                            onClick = { showMoveDialog = true },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = stringResource(R.string.folder_detail_move_selected))
                        }
                        IconButton(
                            onClick = { showDeleteSelectedDialog = true },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.folder_detail_delete_selected))
                        }
                    } else {
                        IconButton(onClick = { vm.enterSelectMode() }, enabled = state.notes.isNotEmpty()) {
                            Icon(Icons.Default.Checklist, contentDescription = stringResource(R.string.folder_detail_select))
                        }
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.folder_detail_rename))
                        }
                        IconButton(onClick = { showDeleteFolderDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.folder_detail_delete_folder))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(folderColor)
            )

            if (state.notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                    selectedIds = selectedIds,
                    isSelectMode = isSelectMode,
                    contentPadding = PaddingValues(top = 0.dp),
                    onClick = { id ->
                        if (isSelectMode) vm.toggleSelection(id) else navController.navigate(Routes.noteDetail(id))
                    }
                )
            } else {
                NotesList(
                    notes = state.notes,
                    selectedIds = selectedIds,
                    isSelectMode = isSelectMode,
                    contentPadding = PaddingValues(top = 0.dp),
                    onClick = { id ->
                        if (isSelectMode) vm.toggleSelection(id) else navController.navigate(Routes.noteDetail(id))
                    }
                )
            }
        }
    }

    if (showRenameDialog) {
        RenameFolderDialog(
            initial = state.folder?.name ?: "",
            initialColor = state.folder?.color ?: "teal",
            onDismiss = { showRenameDialog = false },
            onConfirm = { name, color ->
                vm.rename(name)
                vm.updateColor(color)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteFolderDialog) {
        if (state.notes.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showDeleteFolderDialog = false },
                title = { Text(stringResource(R.string.folder_detail_delete_folder)) },
                text = { Text(stringResource(R.string.folder_detail_delete_folder_nonempty, state.notes.size)) },
                confirmButton = {
                    TextButton(onClick = { showDeleteFolderDialog = false }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showDeleteFolderDialog = false },
                title = { Text(stringResource(R.string.folder_detail_delete_folder)) },
                text = { Text(stringResource(R.string.folder_detail_delete_folder_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteFolderDialog = false
                        vm.deleteFolder { navController.popBackStack() }
                    }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteFolderDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text(stringResource(R.string.folder_detail_move_selected)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    listItems(allFolders.filter { it.id != folderId }, key = { it.id }) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.moveSelected(folder.id) { showMoveDialog = false }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Text(
                                text = "  " + folder.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.folder_detail_delete_selected)) },
            text = { Text(stringResource(R.string.folder_detail_delete_selected_confirm, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedDialog = false
                    vm.deleteSelected {}
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun NotesList(
    notes: List<Note>,
    selectedIds: Set<String>,
    isSelectMode: Boolean,
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
                    containerColor = if (note.id in selectedIds)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    if (isSelectMode) {
                        Checkbox(
                            checked = note.id in selectedIds,
                            onCheckedChange = { onClick(note.id) }
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
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
        }
        item { Box(modifier = Modifier.padding(16.dp)) {} }
    }
}

@Composable
private fun WallpaperGrid(
    notes: List<Note>,
    selectedIds: Set<String>,
    isSelectMode: Boolean,
    contentPadding: PaddingValues,
    onClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = 8.dp,
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
                    containerColor = if (note.id in selectedIds)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (isSelectMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = note.id in selectedIds,
                                onCheckedChange = { onClick(note.id) }
                            )
                        }
                    }
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
    initialColor: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    val isDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_detail_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.folder_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FolderPalette.all) { color ->
                        val bg = if (isDark) color.dark else color.light
                        val isSelected = color.key == selectedColor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = color.key }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text, selectedColor) },
                enabled = text.isNotBlank() && text != initial || selectedColor != initialColor
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
