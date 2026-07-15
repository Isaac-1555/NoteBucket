# PRD: NoteBucket — Local-First Intelligent Note Organizer

**Working name:** NoteBucket
**Owner:** [You]
**Status:** Draft v2 (pivoted from WhatsApp self-chat observer to standalone note app)
**Last updated:** July 13, 2026

---

## 0. Revision History

| Version | Date | Change |
|---|---|---|
| v1 | 2026-07-13 | Initial draft: WhatsApp self-chat observer via `NotificationListenerService` + Cactus Needle. |
| v2 | 2026-07-13 | **Pivot:** WhatsApp self-chat cannot be captured passively on the sending device (WhatsApp suppresses self-chat notifications). 2nd-number / WhatsApp Business workaround rejected for onboarding friction. Product redefined as a **standalone note-taking app** with on-device classification + semantic search. Drops WhatsApp integration, `NotificationListenerService`, foreground service, battery-optimization exemption, and the Play Store notification-access review burden. Web app scoped as separate v2 project. |

---

## 1. Problem Statement

People capture quick notes throughout the day — links, recipes, wallpaper URLs, tool recommendations, todos, code snippets, articles to read later — but most note apps dump them into a single chronological stream or require manual tagging. Retrieval ("that wallpaper site I saved last week") degrades to scrolling or keyword search, which misses intent and time context. Existing "smart" note apps solve this by sending content to cloud LLMs, which is unacceptable for sensitive personal material.

## 2. Goal

Build a standalone Android app that:
1. Accepts a quick note from the user via a single primary input screen.
2. Uses a local, on-device LLM (Cactus Needle, 26M params) to classify each committed note into a category (link, recipe, wallpaper, tool, article, todo, code, etc.) and extract structured metadata as a function call.
3. Stores each note in a local SQLite database, organized into "buckets" by category, with an embedding vector for semantic search.
4. Provides a dashboard UI to browse buckets.
5. Provides semantic search ("show me wallpapers from last week") that understands intent and time, not just keyword matching.
6. Does all of this **100% on-device** — no cloud LLM calls, no third-party data transmission — because personal note content is sensitive.

## 3. Non-Goals

- Not a WhatsApp client, WhatsApp integration, or chat observer (dropped in v2 — see §0).
- Not a general-purpose chat replacement.
- Not multi-user or collaborative.
- Not a background listener / passive observer of any other app.
- Not a cloud-synced product in v1 (optional encrypted user-controlled backup is v2+).
- Web app is a separate future project, not pre-designed into v1.

## 4. Key Constraints

- **Privacy-first:** No note content, embeddings, or metadata may leave the device. No analytics SDKs that transmit content.
- **On-device inference only:** No OpenAI/Anthropic/Google cloud API calls for classification or search.
- **Performance:** Classification on "Done" press should feel instant — target < 2 seconds end-to-end on a mid-range device. Embedding generation adds to this but should remain < 3 seconds total.
- **Crash-safety:** A note typed but not yet committed must survive app kill, OS reboot, or process death — drafts persist to a Room `drafts` table on every keystroke (debounced).
- **No sensitive Android permissions.** v1 uses no `NotificationListenerService`, no `AccessibilityService`, no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, no foreground service. This dramatically simplifies Play Store review.

## 5. How It Works — High-Level Architecture

```
[User] ─types─► Compose NoteInput screen
                    │
                    ├─ persists to Room `drafts` on every keystroke (debounced)
                    │
                    ├─ presses Done ─► foreground classify (Needle)
                    │                    │
                    │                    ├─ tool-call JSON → category + fields
                    │                    ├─ confidence guard → low cosine → Unsorted
                    │                    ├─ MiniLM embedding at save time
                    │                    ├─ Room: draft → note + bucket assignment
                    │                    └─ UI: show assigned bucket, open fresh input
                    │
                    └─ closes app w/ active draft ─► ProcessLifecycle observer
                                                        │
                                                        └─ schedule WorkManager
                                                            one-shot (1 min delay)
                                                                │
                                                                ├─ if draft still
                                                                │   uncommitted after 1 min
                                                                │   → classify + commit
                                                                │
                                                                └─ if user reopens <1 min
                                                                   → cancel job,
                                                                   restore draft

Dashboard (Home)
    │
    ├─ Bucket tiles (count + latest item)
    ├─ Tap bucket → reverse-chrono list (grid for wallpapers)
    ├─ Search bar → Needle parses → DB filter → MiniLM rank
    ├─ FAB → NoteInput
    └─ Settings (storage, categories, clear data)
```

