package com.example.notebucket.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.notebucket.R
import com.example.notebucket.data.AttachmentStorage
import com.example.notebucket.data.NoteBucketRepository
import com.example.notebucket.data.entity.AttachmentEntity
import com.example.notebucket.sort.FolderRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val fileName: String,
    val mimeType: String
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

data class NoteInputState(
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val isCommitting: Boolean = false,
    val snackbar: String? = null,
    val error: String? = null,
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val isListening: Boolean = false
) {
    val text: String get() = textFieldValue.text
    val isNotBlank: Boolean get() = text.isNotBlank()
}

@HiltViewModel
class NoteInputViewModel @Inject constructor(
    private val repo: NoteBucketRepository,
    private val router: FolderRouter
) : ViewModel() {

    private val _state = MutableStateFlow(NoteInputState())
    val state: StateFlow<NoteInputState> = _state.asStateFlow()

    private var debounceJob: Job? = null
    private val pendingId = UUID.randomUUID().toString()

    init {
        viewModelScope.launch { maybeRestoreOrCommitDraft() }
    }

    fun onTextChange(newValue: TextFieldValue) {
        val oldText = _state.value.text
        val newText = newValue.text

        val finalValue = if (newText.length > oldText.length && newText.endsWith("\n")) {
            handleNewlineInsertion(oldText, newText, newValue)
        } else {
            newValue
        }

        _state.value = _state.value.copy(textFieldValue = finalValue)
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            if (finalValue.text.isBlank()) repo.deleteDraft() else repo.saveDraft(finalValue.text)
        }
    }

    private fun handleNewlineInsertion(
        oldText: String,
        newText: String,
        newValue: TextFieldValue
    ): TextFieldValue {
        val lines = newText.substringBeforeLast("\n").split("\n")
        val currentLine = lines.lastOrNull() ?: return newValue

        val numberedMatch = NUMBERED_LIST_PATTERN.find(currentLine)
        if (numberedMatch != null) {
            val indent = numberedMatch.groupValues[1]
            val num = numberedMatch.groupValues[2].toIntOrNull() ?: return newValue
            val contentAfter = numberedMatch.groupValues[3]

            if (contentAfter.isBlank()) {
                val updatedText = newText.substringBeforeLast("\n")
                if (updatedText == oldText) return newValue
                return TextFieldValue(
                    text = updatedText,
                    selection = androidx.compose.ui.text.TextRange(updatedText.length)
                )
            }

            val nextLine = "$indent${num + 1}. "
            val insertedText = newText + nextLine
            return TextFieldValue(
                text = insertedText,
                selection = androidx.compose.ui.text.TextRange(insertedText.length)
            )
        }

        val bulletMatch = BULLET_LIST_PATTERN.find(currentLine)
        if (bulletMatch != null) {
            val indent = bulletMatch.groupValues[1]
            val marker = bulletMatch.groupValues[2]
            val contentAfter = bulletMatch.groupValues[3]

            if (contentAfter.isBlank()) {
                val updatedText = newText.substringBeforeLast("\n")
                if (updatedText == oldText) return newValue
                return TextFieldValue(
                    text = updatedText,
                    selection = androidx.compose.ui.text.TextRange(updatedText.length)
                )
            }

            val nextLine = "$indent$marker "
            val insertedText = newText + nextLine
            return TextFieldValue(
                text = insertedText,
                selection = androidx.compose.ui.text.TextRange(insertedText.length)
            )
        }

        return newValue
    }

    fun addAttachment(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val (path, displayName) = AttachmentStorage.copyToStorage(context, uri, pendingId)
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val pending = PendingAttachment(
                    filePath = path,
                    fileName = displayName,
                    mimeType = mimeType
                )
                _state.value = _state.value.copy(
                    pendingAttachments = _state.value.pendingAttachments + pending
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = "Failed to add attachment: ${t.message}")
            }
        }
    }

    fun removeAttachment(id: String, context: android.content.Context) {
        val attachment = _state.value.pendingAttachments.find { it.id == id } ?: return
        AttachmentStorage.deleteFile(attachment.filePath)
        _state.value = _state.value.copy(
            pendingAttachments = _state.value.pendingAttachments.filter { it.id != id }
        )
    }

    fun setListening(listening: Boolean) {
        _state.value = _state.value.copy(isListening = listening)
    }

    fun onVoiceError(message: String) {
        _state.value = _state.value.copy(
            isListening = false,
            snackbar = message
        )
    }

    fun appendText(text: String) {
        val current = _state.value.textFieldValue
        val newText = current.text + text
        _state.value = _state.value.copy(
            textFieldValue = TextFieldValue(
                text = newText,
                selection = androidx.compose.ui.text.TextRange(newText.length)
            )
        )
    }

    fun onDone() {
        val text = _state.value.text.trim()
        if (text.isBlank() || _state.value.isCommitting) return
        debounceJob?.cancel()
        _state.value = _state.value.copy(isCommitting = true)
        viewModelScope.launch {
            try {
                val result = router.commit(text)
                val noteId = result.note.id
                for (pending in _state.value.pendingAttachments) {
                    val entity = AttachmentEntity(
                        id = pending.id,
                        noteId = noteId,
                        fileName = pending.fileName,
                        mimeType = pending.mimeType,
                        filePath = pending.filePath,
                        createdAt = System.currentTimeMillis()
                    )
                    repo.insertAttachment(entity)
                }
                repo.deleteDraft()
                _state.value = _state.value.copy(
                    textFieldValue = TextFieldValue(""),
                    isCommitting = false,
                    pendingAttachments = emptyList(),
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
                _state.value = _state.value.copy(
                    textFieldValue = TextFieldValue(draft.text),
                    snackbar = "Restored your last draft."
                )
            }
        } else {
            _state.value = _state.value.copy(
                textFieldValue = TextFieldValue(draft.text),
                snackbar = "Restored your last draft."
            )
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 500L
        private const val STALE_DRAFT_MS = 60_000L
        private val NUMBERED_LIST_PATTERN = Regex("""^(\s*)(\d+)\.\s(.*)$""")
        private val BULLET_LIST_PATTERN = Regex("""^(\s*)([-*])\s(.*)$""")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteInputScreen(navController: NavHostController) {
    val vm: NoteInputViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var hasAudioPermission by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    val voiceTranscriber = remember { VoiceTranscriber(context) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { vm.addAttachment(context, it) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.addAttachment(context, it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            startVoiceRecognition(voiceTranscriber, vm)
        } else {
            showPermissionRationale = true
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHost.showSnackbar(it)
            vm.consumeSnackbar()
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceTranscriber.destroy() }
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                BasicTextField(
                    value = state.textFieldValue,
                    onValueChange = vm::onTextChange,
                    textStyle = TextStyle(
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                if (state.pendingAttachments.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.pendingAttachments, key = { it.id }) { attachment ->
                            AttachmentThumbnail(
                                attachment = attachment,
                                onRemove = { vm.removeAttachment(attachment.id, context) }
                            )
                        }
                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (state.text.isNotBlank()) {
                val wordCount = state.text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                Text(
                    text = "$wordCount words",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Add image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Add file",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (state.isListening) {
                            voiceTranscriber.stopListening()
                            vm.setListening(false)
                        } else if (!VoiceTranscriber.isAvailable(context)) {
                            vm.onVoiceError("Voice recognition not available on this device.")
                        } else if (hasAudioPermission) {
                            startVoiceRecognition(voiceTranscriber, vm)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (state.isListening)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        if (state.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (state.isListening) "Stop recording" else "Voice input",
                        tint = if (state.isListening)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Button(
                onClick = { vm.onDone() },
                enabled = state.isNotBlank && !state.isCommitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                if (state.isCommitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.noteinput_done),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

private fun startVoiceRecognition(transcriber: VoiceTranscriber, vm: NoteInputViewModel) {
    vm.setListening(true)
    transcriber.startListening(
        onResult = { text ->
            vm.appendText(" $text")
            vm.setListening(false)
        },
        onPartial = { },
        onEndOfSpeech = {
            vm.setListening(false)
        },
        onError = { message ->
            vm.onVoiceError(message)
        }
    )
}

@Composable
private fun AttachmentThumbnail(
    attachment: PendingAttachment,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (attachment.isImage) {
            AsyncImage(
                model = File(attachment.filePath),
                contentDescription = attachment.fileName,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
