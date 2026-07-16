package com.example.notebucket.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.notebucket.R
import com.example.notebucket.ai.BgeEmbedder
import com.example.notebucket.data.NoteBucketDatabase
import com.example.notebucket.data.NoteBucketRepository
import com.example.notebucket.data.SettingsRepository
import com.example.notebucket.sort.FolderRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val threshold: Float = FolderRouter.DEFAULT_THRESHOLD,
    val noteCount: Int = 0,
    val folderCount: Int = 0,
    val dbSizeHuman: String = "—",
    val modelLoaded: Boolean = false,
    val clearDone: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: NoteBucketRepository,
    private val settings: SettingsRepository,
    private val embedder: BgeEmbedder
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val thresholdFlow: StateFlow<Float> =
        settings.observeThreshold().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderRouter.DEFAULT_THRESHOLD)

    init {
        viewModelScope.launch {
            thresholdFlow.collect { t -> _state.value = _state.value.copy(threshold = t) }
        }
        viewModelScope.launch {
            repo.observeAllNotes().collect { notes ->
                _state.value = _state.value.copy(noteCount = notes.size)
            }
        }
        viewModelScope.launch {
            repo.observeFolders().collect { folders ->
                _state.value = _state.value.copy(folderCount = folders.size)
            }
        }
        viewModelScope.launch { refresh() }
    }

    fun onThresholdChange(value: Float) {
        _state.value = _state.value.copy(threshold = value)
        viewModelScope.launch { settings.setThreshold(value) }
    }

    fun reloadModel() {
        viewModelScope.launch {
            embedder.unload()
            embedder.loadModel()
            refresh()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                repo.clearAll()
                _state.value = _state.value.copy(clearDone = true)
                refresh()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "Failed to clear")
            }
        }
    }

    fun consumeClearDone() {
        _state.value = _state.value.copy(clearDone = false)
    }

    private suspend fun refresh() {
        val dbFile = context.getDatabasePath(NoteBucketDatabase.NAME)
        val sizeBytes = if (dbFile.exists()) dbFile.length() else 0L
        _state.value = _state.value.copy(
            dbSizeHuman = humanSize(sizeBytes),
            modelLoaded = embedder.isLoaded()
        )
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
        return "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.clearDone) {
        if (state.clearDone) {
            snackbarHostState.showSnackbar(context.getString(R.string.settings_clear_done))
            vm.consumeClearDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CollapsibleSection(
                title = stringResource(R.string.settings_section_storage),
                defaultExpanded = true
            ) {
                Text(stringResource(R.string.settings_storage_notes, state.noteCount))
                Text(stringResource(R.string.settings_storage_folders, state.folderCount))
                Text(stringResource(R.string.settings_storage_db, state.dbSizeHuman))
            }

            CollapsibleSection(
                title = stringResource(R.string.settings_section_model)
            ) {
                Text(
                    text = if (state.modelLoaded)
                        stringResource(R.string.settings_model_status_loaded)
                    else stringResource(R.string.settings_model_status_not_loaded)
                )
                OutlinedButton(onClick = { vm.reloadModel() }) {
                    Text(stringResource(R.string.settings_model_reload))
                }
            }

            CollapsibleSection(
                title = stringResource(R.string.settings_section_clear),
                headerColor = MaterialTheme.colorScheme.error
            ) {
                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_clear_data))
                }
            }

            CollapsibleSection(
                title = stringResource(R.string.settings_section_advanced)
            ) {
                Text(
                    text = stringResource(R.string.settings_threshold),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_threshold_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.threshold,
                    onValueChange = vm::onThresholdChange,
                    valueRange = 0f..1f
                )
                Text(
                    text = "Current: ${"%.2f".format(state.threshold)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_data)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    vm.clearAll()
                }) {
                    Text(stringResource(R.string.settings_clear_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.settings_clear_cancel))
                }
            }
        )
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    defaultExpanded: Boolean = false,
    headerColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = headerColor
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }
        if (expanded) {
            content()
        }
    }
}
