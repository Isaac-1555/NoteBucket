package com.example.notebucket.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.notebucket.R
import com.example.notebucket.data.NoteBucketRepository
import com.example.notebucket.sort.Folder
import com.example.notebucket.sort.SearchResult
import com.example.notebucket.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DatePreset(val label: String, val millisBack: Long?) {
    ALL_TIME("All time", null),
    PAST_WEEK("Past week", 7L * 24 * 60 * 60 * 1000),
    PAST_MONTH("Past month", 30L * 24 * 60 * 60 * 1000),
    PAST_YEAR("Past year", 365L * 24 * 60 * 60 * 1000)
}

data class SearchUiState(
    val query: String = "",
    val folderFilterId: String? = null,
    val datePreset: DatePreset = DatePreset.ALL_TIME,
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: NoteBucketRepository,
    private val router: com.example.notebucket.sort.FolderRouter
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val folders: StateFlow<List<Folder>> =
        repo.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(text: String) {
        _state.value = _state.value.copy(query = text)
    }

    fun setFolderFilter(folderId: String?) {
        _state.value = _state.value.copy(folderFilterId = folderId)
    }

    fun setDatePreset(preset: DatePreset) {
        _state.value = _state.value.copy(datePreset = preset)
    }

    fun search() {
        val s = _state.value
        if (s.query.isBlank()) return
        val now = System.currentTimeMillis()
        val dateFrom = s.datePreset.millisBack?.let { now - it }
        _state.value = s.copy(isSearching = true, error = null)
        viewModelScope.launch {
            try {
                val results = router.search(
                    query = s.query.trim(),
                    topK = 5,
                    folderFilter = s.folderFilterId,
                    dateFrom = dateFrom,
                    dateTo = null
                )
                _state.value = _state.value.copy(results = results, isSearching = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(isSearching = false, error = t.message ?: "Search failed")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavHostController) {
    val vm: SearchViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val folders by vm.folders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() })
            )

            Button(
                onClick = { vm.search() },
                enabled = state.query.isNotBlank() && !state.isSearching,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSearching) "Searching…" else stringResource(R.string.action_search))
            }

            Text("Folder", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.folderFilterId == null,
                        onClick = { vm.setFolderFilter(null) },
                        label = { Text("All") }
                    )
                }
                listItems(folders, key = { it.id }) { folder ->
                    FilterChip(
                        selected = state.folderFilterId == folder.id,
                        onClick = { vm.setFolderFilter(folder.id) },
                        label = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            Text("Date", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listItems(DatePreset.entries.toList()) { preset ->
                    FilterChip(
                        selected = state.datePreset == preset,
                        onClick = { vm.setDatePreset(preset) },
                        label = { Text(preset.label) }
                    )
                }
            }

            if (state.error != null) {
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.results.isEmpty() && !state.isSearching && state.query.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_empty_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    listItems(state.results, key = { it.note.id }) { result ->
                        val folderName = folders.find { it.id == result.note.folderId }?.name ?: "?"
                        SearchResultCard(
                            text = result.note.text,
                            folderName = folderName,
                            onClick = { navController.navigate(Routes.noteDetail(result.note.id)) }
                        )
                    }
                    if (state.results.isEmpty() && !state.isSearching) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Text(stringResource(R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    text: String,
    folderName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
