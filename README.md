# NoteBucket

On-device note organizer: BGE-small embeddings + llama.cpp route notes into
folders by semantic similarity and power semantic search. 100% local — no
cloud, no accounts, no analytics.

## What it is

A standalone Android app. You type or paste a note; an on-device embedding
model (BGE-small-en-v1.5, 33M params) embeds it, cosine-sorts it against
existing folder centroids, and files it (creating a new folder when nothing
clears the threshold). Drafts are crash-safe (persisted to Room on every
keystroke, debounced 500ms) and commit automatically one minute after you
background the app.

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
2. **Home:** folder tiles (count + latest note preview). Search bar entry
   at top. FAB → New note.
3. **NoteInput:** type/paste, press Done. Draft persists to Room on every
   keystroke (debounced 500ms). Background the app with an uncommitted
   draft → WorkManager commits it after 1 minute.
4. **Folder Detail:** reverse-chrono note list, or grid for wallpaper-like
   folders (heuristic: name matches wallpaper/image/photo/pic, or >50% of
   notes are image-URL extensions). Rename folder via the edit icon.
5. **Search:** semantic search with folder filter + date preset filters
   (All / Past week / Past month / Past year). Top-5 results.
6. **Note Detail:** full text, folder link, timestamp, delete + recategorize.
7. **Settings:** threshold slider (default 0.55), storage stats, model
   reload, clear all data.

## Architecture

```
Note text → BGE-small embed (llama.cpp JNI, 384-dim, L2-normalized)
          → cosine vs each folder centroid (Room)
          → max sim ≥ threshold T → assign + incrementally update centroid
          → no match → create new folder (KeyBERT-style crude name)

Search query → BGE embed (retrieval prefix) → cosine vs all note
             embeddings → top-5, pre-filtered by folder/date

Draft lifecycle:
  keystroke → debounced 500ms → Room drafts table
  Done      → embed + commit + delete draft
  background with active draft → ProcessLifecycleObserver schedules
                                 WorkManager one-shot (1 min delay)
  reopen < 1 min               → cancel work, restore draft
  reopen with stale draft      → commit on next open
```

## Tech stack

| Layer | Choice |
|---|---|
| Platform | Native Android (Kotlin) |
| Min SDK | Android 12 (API 31) |
| UI | Jetpack Compose (Bom 2026.02.01), Material 3 |
| DI | Hilt 2.60.1 |
| Navigation | navigation-compose 2.8.5 |
| Persistence | Room 2.7.2 (folders, notes, drafts; embeddings as BLOB) |
| Settings | DataStore Preferences |
| Background work | WorkManager + ProcessLifecycleObserver |
| Embedding model | BAAI/bge-small-en-v1.5 (33M, 384-dim, Apache-2.0) |
| Inference runtime | llama.cpp via NDK + CMake (git submodule), arm64-v8a, JNI |
| Model format | GGUF Q8_0 (~33MB) bundled in APK assets |
| Sensitive permissions | None |

## Key files

| File | Purpose |
|---|---|
| `app/src/main/cpp/bge-jni.cpp` | JNI bridge: loadModel, embed, unload |
| `app/src/main/cpp/CMakeLists.txt` | CMake config, builds llama.cpp + libbge.so |
| `ai/NativeBridge.kt` | Kotlin JNI declarations |
| `ai/BgeEmbedder.kt` | Asset copy + embedNote/embedQuery + load state flow |
| `sort/FolderRouter.kt` | Centroid routing + threshold + KeyBERT naming + search + recategorize |
| `sort/Folder.kt`, `sort/Note.kt` | Domain models |
| `data/NoteBucketDatabase.kt` | Room database |
| `data/dao/*` | FolderDao, NoteDao, DraftDao |
| `data/NoteBucketRepository.kt` | Single data entry point |
| `data/SettingsRepository.kt` | DataStore-backed onboarding flag + threshold |
| `data/mapper/Mappers.kt` | Entity ↔ domain + FloatArray ↔ ByteArray |
| `di/*Module.kt` | Hilt modules (Database, WorkManager, DataStore) |
| `work/DraftCommitWorker.kt` | HiltWorker that commits stale drafts |
| `work/DraftLifecycleObserver.kt` | Schedules/cancels draft commit on app background/foreground |
| `NoteBucketApp.kt` | @HiltAndroidApp, WorkManager config, lifecycle observer registration |
| `MainActivity.kt` | @AndroidEntryPoint, edge-to-edge, theme |
| `ui/nav/Routes.kt` | Route constants |
| `ui/nav/NoteBucketNavGraph.kt` | NavHost + onboarding-aware start destination |
| `ui/screens/*` | Onboarding, NoteInput, Home, FolderDetail, Search, Settings, NoteDetail |
| `ui/theme/*` | Material 3 color scheme + theme |
| `PRD.md` | Product requirements (locked contract) |
| `AGENTS.md` | Agent rules |

## Permissions

None. No INTERNET, no storage, no notifications, no foreground service.
PRD §4: privacy-first, no sensitive permissions.
