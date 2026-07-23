# NoteBucket

On-device note organizer: BGE-small embeddings + llama.cpp route notes into
folders by semantic similarity and power semantic search. 100% local — no
cloud, no accounts, no analytics.

**v0.3.0** (versionCode 3)

## What it is

A standalone Android app. You type or paste a note; an on-device embedding
model (BGE-small-en-v1.5, 33M params) embeds it, cosine-scores it against
each folder's name embedding, and files it (ambiguous matches show a
disambiguation dialog; unmatched notes go to "Unsorted"). Attach images or
files, and bulk-move or bulk-delete notes across folders.
Drafts are crash-safe (persisted to Room on every keystroke, debounced 500ms)
and commit automatically one minute after you background the app.

## Prerequisites

- Android NDK 28.2.13676358 (Android Studio > SDK Tools > NDK)
- Android SDK with platform android-36.1, build-tools 36.0.0, cmake 3.22.1
- Java 17+ (Gradle JVM)
- Git (for llama.cpp submodule)

## Setup

### 1. Clone with submodules

```bash
git clone --recurse-submodules <repo-url>
cd NoteBucket
```

If already cloned without submodules:

```bash
git submodule update --init --recursive
```

### 2. Fetch the BGE model (GGUF)

The model weights are gitignored (too large for git). Download manually:

```bash
curl -L -o app/src/main/assets/bge-small-en-v1.5.Q8_0.gguf \
  https://huggingface.co/ggml-org/bge-small-en-v1.5-Q8_0-GGUF/resolve/main/bge-small-en-v1.5-Q8_0-GGUF
```

File: ~33 MB. Alternative quant: Q4_K_M (~25 MB) — rename to match
`BgeEmbedder.DEFAULT_ASSET` in `ai/BgeEmbedder.kt`.

### 3. Configure local SDK path

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

(Already gitignored — do not commit.)

### 4. Build

```bash
./gradlew :app:assembleDebug
```

This builds llama.cpp + ggml from source via NDK + CMake, runs KSP for
Room + Hilt, then compiles the Kotlin app. First build takes several
minutes.

### 5. Install on device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires a real arm64-v8a Android phone (API 31+). Emulator x86_64 is NOT
supported — only arm64-v8a ABI is built.

## App flow

1. **Onboarding (first run only):** privacy explanation + on-device model
   load. Marked complete via DataStore; skipped on subsequent opens.
2. **Home:** color-coded folder tiles (count + latest note preview, 10
   color options). Search bar entry at top. FAB → New note.
3. **NoteInput:** type/paste text, attach images/files via picker.
   Press Done to embed and
   route. Draft persists to Room on every keystroke (debounced 500ms).
   Background the app with an uncommitted draft → WorkManager commits it
   after 1 minute.
4. **Folder Detail:** reverse-chrono note list, or grid for wallpaper-like
   folders (heuristic: name matches wallpaper/image/photo/pic, or >50% of
   notes are image-URL extensions). Rename, recolor, or delete folder.
   Multi-select notes for bulk move or bulk delete.
5. **Search:** semantic search with folder filter + date range filters
   (All time / Past week / Past month / Past year / custom range).
   Top-5 results ranked by cosine similarity with a relative margin floor.
6. **Note Detail:** full text, inline editing, folder link, timestamp,
   attachments (image thumbnails, file cards), delete + recategorize.
7. **Settings:** threshold slider (default 0.55), theme mode (System /
   Light / Dark), hidden folder management, model reload, storage stats
   (note/folder count, DB size), clear all data.

## Architecture

```
Note text → BGE-small embed (llama.cpp JNI, 384-dim, L2-normalized)
          → cosine vs each folder's nameEmbedding (Room)
          → max sim ≥ threshold T → assign to that folder
          → ambiguous (margin ≤ 0.03) or unsorted → disambiguation dialog
          → no match above threshold → file to "Unsorted"

Search query → BGE embed (retrieval prefix) → cosine vs all note
             embeddings → top-5, pre-filtered by folder/date, relative
             margin floor (topSim − 0.25, min 0.35)

Draft lifecycle:
  keystroke → debounced 500ms → Room drafts table
  Done      → embed + commit + delete draft
  background with active draft → ProcessLifecycleObserver schedules
                                 WorkManager one-shot (1 min delay)
  reopen < 1 min               → cancel work, restore draft
  reopen with stale draft      → commit on next open

Attachment lifecycle:
  pick image/file → copy to internal storage (attachments/{noteId}/)
  display inline  → Coil thumbnails for images, file cards for others

Voice lifecycle:
  tap mic → Android SpeechRecognizer → transcribed text appended to note
```