**Key invariants:**
- The only classification triggers are: (a) user presses Done, or (b) app stays closed ≥1 minute with an uncommitted draft. No passive observation of other apps.
- Drafts are crash-safe via Room; the WorkManager one-shot job is a best-effort commit, not the only path — on next app open, any uncommitted draft older than 1 minute is also classified and committed.
- No foreground service. No persistent listener. Battery impact is bounded by user-initiated actions only.

## 6. Core Components

### 6.1 Note Input Layer
- **UI:** Single primary input screen (text field + Done button). After Done commits and classifies, the input is cleared and a new note can be typed immediately — one note per input, but many inputs per session.
- **Draft persistence:** Every keystroke is debounced (~500ms) and written to a Room `drafts` table. Drafts survive process death and reboot.
- **Commit triggers:**
  1. **Done press** → synchronous (foreground) classify + commit; UI shows assigned bucket before clearing the input.
  2. **App backgrounded with active draft** → ProcessLifecycle observer schedules a WorkManager one-shot with `setInitialDelay(1, MINUTES)`:
     - If the user reopens within 1 minute, the job is cancelled and the draft is restored to the input (undo / "still editing" window).
     - If 1 minute elapses with the app still backgrounded, the job classifies and commits the draft.
  3. **App reopened with stale uncommitted draft** (older than 1 minute, e.g. after a reboot that killed the WorkManager job) → classify + commit on next open.
- **Empty draft handling:** Empty / whitespace-only drafts are not classified; they are deleted on close.

