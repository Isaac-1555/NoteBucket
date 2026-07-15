# PRD: NoteBucket — Local-First Note Organizer (Embedding-Routed)

**Working name:** NoteBucket
**Status:** Draft v3 (pivot from LLM classifier to embedding-routed sorting)
**Last updated:** July 15, 2026

---

## 0. Revision History

| Version | Date | Change |
|---|---|---|
| v1 | 2026-07-13 | WhatsApp self-chat observer via NotificationListenerService + Cactus Needle. |
| v2 | 2026-07-13 | Pivot to standalone note app. Cactus Needle classifier + MiniLM embeddings + sqlite-vec. |
| v3 | 2026-07-15 | **Pivot:** Drop Cactus Needle (no GGUF, custom arch not in llama.cpp) and MiniLM. Single embedding model — BGE-small-en-v1.5 via llama.cpp — drives both folder assignment (centroid cosine + threshold) and semantic search. No LLM in v1. New folders auto-named via KeyBERT-style extraction, user-renameable. Storage spike = in-memory; v1 production = Room. |

---

## 1. Problem Statement

People capture quick notes — links, recipes, wallpaper URLs, tool recommendations, todos, code snippets — but most note apps dump them into one chronological stream or require manual tagging. Retrieval ("that wallpaper site I saved last week") degrades to scrolling or keyword search, which misses intent and time context. Existing "smart" note apps solve this by sending content to cloud LLMs, unacceptable for sensitive personal material.

## 2. Goal

Build a standalone Android app that:
1. Accepts a quick note via a single primary input screen.
2. Embeds the note with an on-device embedding model (BGE-small-en-v1.5) and routes it to an existing folder by centroid cosine similarity, or creates a new folder when no match clears a threshold.
3. Stores each note with its embedding vector for semantic search.
4. Provides a dashboard UI to browse folders.
5. Provides semantic search ("wallpapers from last week") that understands intent, not just keyword matching.
6. Does all of this 100% on-device — no cloud calls, no third-party data transmission.

## 3. Non-Goals

- Not a WhatsApp client/integration/observer.
- Not multi-user or collaborative.
- Not a background listener / passive observer of any other app.
- Not a cloud-synced product in v1.
- No on-device LLM in v1 (classification is embedding-routed, not generative). LLM-assisted naming/summarization is v2+.
- Web app is a separate future project.

## 4. Key Constraints

- **Privacy-first:** No note content, embeddings, or metadata leave the device. No analytics SDKs that transmit content.
- **On-device inference only:** No OpenAI/Anthropic/Google cloud API calls.
- **Performance:** Embedding + folder assignment on "Done" press should feel instant — target < 1 second end-to-end on a mid-range arm64 device. BGE-small is 33M params; llama.cpp CPU inference on arm64 is well-characterized for this size.
- **Crash-safety:** A typed-but-uncommitted note must survive app kill, OS reboot, or process death — drafts persist to a Room `drafts` table on every keystroke (debounced).
- **No sensitive Android permissions.** No NotificationListenerService, no AccessibilityService, no REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, no foreground service. Simplifies Play Store review.

## 5. How It Works — High-Level Architecture

```
[User] ─types─► Compose NoteInput screen
                    │
                    ├─ persists to Room `drafts` on every keystroke (debounced ~500ms)
                    │
                    ├─ presses Done ─► embed note (BGE via llama.cpp JNI)
                    │                    │
                    │                    ├─ cosine vs each existing folder centroid
                    │                    ├─ max sim ≥ threshold T → assign to that folder
                    │                    │   (centroid updated incrementally)
                    │                    ├─ no folder clears T → create new folder
                    │                    │   (KeyBERT crude name, user-renameable later)
                    │                    ├─ Room: draft → note + folder assignment + embedding BLOB
                    │                    └─ UI: show assigned folder, open fresh input
                    │
                    └─ closes app w/ active draft ─► ProcessLifecycle observer
                                                        │
                                                        └─ schedule WorkManager
                                                            one-shot (1 min delay)
                                                                ├─ if draft still uncommitted
                                                                │   after 1 min → embed + commit
                                                                └─ if user reopens <1 min
                                                                   → cancel job, restore draft

Dashboard (Home)
    ├─ Folder tiles (count + latest item)
    ├─ Tap folder → reverse-chrono list (grid for wallpapers)
    ├─ Search bar → embed query (with BGE retrieval prefix) → cosine vs all note embeddings → top-K
    ├─ FAB → NoteInput
    └─ Settings (storage, threshold T, clear data)
```

**Key invariants:**
- The only commit triggers are: (a) user presses Done, or (b) app stays closed ≥1 minute with an uncommitted draft. No passive observation.
- Drafts are crash-safe via Room; WorkManager is best-effort, not the only path — on next app open, any uncommitted draft older than 1 minute is also embedded and committed.
- No foreground service. No persistent listener. Battery impact bounded by user-initiated actions.
- Folder assignment is purely embedding-vs-centroid. Folder names (crude or user-renamed) never enter matching. Renames are always safe.