## Tech stack

| Layer | Choice |
|---|---|
| Platform | Native Android (Kotlin) |
| Min SDK | Android 12 (API 31) |
| UI | Jetpack Compose (BOM 2026.02.01), Material 3 |
| DI | Hilt 2.60.1 |
| Navigation | navigation-compose 2.8.5 |
| Persistence | Room 2.7.2 (folders, notes, drafts, attachments; embeddings as BLOB) |
| Settings | DataStore Preferences 1.1.1 |
| Background work | WorkManager 2.10.0 + ProcessLifecycleObserver |
| Image loading | Coil 2.7.0 (Compose) |
| Embedding model | BAAI/bge-small-en-v1.5 (33M, 384-dim, Apache-2.0) |
| Inference runtime | llama.cpp via NDK 28.2 + CMake 3.22.1 (git submodule), arm64-v8a, JNI |
| Model format | GGUF Q8_0 (~33MB) bundled in APK assets |

## Key files

| File | Purpose |
|---|---|
| `app/src/main/cpp/bge-jni.cpp` | JNI bridge: loadModel, embed, unload |
| `app/src/main/cpp/CMakeLists.txt` | CMake config, builds llama.cpp + libbge.so |
| `ai/NativeBridge.kt` | Kotlin JNI declarations |
| `ai/BgeEmbedder.kt` | Asset copy + embedNote/embedQuery + load state flow |
| `sort/FolderRouter.kt` | Name-embedding routing + threshold + search + recategorize + bulk ops |
| `sort/Folder.kt`, `sort/Note.kt` | Domain models |
| `data/NoteBucketDatabase.kt` | Room database (v5, 4 tables) |
| `data/dao/FolderDao.kt` | Folder CRUD + embedding updates |
| `data/dao/NoteDao.kt` | Note CRUD + bulk operations |
| `data/dao/DraftDao.kt` | Singleton draft persistence |
| `data/dao/AttachmentDao.kt` | Attachment CRUD |
| `data/entity/FolderEntity.kt` | Room entity: id, name, nameEmbedding, noteCount, color, isHidden |
| `data/entity/NoteEntity.kt` | Room entity: id, text, embedding, folderId, timestamp |
| `data/entity/DraftEntity.kt` | Room entity: singleton draft with updatedAt |
| `data/entity/AttachmentEntity.kt` | Room entity: id, noteId, fileName, mimeType, filePath |
| `data/NoteBucketRepository.kt` | Single data entry point |
| `data/SettingsRepository.kt` | DataStore-backed onboarding flag + threshold + theme |
| `data/AttachmentStorage.kt` | File-based attachment I/O (internal storage) |
| `data/mapper/Mappers.kt` | Entity ↔ domain + FloatArray ↔ ByteArray |
| `di/*Module.kt` | Hilt modules (Database, WorkManager, DataStore) |
| `work/DraftCommitWorker.kt` | HiltWorker that commits stale drafts |
| `work/DraftLifecycleObserver.kt` | Schedules/cancels draft commit on app background/foreground |
| `NoteBucketApp.kt` | @HiltAndroidApp, WorkManager config, lifecycle observer registration |
| `MainActivity.kt` | @AndroidEntryPoint, edge-to-edge, theme |
| `ui/nav/Routes.kt` | Route constants |
| `ui/nav/NoteBucketNavGraph.kt` | NavHost + onboarding-aware start destination |
| `ui/screens/OnboardingScreen.kt` | Privacy intro + model load progress |
| `ui/screens/NoteInputScreen.kt` | Note creation + attachments |
| `ui/screens/HomeScreen.kt` | Folder grid + search entry + FAB |
| `ui/screens/FolderDetailScreen.kt` | Note list/grid + rename + bulk operations |
| `ui/screens/SearchScreen.kt` | Semantic search + folder/date filters |
| `ui/screens/NoteDetailScreen.kt` | View/edit/delete/recategorize + attachments |
| `ui/screens/SettingsScreen.kt` | Threshold, theme, hidden folders, storage, clear |
| `ui/theme/Theme.kt` | Material 3 dynamic color + light/dark fallback |
| `ui/theme/Color.kt` | Teal-centric color palette |
| `ui/theme/FolderPalette.kt` | 10 folder color options |
| `PRD.md` | Product requirements (locked contract) |
| `AGENTS.md` | Agent rules |

## Permissions

Core features (note taking, embedding, search, attachments) are fully offline.
No storage, notifications, or foreground service permissions.