### 6.2 Local Classification — Cactus Needle
- **Model:** [Cactus Needle](https://github.com/cactus-compute/needle) — a 26M-parameter function-calling model distilled from Gemini 3.1 Flash-Lite, MIT-licensed, weights open on Hugging Face.
- **Why it fits:**
  - Purpose-built for single-shot function/tool calling: given a query + a list of "tools," it emits a JSON function call. This maps directly to "categorize this message and extract its fields."
  - Extremely small (26M params, ~14MB) and fast: ~6,000 tok/s prefill, ~1,200 tok/s decode on the Cactus C++ runtime — negligible battery/latency cost per note.
  - Runs fully offline via the **Cactus runtime** (a C++ inference engine built for mobile/wearable/edge deployment) — integrated into the Android app via JNI/NDK.
  - MIT license permits commercial use, modification, and redistribution.
- **Important limitations to design around:**
  - No prebuilt Android AAR exists — Cactus must be built from source via the Android NDK and wrapped with a Kotlin JNI interface. This is the highest-risk phase of the build; a fallback to `llama.cpp` + a small GGUF function-calling model (e.g. Qwen2.5-0.5B quantized) is pre-approved if the Needle NDK build is blocked beyond one week.
  - Needle is **single-shot only** — no multi-turn reasoning, no conversation memory. Fine here since each note is classified independently.
  - Needle **has no built-in "abstain" behavior** — given any input it will always emit some tool call, even if nothing fits well. Mitigation: define a `save_note` fallback/catch-all tool, and/or add a confidence-score guard using the model's contrastive retrieval head (top-1 cosine similarity) to route low-confidence classifications to an "Unsorted" bucket for manual review.
  - Needle is a **router/classifier, not a general chat model** — don't expect it to write summaries or hold conversations. Auto-summarization of long notes (v2+) needs a second, larger on-device model.

- **Tool/function schema (v1 starting set, to be tuned in Phase 6):**
```json
[
  { "name": "save_link", "description": "A generic web link or article URL", "parameters": {"url":"string","title":"string?"} },
  { "name": "save_github_repo", "description": "A GitHub repository link", "parameters": {"url":"string","repo_name":"string?"} },
  { "name": "save_dev_tool", "description": "A link to a free developer tool/product", "parameters": {"url":"string","tool_name":"string?"} },
  { "name": "save_recipe", "description": "A recipe, ingredient list, or cooking link/text", "parameters": {"text_or_url":"string","dish_name":"string?"} },
  { "name": "save_wallpaper", "description": "A wallpaper image link or reference", "parameters": {"url":"string"} },
  { "name": "save_note", "description": "Fallback: anything that doesn't clearly fit another category", "parameters": {"text":"string"} }
]
```
Additional buckets (Article, Todo, Code/Snippet, Media/Image) will be considered during Phase 6 hand-labeling based on actual user notes.

### 6.3 Semantic Search — Embeddings
Needle does not produce general-purpose semantic embeddings for free-text search — it's specialized for tool-call routing. For "find me wallpapers from last week" style search, a small **on-device embedding model** runs in addition to Needle:
- **Model:** `all-MiniLM-L6-v2` (INT8/GGUF, ~25–90MB) run via `llama.cpp`'s embedding mode.
- Each saved note gets an embedding vector at save time.
- Vectors are stored in a lightweight on-device vector index via the [sqlite-vec](https://github.com/asg017/sqlite-vec) SQLite extension (loaded via a Room `SupportSQLiteOpenHelper` callback), co-located with the structured metadata in the same DB file.
- **Query parsing:** A search query like *"wallpapers from last week"* needs (a) category filtering ("wallpaper") and (b) a time filter ("last week"). This is itself a small function-calling task — reuse **Needle** with a `search_query` tool schema:
```json
{ "name": "search_query", "parameters": { "category": "string?", "date_from": "date?", "date_to": "date?", "free_text": "string?" } }
```
Needle extracts structured filters; the free-text remainder (if any) goes to the embedding model for semantic ranking within the filtered set.

### 6.4 Storage
- **Database:** SQLite (via Room ORM) for structured note metadata (category, extracted fields, timestamps, source draft ID, raw text).
- **Vector index:** sqlite-vec extension loaded into the same DB file via `RoomDatabase.Callback.onOpen`.
- **Media:** v1 stores URL/text references only — no image fetching. Wallpaper image caching is a Phase 6 decision.
- **No cloud sync in v1.** (Optional encrypted user-controlled backup is v2+.)

### 6.5 Dashboard UI
- Home screen: bucket tiles (Links, GitHub Repos, Dev Tools, Recipes, Wallpapers, Unsorted, ...), each showing count and most recent item.
- Bucket detail view: reverse-chronological list/grid (grid for wallpapers), tap to open original link or view saved text.
- Search bar at top: natural-language semantic search box.
- Manual override: user can re-categorize or delete any note (also used as implicit feedback for v2 fine-tuning).
- Settings: storage usage, category management (add/rename/merge buckets), clear data.

## 7. User Flow

1. **Onboarding:** User installs app → sees a brief privacy explanation ("all processing on-device, nothing leaves your phone") → lands on NoteInput. No permissions prompts. No account. No second phone number.
2. **Everyday use:** User opens NoteBucket, types or pastes a note, presses Done. Within ~2 seconds the note appears in the relevant bucket. The input clears and the next note can be typed immediately. Alternatively, the user can type and close the app — if they don't come back within a minute, the note is committed and classified in the background; if they do come back, the draft is restored untouched.
3. **Retrieval:** User opens NoteBucket, taps a bucket or types a search query ("recipes with chicken I saved this month"), gets ranked, relevant results.

## 8. Success Metrics

| Metric | Target |
|---|---|
| Classification accuracy (category correctness) on a hand-labeled sample of the user's own notes | ≥ 90% |
| End-to-end latency from Done press → note in DB with bucket assigned | < 2 seconds on a mid-range device |
| Battery impact under normal use (~20–50 notes/day) | < 0.5% additional daily drain (no persistent service) |
| Draft crash-safety | A typed note survives app kill, OS reboot, and process death — verified in Phase 6 |
| Search satisfaction | User finds the intended note within the top 5 results for time/category-qualified queries |

(Replaces v1 metrics about background-listener uptime and OEM-skin survival — those are no longer relevant since v2 has no background listener.)

## 9. Risks & Open Questions

| Risk | Notes |
|---|---|
| **Needle NDK build unproven on Android** | No prebuilt Android AAR exists for the Cactus runtime. Must build C++ from source via NDK + write a Kotlin JNI wrapper. **Mitigation:** de-risk gate at end of week 4 — both native libs must run inference on emulator + a real mid-range device before proceeding. If blocked >1 week, fall back to `llama.cpp` + Qwen2.5-0.5B quantized for both classification and embeddings (single runtime, larger model). Pre-approved. |
| **WorkManager one-shot delay unreliable across OEMs** | Some OEM skins aggressively defer background jobs. **Mitigation:** use `setInitialDelay(1, MINUTES)` on a regular (non-expedited) WorkManager job; verify on Samsung One UI, Xiaomi HyperOS, OnePlus OxygenOS in Phase 6. Worst case is benign — draft commits on next app open instead. |
| **Draft loss on phone reboot before 1-minute WorkManager job fires** | **Mitigation:** drafts are persisted to Room, so they survive reboot. On app next open, any uncommitted draft older than 1 minute is classified and committed; younger drafts are restored to the input. |
| **sqlite-vec ABI / version mismatch with Android's bundled SQLite** | **Mitigation:** pin sqlite-vec version; test on minSdk 31 + targetSdk 35 emulators in Phase 1. |
| **Native library size bloats APK** | Needle weights ~14MB + MiniLM GGUF ~25–90MB. **Mitigation:** use AAB for Play Store (per-ABI split); sideload can ship APK with arm64-v8a only. |
| **Classification latency > 2s on slow devices** | **Mitigation:** Phase 3 measure on a mid-range device. Options: smaller MiniLM quantization, background-thread classify with UI spinner, cache the compiled tool-schema prompt. |
| **Needle's no-abstain behavior** | **Mitigation:** define `save_note` fallback tool + confidence-score guard using Needle's contrastive retrieval head; low top-1 cosine routes to "Unsorted" for manual review. |
| **Final bucket list** | Default to the PRD example set (link, github_repo, dev_tool, recipe, wallpaper, note). Add Article/Todo/Code/Media during Phase 6 hand-labeling based on actual user notes. |

## 10. Tech Stack Summary

| Layer | Choice |
|---|---|
| Platform | Native Android (Kotlin) |
| Min SDK | Android 12 (API 31) |
| Target SDK | 36 |
| Build | Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10 |
| UI | Jetpack Compose (Bom 2026.02.01), Material 3 |
| DI | Hilt |
| Background work | WorkManager (one-shot, 1-minute delayed draft-commit job) + ProcessLifecycleObserver |
| Classification | Cactus Needle (26M, MIT) via Cactus C++ runtime built with NDK + JNI (fallback: llama.cpp + Qwen2.5-0.5B) |
| Embeddings | Quantized all-MiniLM-L6-v2 via llama.cpp embedding mode |
| DB | SQLite + Room + sqlite-vec (vector search, loaded via Room OpenHelper callback) |
| Sensitive permissions | **None** (no NotificationListenerService, no AccessibilityService, no foreground service, no battery-optimization exemption) |
| Distribution | Sideload for friend testing → Play Store after |

## 11. Future Work (v2+)

- Fine-tune Needle on the user's own corrected categorizations (Needle supports local fine-tuning via its playground/CLI).
- Optional encrypted, user-controlled cloud backup.
- Auto-summarization of long saved text notes (requires a small general-purpose on-device LLM, not Needle).
- Widget / quick-glance view of latest saved items.
- Export bucket contents (e.g., wallpapers to a folder, recipes to a text file).
- Wallpaper / media image fetching and local caching (decide during Phase 6 whether v1 should include this).
- Multi-language support (MiniLM is multilingual; Needle coverage needs validation).
- **Web app** as a separate project (potentially Next.js + same models via WASM/server); v1 Android architecture deliberately does not pre-design for it.