## 6. Core Components

### 6.1 Note Input Layer
- Single primary input screen (text field + Done). After Done commits and assigns, input clears for the next note.
- Draft persistence: every keystroke debounced ~500ms, written to Room `drafts`. Survives process death and reboot.
- Commit triggers:
  1. Done press → synchronous (foreground) embed + assign + commit; UI shows assigned folder before clearing input.
  2. App backgrounded with active draft → ProcessLifecycle observer schedules WorkManager one-shot with `setInitialDelay(1, MINUTES)`.
  3. App reopened with stale uncommitted draft (>1 min, e.g. after reboot killed the WorkManager job) → embed + commit on next open.
- Empty/whitespace-only drafts are not embedded; deleted on close.

### 6.2 Embedding Model + Runtime — BGE-small + llama.cpp
- **Model:** `BAAI/bge-small-en-v1.5` — 33M params, 384-dim embeddings, BERT-arch, CLS pooling, Apache-2.0. GGUF from `ggml-org/bge-small-en-v1.5-Q8_0-GGUF` (~33MB Q8_0) or Q4_K_M (~25MB).
- **Runtime:** `llama.cpp` built from source via Android NDK + CMake as a git submodule. arm64-v8a only for v1. Loaded via thin JNI wrapper.
- **Why it fits:**
  - BGE-small is purpose-built for semantic embeddings; 384-dim is cheap to store and compute cosine over.
  - llama.cpp has native BERT/BGE arch support (`--embedding --pooling cls`); no custom ggml work needed.
  - ~33MB Q8_0 quantization fits trivially in APK assets.
  - CPU-only inference on arm64 with NEON is well-characterized; no NPU/GPU dependency.
- **Two embed modes:**
  - `embedNote(text)` — raw text. Used for note ingestion + folder centroids.
  - `embedQuery(text)` — text prefixed with `"Represent this sentence for searching relevant passages: "` per BGE retrieval convention. Used for search queries only.
- **Limitations:**
  - BGE is an embedder, not a generator. It cannot produce folder names, summaries, or chat responses. Folder naming is handled by KeyBERT-style extraction (§6.3), not the model.
  - BGE-small-en is English-only. Multilingual is v2+ (BGE-small-zh or multilingual variant).

### 6.3 Folder Routing + New-Folder Naming
- **Routing:** cosine similarity between note embedding and each folder's centroid. Centroid = mean of member note embeddings, updated incrementally: `centroid = (centroid * n + newEmb) / (n+1)`. Assign to argmax folder if `sim ≥ threshold T`; else create new folder.
- **Threshold T:** default 0.55, tunable in Settings. BGE-small-en-v1.5 related-content cosines typically land 0.5–0.8; unrelated 0.1–0.4. Exposed as a live slider in the spike UI for tuning against real notes.
- **New-folder naming (crude, auto):** KeyBERT-style — split note into candidate 1–2 gram phrases, embed each via the same BGE model, cosine vs the note's own embedding, pick the top phrase as the folder name. No LLM. Crude but automatic. User can rename any folder later; renames never affect routing.
- **No-abstain handling:** BGE always produces an embedding; the threshold is the only "no good match" guard. A note that matches nothing clears T=0.55 by having max-sim < 0.55, which creates a new folder. This is the intended behavior, not a failure.

### 6.4 Semantic Search
- Query embedded via `embedQuery` (with retrieval prefix).
- Cosine similarity vs all stored note embeddings; return top-K (default 5).
- Optional filters (folder, date range) applied before cosine ranking — pure Kotlin filtering over note metadata, no model involvement.
- Storage and retrieval scale: spike = in-memory brute-force. v1 production = Room + embeddings as BLOB + brute-force over loaded rows (sufficient for thousands of notes); sqlite-vec extension is a v1.5+ optimization if brute-force latency becomes a problem.

### 6.5 Storage
- **Database:** SQLite via Room for note metadata (folder_id, text, timestamps, source draft ID) + embeddings as BLOB (384 floats × 4 bytes = 1.5KB/note).
- **Spike:** in-memory `List<Note>` + `List<Folder>` — no Room, no DB. Verifies runtime + routing + search before persistence layer is built.
- **No cloud sync in v1.**

### 6.6 Dashboard UI
- Home: folder tiles (count + most recent item).
- Folder detail: reverse-chronological list (grid for wallpaper-like folders).
- Search bar: natural-language semantic search.
- Manual override: re-categorize or delete any note; rename any folder (renames never affect routing).
- Settings: storage usage, threshold T slider, clear data.

## 7. User Flow

