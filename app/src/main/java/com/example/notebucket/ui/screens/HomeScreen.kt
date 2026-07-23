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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.example.notebucket.R
import com.example.notebucket.data.NoteBucketRepository
import com.example.notebucket.data.dao.FolderCount
import com.example.notebucket.sort.FolderRouter
import com.example.notebucket.ui.nav.Routes
import com.example.notebucket.ui.theme.FolderPalette
import com.example.notebucket.ui.theme.glow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderTile(
    val id: String,
    val name: String,
    val noteCount: Int,
    val color: String
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: NoteBucketRepository,
    private val router: FolderRouter
) : ViewModel() {

    val tiles: StateFlow<List<FolderTile>> =
        combine(repo.observeVisibleFolders(), repo.observeNoteCountsByFolder()) { folders, counts ->
            val countMap = counts.associate { it.folderId to it.cnt }
            folders.map { f ->
                FolderTile(
                    id = f.id,
                    name = f.name,
                    noteCount = countMap[f.id] ?: 0,
                    color = f.color
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            router.seedDefaultFolders()
        }
    }

    fun createFolder(name: String, color: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            router.createFolder(name.trim(), color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val vm: HomeViewModel = hiltViewModel()
    val tiles by vm.tiles.collectAsState()
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.home_create_folder))
                    }
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.NOTE_INPUT) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_new_note))
            }
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { navController.navigate(Routes.SEARCH) }
            ) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    placeholder = { Text(stringResource(R.string.home_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (tiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.home_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.home_folders_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tiles, key = { it.id }) { tile ->
                        FolderTileCard(tile = tile, onClick = {
                            navController.navigate(Routes.folderDetail(tile.id))
                        })
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, color ->
                vm.createFolder(name, color)
                showCreateFolderDialog = false
            }
        )
    }
}

@Composable
private fun FolderTileCard(tile: FolderTile, onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val folderColor = remember(tile.color, isDark) {
        FolderPalette.resolve(tile.color, isDark)
    }

    val isUnsorted = tile.name.equals("Unsorted", ignoreCase = true) && tile.noteCount > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .then(if (isUnsorted) Modifier.glow(folderColor) else Modifier)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(folderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${tile.noteCount} notes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val defaultColor = FolderPalette.all[0].key
    var selectedColor by remember { mutableStateOf(defaultColor) }
    val isDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_folder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.create_folder_hint)) },
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
                enabled = text.isNotBlank()
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
