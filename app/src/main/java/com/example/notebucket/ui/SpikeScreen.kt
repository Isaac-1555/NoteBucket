package com.example.notebucket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SpikeScreen() {
    val vm: SpikeViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var noteText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "NoteBucket Spike",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (!state.isModelLoaded) {
            Button(
                onClick = { vm.loadModel(context) },
                enabled = !state.isLoadingModel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isLoadingModel) "Loading..." else "Load Model")
            }
            if (state.isLoadingModel) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Text(text = state.status, style = MaterialTheme.typography.bodyMedium)

        if (state.error != null) {
            Text(
                text = state.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (state.isModelLoaded) {
            HorizontalDivider()

            Text("Input", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note text") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5
            )
            Button(
                onClick = {
                    vm.commitNote(noteText)
                    noteText = ""
                },
                enabled = noteText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text("Threshold: ${"%.2f".format(state.threshold)}", fontWeight = FontWeight.SemiBold)
            Slider(
                value = state.threshold,
                onValueChange = { vm.setThreshold(it) },
                valueRange = 0f..1f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Latency: ${state.lastLatencyMs}ms")
                Text("Peak heap: ${state.peakNativeHeapKb}KB")
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()

            Text("Folders (${state.folders.size})", fontWeight = FontWeight.SemiBold)
            state.folders.forEach { folder ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = folder.name +
                                if (folder.isUserRenamed) " (renamed)" else "",
                            modifier = Modifier.weight(1f)
                        )
                        Text("${folder.noteCount}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()

            Text("Search", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Query") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { vm.search(searchQuery) },
                    enabled = searchQuery.isNotBlank() && state.notes.isNotEmpty()
                ) {
                    Text("Search")
                }
            }

            state.searchResults.forEach { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = result.note.text.take(80) +
                                if (result.note.text.length > 80) "..." else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "sim=${"%.3f".format(result.similarity)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()

            Text("Notes (${state.notes.size})", fontWeight = FontWeight.SemiBold)
            state.notes.takeLast(10).forEach { note ->
                val folder = state.folders.find { it.id == note.folderId }
                Text(
                    text = "[${folder?.name ?: "?"}] ${note.text.take(60)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}
