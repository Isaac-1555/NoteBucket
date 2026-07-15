# NoteBucket Spike

On-device note sorting + semantic search via BGE-small embeddings + llama.cpp.

## Prerequisites

- Android NDK 28.2.13676358 (install via Android Studio > SDK Tools > NDK)
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

File: ~33 MB. Alternative quant: Q4_K_M (~25 MB) — rename to match the asset name in `SpikeViewModel.kt`.

### 3. Configure local SDK path

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

(Already gitignored — do not commit.)

### 4. Build

```bash
./gradlew :app:assembleDebug
```

This builds llama.cpp + ggml from source via NDK + CMake, then the Kotlin app.
First build takes several minutes (compiling llama.cpp C++ for arm64-v8a).

### 5. Install on device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires a real arm64-v8a Android phone (API 31+). Emulator x86_64 is NOT supported
in this spike — only arm64-v8a ABI is built.

### 6. Test

1. Open "NoteBucket Spike" on the phone.
2. Press "Load Model" — wait for "Model loaded" status.
3. Type a note, press "Done" — observe assigned folder + latency + heap.
4. Adjust the threshold slider to tune folder clustering.
5. Type a search query, press "Search" — observe top-5 results.

## Architecture (spike)

```
Note text → BGE-small embed (llama.cpp JNI, 384-dim, L2-normalized)
         → cosine vs folder centroids
         → max sim ≥ threshold T → assign to existing folder (update centroid)
         → no match → create new folder (KeyBERT crude name, user-renameable)

Search query → BGE embed (with retrieval prefix) → cosine vs all note embeddings → top-5
```

All in-memory for the spike. No DB, no persistence, no WorkManager.

## Key files

| File | Purpose |
|---|---|
| `app/src/main/cpp/bge-jni.cpp` | JNI bridge: loadModel, embed, unload |
| `app/src/main/cpp/CMakeLists.txt` | CMake config, builds llama.cpp + libbge.so |
| `app/src/main/java/.../ai/NativeBridge.kt` | Kotlin JNI declarations |
| `app/src/main/java/.../ai/BgeEmbedder.kt` | Asset copy + embedNote/embedQuery |
| `app/src/main/java/.../sort/FolderSorter.kt` | Centroid routing + threshold + KeyBERT naming |
| `app/src/main/java/.../ui/SpikeScreen.kt` | Compose UI |
| `PRD.md` | Product requirements (locked contract) |
| `AGENTS.md` | Agent rules |
