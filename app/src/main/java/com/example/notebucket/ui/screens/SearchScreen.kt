package com.example.notebucket.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val filtersExpanded: Boolean = false,
    val useCustomDateRange: Boolean = false,
    val customDateFromMillis: Long? = null,
    val customDateToMillis: Long? = null,
    val results: List<SearchResult> = emptyList(),
    val resultCount: Int = 0,
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
        _state.value = _state.value.copy(datePreset = preset, useCustomDateRange = false)
    }

    fun toggleFilters() {
        _state.value = _state.value.copy(filtersExpanded = !_state.value.filtersExpanded)
    }

    fun setUseCustomDateRange(enabled: Boolean) {
        _state.value = _state.value.copy(useCustomDateRange = enabled)
    }

    fun setCustomDateFrom(millis: Long?) {
        _state.value = _state.value.copy(customDateFromMillis = millis)
    }

    fun setCustomDateTo(millis: Long?) {
        _state.value = _state.value.copy(customDateToMillis = millis)
    }

    fun clearFilters() {
        _state.value = _state.value.copy(
            folderFilterId = null,
            datePreset = DatePreset.ALL_TIME,
            useCustomDateRange = false,
            customDateFromMillis = null,
            customDateToMillis = null
        )
    }

    private fun hasActiveFilters(): Boolean {
        val s = _state.value
        return s.folderFilterId != null ||
            s.datePreset != DatePreset.ALL_TIME ||
            s.useCustomDateRange
    }

    fun search() {
        val s = _state.value
        if (s.query.isBlank()) return
        val now = System.currentTimeMillis()
        val dateFrom = if (s.useCustomDateRange) {
            s.customDateFromMillis
        } else {
            s.datePreset.millisBack?.let { now - it }
        }
        val dateTo = if (s.useCustomDateRange) s.customDateToMillis else null
        _state.value = s.copy(isSearching = true, error = null)
        viewModelScope.launch {
            try {
                val results = router.search(
                    query = s.query.trim(),
                    topK = 5,
                    folderFilter = s.folderFilterId,
                    dateFrom = dateFrom,
                    dateTo = dateTo
                )
                _state.value = _state.value.copy(
                    results = results,
                    resultCount = results.size,
                    isSearching = false
                )
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

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

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

            // ── Filters toggle ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.toggleFilters() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_filters),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (state.filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (state.filtersExpanded) "Collapse filters" else "Expand filters",
                    modifier = Modifier.size(20.dp)
                )
            }

            // ── Collapsible filter content ───────────────────────────────
            AnimatedVisibility(
                visible = state.filtersExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Folder filter
                    Text(stringResource(R.string.search_filter_folder), style = MaterialTheme.typography.labelMedium)
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

                    // Date filter
                    Text(stringResource(R.string.search_filter_date), style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listItems(DatePreset.entries.toList()) { preset ->
                            FilterChip(
                                selected = state.datePreset == preset && !state.useCustomDateRange,
                                onClick = { vm.setDatePreset(preset) },
                                label = { Text(preset.label) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = state.useCustomDateRange,
                                onClick = { vm.setUseCustomDateRange(true) },
                                label = { Text(stringResource(R.string.search_filter_custom_range)) }
                            )
                        }
                    }

                    // Custom date range fields
                    AnimatedVisibility(visible = state.useCustomDateRange) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = state.customDateFromMillis?.let { dateFormat.format(Date(it)) } ?: "",
                                    onValueChange = {},
                                    label = { Text(stringResource(R.string.search_filter_date_from)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showFromPicker = true },
                                    readOnly = true,
                                    enabled = false,
                                    trailingIcon = {
                                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                OutlinedTextField(
                                    value = state.customDateToMillis?.let { dateFormat.format(Date(it)) } ?: "",
                                    onValueChange = {},
                                    label = { Text(stringResource(R.string.search_filter_date_to)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showToPicker = true },
                                    readOnly = true,
                                    enabled = false,
                                    trailingIcon = {
                                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // Clear filters button
                    val hasActiveFilters = state.folderFilterId != null ||
                        state.datePreset != DatePreset.ALL_TIME ||
                        state.useCustomDateRange
                    if (hasActiveFilters) {
                        OutlinedButton(
                            onClick = { vm.clearFilters() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.search_clear_filters))
                        }
                    }
                }
            }

            // ── Error ────────────────────────────────────────────────────
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ── Results ──────────────────────────────────────────────────
            if (state.results.isEmpty() && !state.isSearching && state.query.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_empty_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (state.results.isNotEmpty() && !state.isSearching) {
                    Text(
                        text = if (state.resultCount == 1) {
                            stringResource(R.string.search_results_count_one)
                        } else {
                            stringResource(R.string.search_results_count, state.resultCount)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Date Picker Dialogs ──────────────────────────────────────────
    if (showFromPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.customDateFromMillis
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.setCustomDateFrom(pickerState.selectedDateMillis)
                    showFromPicker = false
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showToPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.customDateToMillis
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.setCustomDateTo(pickerState.selectedDateMillis)
                    showToPicker = false
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
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
