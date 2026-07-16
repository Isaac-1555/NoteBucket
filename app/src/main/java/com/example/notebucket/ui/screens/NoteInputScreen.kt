package com.example.notebucket.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.example.notebucket.R
import com.example.notebucket.data.NoteBucketRepository
import com.example.notebucket.sort.FolderRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteInputState(
    val text: String = "",
    val isCommitting: Boolean = false,
    val snackbar: String? = null,
    val error: String? = null
)

@HiltViewModel
class NoteInputViewModel @Inject constructor(
    private val repo: NoteBucketRepository,
    private val router: FolderRouter
) : ViewModel() {

    private val _state = MutableStateFlow(NoteInputState())
    val state: StateFlow<NoteInputState> = _state.asStateFlow()

    private var debounceJob: Job? = null

    init {
        viewModelScope.launch { maybeRestoreOrCommitDraft() }
    }

    fun onTextChange(text: String) {
        _state.value = _state.value.copy(text = text)
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            if (text.isBlank()) repo.deleteDraft() else repo.saveDraft(text)
        }
    }

    fun onDone() {
        val text = _state.value.text.trim()
        if (text.isBlank() || _state.value.isCommitting) return
        debounceJob?.cancel()
        _state.value = _state.value.copy(isCommitting = true)
        viewModelScope.launch {
            try {
                val result = router.commit(text)
                repo.deleteDraft()
                _state.value = _state.value.copy(
                    text = "",
                    isCommitting = false,
                    snackbar = if (result.isNewFolder)
                        "Created folder \"${result.folder.name}\""
                    else
                        "Filed in \"${result.folder.name}\""
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isCommitting = false,
                    error = t.message ?: "Failed to commit"
                )
            }
        }
    }

    fun consumeSnackbar() {
        _state.value = _state.value.copy(snackbar = null)
    }

    private suspend fun maybeRestoreOrCommitDraft() {
        val draft = repo.getDraft() ?: return
        if (draft.text.isBlank()) {
            repo.deleteDraft()
            return
        }
        val ageMs = System.currentTimeMillis() - draft.updatedAt
        if (ageMs >= STALE_DRAFT_MS) {
            try {
                router.commit(draft.text)
                repo.deleteDraft()
            } catch (_: Throwable) {
                _state.value = _state.value.copy(text = draft.text, snackbar = "Restored your last draft.")
            }
        } else {
            _state.value = _state.value.copy(text = draft.text, snackbar = "Restored your last draft.")
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 500L
        private const val STALE_DRAFT_MS = 60_000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteInputScreen(navController: NavHostController) {
    val vm: NoteInputViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHost.showSnackbar(it)
            vm.consumeSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (state.isCommitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(
                            onClick = { vm.onDone() },
                            enabled = state.text.isNotBlank()
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.noteinput_done),
                                tint = if (state.text.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            BasicTextField(
                value = state.text,
                onValueChange = vm::onTextChange,
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box {
                        if (state.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.noteinput_hint),
                                style = TextStyle(
                                    fontSize = 17.sp,
                                    lineHeight = 26.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(Modifier.height(32.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val wordCount = state.text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                Text(
                    text = if (state.text.isNotBlank()) "$wordCount words" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                state.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