1. **Onboarding:** install → brief privacy explanation → land on NoteInput. No permissions, no account.
2. **Everyday use:** open NoteBucket, type/paste a note, press Done. Within ~1s the note appears in a folder (existing or newly created). Input clears for the next note. Or: type and close — if not back within 1 min, note commits in background; if back within 1 min, draft restored.
3. **Retrieval:** open NoteBucket, tap a folder or type a search query, get ranked relevant results.
4. **Folder management:** rename any folder at any time. Renames are cosmetic — routing is embedding-based and unaffected.

## 8. Success Metrics

| Metric | Target |
|---|---|
| Embedding latency (Done press → embedding ready) | < 500ms on a mid-range arm64 device |
| End-to-end assign latency (Done → folder assigned in DB) | < 1 second |
| Peak native heap under inference | < 150MB |
| Folder clustering quality (hand-labeled sample of user's own notes) | ≥ 80% notes land in the folder the user would have chosen |
| Search satisfaction | User finds intended note within top 5 for time/category-qualified queries |
| Draft crash-safety | Typed note survives app kill, OS reboot, process death — verified in spike + v1 |

## 9. Risks & Open Questions

| Risk | Notes |
|---|---|
| **Threshold T tuning is load-bearing** | Too high → every note spawns a new folder. Too low → everything dumps into one folder. Mitigation: live slider in spike UI, tune against real notes before v1 lock. |
| **KeyBERT crude names may be nonsensical** | Mitigation: user-renameable; names are cosmetic and never affect routing. v2+ could use a small on-device LLM for naming. |
| **BGE-small English-only** | Multilingual users get poor embeddings. Mitigation: v2+ swap to BGE-small-zh or a multilingual embedder. |
| **llama.cpp NDK build fragility** | Mitigation: pin llama.cpp submodule to a release tag; spike phase verifies arm64 build before any product code is layered on. |
| **Folder centroid drift** | A folder that absorbs diverse notes can drift and attract more diverse notes. Mitigation: v1.5+ could add per-folder embedding variance tracking + split prompts; v1 accepts the drift. |
| **APK size** | BGE-small Q8_0 ~33MB + llama.cpp .so ~5-10MB. Mitigation: AAB per-ABI split for Play Store; sideload ships arm64-only. |
| **WorkManager 1-min delay unreliable across OEMs** | Mitigation: regular (non-expedited) WorkManager job + verify on Samsung/Xiaomi/OnePlus in v1. Worst case benign — draft commits on next app open. |
| **Brute-force cosine latency at scale** | Thousands of notes × 384-dim cosine in Kotlin is fine. Tens of thousands needs sqlite-vec or HNSW. v1 targets <5000 notes/user; v1.5+ adds sqlite-vec if needed. |

## 10. Tech Stack Summary

| Layer | Choice |
|---|---|
| Platform | Native Android (Kotlin) |
| Min SDK | Android 12 (API 31) |
| Target SDK | 36 |
| Build | Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10 |
| UI | Jetpack Compose (Bom 2026.02.01), Material 3 |
| DI | Hilt |
| Background work | WorkManager one-shot + ProcessLifecycleObserver |
| Embedding model | BAAI/bge-small-en-v1.5 (33M, 384-dim, Apache-2.0) |
| Inference runtime | llama.cpp built from source via NDK + CMake (git submodule), arm64-v8a, JNI wrapper |
| Model format | GGUF (Q8_0 default, Q4_K_M fallback), bundled in APK assets |
| Storage (spike) | In-memory `List<Note>` / `List<Folder>` |
| Storage (v1) | SQLite + Room, embeddings as BLOB, brute-force cosine |
| Sensitive permissions | None |
| Distribution | Sideload for friend testing → Play Store after |

## 11. Build Phases

- **Phase S (spike, this plan):** Verify BGE-small runs on a real arm64 phone via llama.cpp with acceptable latency/memory. Minimal UI: input + Done + threshold slider + folder list + search + perf readout. In-memory storage. No Room, no WorkManager, no onboarding. Gate: < 500ms embed latency, < 150MB heap, folder clustering works on 5–10 hand-tested notes.
- **Phase 1:** On top of green spike — Room DB + draft persistence + WorkManager commit job + ProcessLifecycle observer. Replaces in-memory store.
- **Phase 2:** Full dashboard UI (folder tiles, folder detail, search bar, settings, manual override/rename).
- **Phase 3:** Hand-label a real note sample, tune T, validate success metrics.
- **Phase 4:** Polish, sideload distribution, friend testing.
- **Phase 5+:** sqlite-vec if scale demands; multilingual embedder; LLM-assisted naming/summarization (v2).

## 12. Future Work (v2+)

- LLM-assisted folder naming + note summarization (would require a small on-device generative model — separate from BGE).
- sqlite-vec or HNSW for scale > 5000 notes.
- Multilingual embeddings (BGE-small-zh or multilingual variant).
- Optional encrypted user-controlled cloud backup.
- Auto-summarization of long notes.
- Widget / quick-glance view.
- Export bucket contents.
- Web app as a separate project.
