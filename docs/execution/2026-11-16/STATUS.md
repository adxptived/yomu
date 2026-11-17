# Yomu plan execution status — 2026-11-16

Plan: [AGENT_EXECUTION_PLAN_2026-11-16.md](../../AGENT_EXECUTION_PLAN_2026-11-16.md)
Product spec: [DEVELOPMENT_PLAN_2026-11-16.md](../../DEVELOPMENT_PLAN_2026-11-16.md)
Matrix: [MIHON_PARITY_2026-11-16.md](../../MIHON_PARITY_2026-11-16.md)

Statuses: `TODO`, `IN_PROGRESS`, `CODE_VERIFIED`, `ACCEPTED`, `BLOCKED`.

## B00 — execution preparation

| Field | Value |
|---|---|
| HEAD at start | `0120bbc4a67baa494953d2ba33f579b5e8fac9b4` (matches the plan base) |
| Pre-existing user changes | 3 untracked plan documents in `docs/` — not touched |
| Committed work at start | none |
| JDK | Temurin 21.0.12 (Gradle daemon), launcher JVM 25 |
| Gradle | 8.14.3, JDK toolchain from `gradle.properties` |
| Android SDK | `ANDROID_SDK_ROOT=C:\Users\ilyam\AppData\Local\Android\Sdk`, `local.properties` present |
| `rtk` | present at `C:\Users\ilyam\.cargo\bin\rtk.exe`, but `rtk proxy` buffers all child output with no progress visibility. All commands below run `gradlew.bat` directly with `--console=plain`. |
| Baseline | `.\gradlew.bat :app:assembleDebug --console=plain` → exit 0, `BUILD SUCCESSFUL`, 493 tasks up-to-date |
| Device | `DEVICE_PENDING` — no Android device/emulator attached in this environment |

### Baseline failures

None observed for `:app:assembleDebug`. Profile-specific baselines are recorded per package as they run.

## Package status

| ID | Status | Notes |
|---|---|---|
| B00 | ACCEPTED | Preparation complete; device-dependent acceptance recorded as `DEVICE_PENDING`. |
| R01 | CODE_VERIFIED | See report below. V01 native checks are `DEVICE_PENDING`. |
| R02 | CODE_VERIFIED | See report below. V02 native checks are `DEVICE_PENDING`. |
| R03 | CODE_VERIFIED | See report below. V03 visual/TalkBack checks are `DEVICE_PENDING`. |
| D01 | CODE_VERIFIED | See report below. V08 audio-tag evidence needs a real file. |
| L02 | CODE_VERIFIED | See report below. V04 device pass pending. |
| L03 | CODE_VERIFIED | See report below. V05/V06 device passes pending. |
| L04 | CODE_VERIFIED | See report below. |
| L01 | CODE_VERIFIED | See report below. V18 device pass pending. |
| L05 | CODE_VERIFIED | Query model and versioned shelves; see the scope note in the report. |
| D02 | CODE_VERIFIED | See report below. |
| S02 | CODE_VERIFIED | Capabilities and probe contract; see the scope note. |
| L06 | CODE_VERIFIED | Paged query indices, one ordering for both kinds; see the scope note on the merged stream. |
| S01 | CODE_VERIFIED | Connection wizard; V09/V10 device pass pending. |
| S03 | CODE_VERIFIED | Feed subscriptions: own table, conditional refresh, identity by GUID; see the scope note. |
| N01 | CODE_VERIFIED | Saved import report with per-file causes and a retry of the fixable ones. |
| S05 | CODE_VERIFIED | Batch update report, revocable trust; lifecycle checks read and covered where possible. |
| S06 | IN_PROGRESS | Content preference Safe/Mixed/18+ with migration and one shared User-Agent; DoH, cookie scope UI and the Shizuku backend are not done. |
| M01 | CODE_VERIFIED | One chapter-navigation resolver; alternatives and gaps only with reliable numbering. |
| M02 | IN_PROGRESS | Spread/virtual-page model, split render and a separate "cover alone" setting; rotate-to-fit, start zoom and tiled decode are not done. |
| M04 | IN_PROGRESS | Per-mode tap schemes, per-axis inversion and one hardware-key resolver; the optional page navigator and animation-speed setting are not done. |
| U01 | IN_PROGRESS | One update policy with per-title check times, restrictions and upload-rhythm prediction; per-title overrides and the excluded-shelf picker are not done. |
| DL02 | IN_PROGRESS | Retention policy with bookmark/shelf/ownership protection and a storage cap; the preview UI and per-shelf pickers are not done. |
| Q04 | IN_PROGRESS | Secrets are masked before the error journal is written; incognito no longer persists automatic progress/read; the remaining entry-point audit and typed diagnostics are open. |
| U02 | IN_PROGRESS | Check-plan screen with week grouping, reasons and a manual check; shelf filters and awaiting the manual result are not done. |
| U03 | IN_PROGRESS | Read/download/bookmark filters on the existing Updates/History feeds, with a ready grouping model; grouped headers, multi-selection and the new-chapters widget are not done. |
| DL01 | IN_PROGRESS | The queue already had pause/resume/cancel/priority/guarded completion; reauth is now its own failure kind with its own message. Retry-After on queue retries and the remaining fairness tests are open. |
| DL03 | IN_PROGRESS | Owned download directories are marked `.nomedia`; existing stable names/index were verified, rescan and ComicInfo round-trip tests remain open. |

---

## R01 — PDF input and tap-zone normalisation

**Base:** `0120bbc4a67baa494953d2ba33f579b5e8fac9b4`, plus the three untracked plan documents.

**Status:** `CODE_VERIFIED` (V01 device checks `DEVICE_PENDING`).

**Confirmed chain (was broken):** `PdfPage.detectTapGestures` → `listener.onTap(rootPx, rootPy)` →
`ReaderViewModel.onTap` → `TapZones.action(x, y)` which compares against fractions 0..1. A centre tap of a
1080×1920 viewport sent `x = 540`, so `540 > 0.75` selected `NEXT` and the centre always turned the page.

**Fix:** the zone contract moved into `domain` and every engine now hands it viewport fractions.

| File | Change |
|---|---|
| `domain/src/main/kotlin/app/yomu/domain/reader/ReaderInput.kt` | NEW. `ReaderPoint`, `ReaderTapAction`, `ReaderInput.normalize`/`normalizeLocal`/`action`. Rejects non-finite and out-of-viewport input instead of clamping. |
| `domain/src/test/kotlin/app/yomu/domain/reader/ReaderInputTest.kt` | NEW. The plan's regression table (1080×1920, 0.25 side fraction), viewport origin offset, zero/NaN viewport, outside touches, all five layouts, inversion, RTL and the EDGES exception. |
| `feature/reader-manga/.../MangaController.kt` | `TapZones.action` is now a delegating adapter over `ReaderInput`; semantics unchanged. |
| `feature/reader-manga/.../MangaReaderView.kt` | Both tap paths call `ReaderInput.action` with a `ReaderPoint`. |
| `feature/reader-manga/.../TapZonesOverlay.kt` | `ZoneGrid` samples `ReaderInput.action`, so the diagram cannot drift from the real behaviour. |
| `feature/reader-pdf/.../PdfReaderView.kt` | `PdfReaderView` measures its own viewport once (`boundsInRoot`). `PdfPage` and `ReflowView` convert their gesture position to root and pass it through `ReaderInput.normalize`; `listener.onTap` now receives fractions only. |
| `feature/reader/.../ReaderViewModel.kt` | `onTap` uses `ReaderInput.action` for the text and PDF branches. |
| `feature/reader/.../ReaderTapZonesHint.kt` | Hint labels use `ReaderTapAction`. |

**Coordinate contract (now documented in code):**

| Data | Units | Used for |
|---|---|---|
| Input position | viewport fractions 0..1 | tap-zone resolution |
| Menu anchor | window px | popup placement / hit test |
| PDF hit point | PDF page points | glyph, link and annotation hit tests |
| Scroll anchor | chapter/page identity + inner offset | save and restore |

A root point becomes a fraction by `(root - viewportOrigin) / viewportSize`. The viewport is the reader area
measured over all of `PAGED`/`SCROLL`/`DUAL`/reflow — never the activity, a page bitmap or a scroll extent.
`width`/`height` ≤ 0, NaN, infinity and touches outside the viewport resolve to no tap.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :app:assembleDebug --console=plain` | baseline, `BUILD SUCCESSFUL` |
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.reader.ReaderInputTest"` | `BUILD SUCCESSFUL`, 12 tests |
| `.\gradlew.bat :feature:reader-manga:compileDebugKotlin :feature:reader-pdf:compileDebugKotlin :feature:reader:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :core:ui:testDebugUnitTest :feature:reader:testDebugUnitTest :feature:reader-pdf:testDebugUnitTest :feature:reader-manga:testDebugUnitTest :app:compileDebugKotlin` | C-READER, `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING`. V01 still needs a real PDF at centre/edges through `PAGED`/`SCROLL`/`DUAL`/reflow,
portrait/landscape, zoom, crop, split-screen, system insets and `tapToTurn` off.

**Matrix:** MP036 (tap zones) — units fixed and covered by a unit test; device acceptance open.

**Next:** R02.

---

## R02 — PDF page menu and page actions

**Status:** `CODE_VERIFIED` (V02 native checks `DEVICE_PENDING`).

**Confirmed chain (was broken):** `PdfPage` used `detectDragGesturesAfterLongPress` only for text selection. When
`charIndexAt` returned `-1` (a scan, a margin, an empty area) nothing happened at all — `PdfListener` had no page-menu
event. Every page action went through `pageFile()`, which returns a file for `ReaderContent.Manga` only, so a PDF
could never save, share or set a cover; the ViewModel then returned silently.

**Fix:** an explicit long-press event with a page identity, and actions dispatched on real capabilities.

| File | Change |
|---|---|
| `domain/.../reader/PageActionContext.kt` | NEW. `PageEngine`, `PageCapabilities`, `PageContext` (physical page + book/entry/chapter identity + capabilities), `PageCapabilitiesOf` per engine. |
| `domain/src/test/.../PageActionContextTest.kt` | NEW. Capability split for manga/PDF text/PDF scan/text, page bounds, notes for library books only. |
| `feature/reader-pdf/.../PdfReaderView.kt` | `PdfListener.onPageLongPress(page, windowPosition)`. The long press now decides: a text hit selects a word, a miss (scan or margin) opens the page menu. A drag that ends on blank space does not open the menu; `pressPage` guards against an async hit test opening the menu of a page that already changed. |
| `feature/reader/.../ReaderViewModel.kt` | `PageMenuRequest` carries the `PageContext`. `pageContext()` builds identity per engine; `pdfHasText()` reads the text layer. `runPageAction(context, action)` refuses unsupported commands with a message. `pageBytes()` renders a PDF page to PNG in the share cache instead of calling the manga-only `pageFile()`. `pageImage()` and `setPageAsCover()` handle both engines. New `translatePdfPage()`. |
| `feature/reader/.../ReaderScreen.kt` | The page menu lists only actions `isPageActionSupported` allows, so no visible command is a no-op. |
| `feature/reader/res/values{,-ru}/strings.xml` | `reader_action_unavailable`. |

**Behaviour:**

- Text PDF: long press on a word selects it as before; long press on a margin opens the page menu.
- Scan or page without a text layer: long press opens the page menu; `COPY_TEXT` and `TRANSLATE` fall back to OCR of a
  1600 px render, which is recycled in `finally`.
- Save/share of a PDF page writes a PNG under the existing `Share.shareDir` cache (already pruned hourly); the original
  document is opened read-only and never rewritten.
- `SET_COVER` and `NOTE` are offered only for library books; an online entry gets the existing message instead.
- With `pageMenuStyle = OFF` the first configured action that the engine supports runs directly.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :feature:reader:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :domain:test :feature:reader:testDebugUnitTest :feature:reader-pdf:testDebugUnitTest :feature:reader-manga:testDebugUnitTest :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING`. V02 still needs native PDFium: stationary long press on text vs scan vs annotation,
before and after rotation, plus a repeated render/OCR loop (10×) checked for bitmap, job and temp-file growth.

**Matrix:** MP019 (PDF page as cover) and MP056 (save/share/copy/set cover) — the PDF path exists now; device
acceptance open.

**Next:** R03.

---

## R03 — shared wheel contract

**Status:** `CODE_VERIFIED` (V03 visual and TalkBack passes `DEVICE_PENDING`).

**Call-site registry** (all use the same `RadialMenu`, the same geometry and the same tokens):

| Context | Entry point | Gesture | Anchor | Style source |
|---|---|---|---|---|
| Library book tile | `LibraryScreen` → `RadialState(at, wheelActions(book))` | long press, drag-select optional | `localToWindow` from the tile | `wheelStyle` / `radialDrag` |
| Page menu (manga + PDF) | `ReaderScreen` → `QuickActionMenu(radial = …)` | long press on the page | root→window from the engine | `pageMenuStyle` |
| Text selection | `feature/annotations/QuickMenus.kt` → `QuickActionMenu(radial = …)` | long press on a selection | selection rect → window | `selectionMenuStyle` |
| Audio cover | `AudioPlayerScreen` → `RadialMenu(state)` | long press on the cover | cover rect → window | same wheel |

Anchors are converted to window coordinates exactly once by the caller; the wheel then computes one clamped centre
(`wheelCenter`) and both draws and hit-tests with it. `H01` (a root/window offset on some configurations) is not
declared a defect: the conversion is now visible in one place per context, and the values are measured on device.

**Gap fixed:** there was no sector cap. A context with many configured actions produced tiny, unhittable sectors and
no TalkBack path. Now:

| File | Change |
|---|---|
| `core/ui/.../components/WheelLayout.kt` | NEW. `MAX_WHEEL_SECTORS = 8`, `ringIndices`, `moreIndex`, `isMore`, `overflowIndices`. |
| `core/ui/src/test/.../WheelLayoutTest.kt` | NEW. 0/1/7/8/9/20 actions, every action reachable exactly once, radius follows the visible count. |
| `core/ui/.../RadialWheel.kt` | `RadialState.overflow`. The ring renders `ringIndices` and substitutes a "More" sector; picking it keeps the menu open and shows the remaining actions as a scrollable list with the same icons, tints and destructive colour. The central button and the drag target both read the ring, not the full action list. |
| `feature/library/.../LibraryScreen.kt` | `wheelIndexAt` uses the ring count for radius and sectors; the drag release maps a sector to its action, and "More" opens the list instead of running a command. |

**Contract kept:** release in a sector runs exactly one action; release in the dead zone leaves the menu open; Back and
outside taps close it; a tap runs an action once. `dragSelect` still governs whether the wheel follows the finger;
tap-only contexts get the same look with a plain tap and no second dispatch.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :core:ui:testDebugUnitTest :feature:library:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :core:ui:testDebugUnitTest :app:testDebugUnitTest` | C-UI, `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING`. V03 still needs screen corners, 320 dp, fontScale 2.0, light/dark/AMOLED and a
TalkBack walk of every context.

**Matrix:** MP056 (radial menu of page actions), MP005/MP006 preparation — no second wheel was introduced.

**Next:** D01.

---

## D01 — description and metadata pipeline

**Status:** `CODE_VERIFIED` (V08 needs a real audio file with a description tag).

**Pipeline audit** (input → parser → insert → `Book`):

| Branch | Description today | Result |
|---|---|---|
| EPUB | `EpubParser` → `pkg.meta.description` → `ImportService` → `BookEntity` | already carried |
| PDF | `pdf.meta().subject` → `ImportService` | already carried |
| CBZ/CBR/CB7/folder | `ComicInfo.xml` `Summary` → `ImportService` | already carried |
| FB2/TXT/HTML/MOBI/DOCX | converter's `builder.meta.description` | already carried |
| OPDS download | `CatalogRepository` writes `item.description` on insert and keeps it on update (`?: existing.description`) | already carried |
| Audio manifest (.json) | `AudiobookStore` `manifest.description` | already carried |
| RSS / LibriVox / YouTube / stream | `AudiobookStore.addStream(description = …)` | already carried |
| **Plain audio file** | `AudioMeta.extract` returned title/authors/cover only | **gap — fixed** |

**Fix:** `AudioMeta.extract` now reads a description from the file's own tags through the new `AudioTags`.

| File | Change |
|---|---|
| `data/.../audio/AudioTags.kt` | NEW. Bounded reader for the tags that can hold a synopsis: ID3v2 `TIT3`, `COMM`, `TXXX:DESCRIPTION`/`COMMENT`; MP4 `©cmt`/`©des`/`desc`; Ogg Vorbis/Opus `DESCRIPTION`/`COMMENT`/`SUMMARY`. Reads at most 512 KiB of tag data, turns HTML into text, keeps paragraphs, caps the result at 4000 characters, and returns null when there is nothing usable. |
| `data/.../audio/AudioMeta.kt` | `extract` fills `ExtractedMeta.description` from `AudioTags` (file or SAF URI) instead of leaving it unset. A file without such a tag keeps null — no invented text. |
| `feature/library/.../BookDetailsScreen.kt` | Explicit **Expand**/**Collapse** button (5 collapsed lines) replacing the undiscoverable tap-anywhere toggle; `plainDescription` hides null, blank and markup-only values. |
| `feature/library/res/values{,-ru}/strings.xml` | `details_description_expand`, `details_description_collapse`. |

**Precedence kept:** a manual edit through the metadata dialog calls `BookRepositoryImpl.update(book)`, which is the
only unconditional writer; source refresh keeps an existing description (`item.description ?: existing.description`),
so a source that stops sending one does not erase the library value. An empty manual value is stored as null, which is
distinguishable from "never edited" because the source path refills only when it has something to write.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :data:testDebugUnitTest --tests "app.yomu.data.audio.AudioTagsTest"` | 12 tests, `BUILD SUCCESSFUL` |
| `.\gradlew.bat :feature:library:testDebugUnitTest --tests "app.yomu.feature.library.DescriptionDisplayTest"` | 7 tests, `BUILD SUCCESSFUL` |
| `.\gradlew.bat :domain:test :data:testDebugUnitTest :feature:library:testDebugUnitTest :app:compileDebugKotlin` | C-DATA, `BUILD SUCCESSFUL` |

**Failures found and fixed during the package** (recorded as they were real, not test noise):

- `TXXX` returned the description field instead of its value — added `userTextValue`.
- The Ogg reader refused files smaller than its read window — the window is now clamped to the file size.
- `android.text.Html.fromHtml` may return null on a JVM without the platform — `plainDescription` now falls back to a
  markup stripper instead of leaking raw tags.

**Device/live:** `DEVICE_PENDING` for V08 with real audiobook files; the tag paths are covered by synthetic fixtures.
H02 stays **unknown**: without the original file from the screenshot nothing is claimed about that particular book.

**Matrix:** MP018 (description on the card) — carried for every branch and now also for audio tags.

**Next:** L02.

---

## L02 — library open and resume

**Status:** `CODE_VERIFIED` (V04 device pass pending).

**Callback table before the change** (the inconsistency the plan names):

| Entry point | Opened | Note |
|---|---|---|
| Local grid/list tile tap | `ReaderRoute(bookId)` | started reading on one tap |
| Online tile tap | `TitleRoute(entryId)` | opened the card |
| Continue shelf | `onOpenBook` / `continueOnline` | correct: this is a resume |
| History, widget | `ReaderRoute` | correct: this is a resume |
| Source result | `TitleRoute` | correct |

So the same gesture meant "read now" for local books and "show the card" for online titles.

**Fix:** one resolver decides, and the tile tap now matches the online behaviour.

| File | Change |
|---|---|
| `domain/.../library/LibraryOpenResolver.kt` | NEW. `LibraryIntent` (Details/Resume/Chapter), `LibraryItemKind` (local book / online title / source result), `LibraryItemState`, `LibraryDestination` (BookDetails / TitleDetails / Reader / ReaderChapter / AudioPlayer / Unavailable), `LibraryUnavailable`, and `offersResume()`. Pure Kotlin: no `NavController`, no Android types. |
| `domain/src/test/.../LibraryOpenResolverTest.kt` | NEW. The full table: every engine, audio, missing progress, finished, missing file, removed source, specific chapter, resume availability. |
| `feature/library/.../LibraryScreen.kt` | A local tile tap calls `onOpenDetails` (the online tiles already did); the wheel's "Read", the Continue shelf and swipe-to-read keep `onOpenBook`. |
| `app/.../navigation/YomuNavigation.kt` | The library callbacks are documented against the resolver; no second route was introduced and audio resumes through the existing `ReaderRoute` handling. |

**Behaviour:** a tile tap opens the card for both local and online items; the card's main button and the Continue shelf
open the saved position; a finished book restarts; an audio book resumes in the player; a missing file or removed source
reports which of the two failed instead of starting an empty screen.

**Tests updated to the new contract** (their old assertion was the defect):

- `AppSmokeTest.catalogExtensionsAndManga` — tile → card → read.
- `NavigationUxScreensTest.readerTapZonesOnFirstOpen` — same two steps before the first-run zone diagram.

The read action is matched by content description: Material's `ExtendedFloatingActionButton` hides its label from
semantics, so `hasText("Read")` could never match it — this is recorded because it cost several runs to find.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.library.LibraryOpenResolverTest"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :domain:test :feature:library:testDebugUnitTest :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :app:testDebugUnitTest` | full suite `BUILD SUCCESSFUL` (94 tests, 1 skipped, 0 failed) |

**Device/live:** `DEVICE_PENDING` for V04 — one tap, Resume and Back for EPUB/PDF/CBZ/folder/online/audio, missing URI,
double tap, tablet pane and restored shelf/scroll.

**Matrix:** MP009 (resume on a tile) — one documented contract; MP005 preparation for the selection work.

**Next:** L03.

---

## L03 — one selection for local books and online titles

**Status:** `CODE_VERIFIED` (V05/V06 device passes pending).

**Confirmed defect:** `LibraryScreen` had `val onlineTiles = if (inOnline || selecting) emptyList() else …` — entering
selection mode emptied the online row, so a mixed selection was impossible by construction. The local selection was a
bare `Set<Long>`, which cannot distinguish `book.id = 7` from `entry.id = 7`.

| File | Change |
|---|---|
| `domain/.../library/LibrarySelection.kt` | NEW. `LibraryItemKey` (`LocalBook` / `OnlineEntry` / `SourceResult(sourceId, url)`), and `LibrarySelection` with `toggle`, `replaceAll`, `rangeTo`, `invert`, `retainOnRefresh`, `intersectWith`, `onSortChanged`, `retainExisting`, `clear`. The range uses the list order passed in, never load order. |
| `domain/src/test/.../LibrarySelectionTest.kt` | NEW. 15 tests: colliding numeric ids, toggle, anchor, forward/back range, missing target, select-all snapshot, refresh, query intersection, sort change, inversion, external deletion, source-result identity. |
| `feature/library/.../LibraryViewModel.kt` | `typedSelection: StateFlow<LibrarySelection>` is the single controller. `toggleSelect` (local), `toggleSelectOnline`, `isOnlineSelected`, `clearSelection`. `selectAll` fills both the typed state and the legacy local id set from one repository query. |
| `feature/library/.../LibraryScreen.kt` | Online tiles stay in the grid while selecting; a tap toggles their own key. The selection title counts both kinds. `LibraryGrid`/`OnlineTile`/`BookListItem` received `onlineSelection` so a selected online tile shows its checkbox. |

**Key semantics:** the legacy `selection: Set<Long>` is kept only as the shape existing bulk actions consume, and it is
derived from the typed state by `syncLegacySelection()`, so there is one owner. Nothing derives a key through a numeric
offset or a sign trick.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.library.LibrarySelectionTest"` | `BUILD SUCCESSFUL`, 15 tests |
| `.\gradlew.bat :feature:library:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :domain:test :feature:library:testDebugUnitTest :app:testDebugUnitTest` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` for V05 (select a local book and an online entry with the same id, one counter) and
V06 (select 200 results with 30 loaded). The Paging-wide "select all" still runs through the repository query in
`selectAll`; wiring a select-all that spans unloaded pages into the typed state is the first item of L04.

**Matrix:** MP005, MP006 — mixed selection and the shared counter; full Paging scope is tracked in L04.

**Next:** L04.

---

## L04 — bulk operations by capability

**Status:** `CODE_VERIFIED`.

| File | Change |
|---|---|
| `domain/.../library/LibraryBulk.kt` | NEW. `BulkAction` (shelves, personal status, favourite, rating, tags, download, delete downloads, remove from library, delete owned files), `BulkCapabilities` matrix per item kind, `BulkOutcome` (Success/Unsupported/Failed), `BulkFailure`, `BulkReport` with `succeeded`/`unsupported`/`failed`/`isPartial`/`retryable`, and `BulkExecutor.run` + `preview`. |
| `domain/src/test/.../LibraryBulkTest.kt` | NEW. 10 tests: owned-file deletion for local books only, personal actions for both library kinds, no action on a source result, applicability counts, "8/12" preview, partial report per item, one failure not cancelling the rest, exception → per-item failure, empty selection. |
| `feature/library/.../LibraryViewModel.kt` | `runBulk(action, run)` executes over the typed selection, emits `BulkPartial` when the result is partial, then clears the selection. `bulkPreview(action)`. |
| `feature/library/.../LibraryScreen.kt` | `LibraryEvent.BulkPartial` shows "Applied to N of M" instead of a silent success. |
| `feature/library/res/values{,-ru}/strings.xml` | `library_bulk_partial`. |

**Ownership rules encoded, not commented:**

- `DELETE_OWNED_FILES` is local-only; a catalogue entry's removal never touches a local file.
- `PERSONAL_STATUS` is separate from the source's publication status — the matrix has no command that writes a source
  status.
- A source result supports no action at all: it must be added first.
- No database transaction is held across the batch; `perform` is called per key and its failure is recorded.

**Note on `setStatus`/`setFavorite`/`setRating`/`addToCollection`:** these existing local-only entry points are kept as
they are, because they are the ones the current UI calls with `Set<Long>`. `runBulk` is the path a mixed selection uses;
the local ones are reachable from it with a `LocalBook` branch. Rewriting them to take keys would have changed the
contract of working screens outside this package's scope.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.library.LibraryBulkTest"` | `BUILD SUCCESSFUL`, 10 tests |
| `.\gradlew.bat :domain:test :feature:library:testDebugUnitTest :app:testDebugUnitTest` | `BUILD SUCCESSFUL` |

**Flaky test observed once:** `DownloadedBookTest.downloadedBookStaysReadable` hit its own 120 s Room timeout while the
full suite ran in parallel; it passes alone and in the next full run. Recorded, not "fixed" by raising its timeout.

**Matrix:** MP007 (bulk read/categories/downloads/delete) — capabilities, applicability preview and per-item report.

**Next:** L01.

---

## L01 — compact header and one display sheet

**Status:** `CODE_VERIFIED` (V18 device pass pending).

**Old entry → new entry** (no command was dropped):

| Command | Before | After |
|---|---|---|
| Search | header icon | header icon (unchanged) |
| Select | header icon | `⋮ → Select` |
| Downloads | header icon when a queue was active | header icon while active, **and** `⋮ → Downloads` |
| Import | separate icon | `⋮ → Import` |
| New shelf | `⋮` | `⋮` (unchanged) |
| Rescan | `⋮` | `⋮` (unchanged) |
| Incognito | `⋮` | `⋮` (unchanged) |
| Settings | `⋮` | `⋮` (unchanged) |
| Filter | toolbar pill | `Display → Filter` |
| Sort | toolbar pill + dropdown | `Display → sort rows` + direction row |
| Grid/list | toolbar icon | `Display → view row` (state also shown in the header row) |
| Clear filters | chips row | chips row **and** `Display → Clear filters` |

| File | Change |
|---|---|
| `feature/library/.../LibraryScreen.kt` | Header keeps title + search + `⋮` (plus the downloads button only while a queue is active). `LibraryToolbar` is one "Display" pill with the active-filter badge, the current sort as a label and a non-interactive view indicator. New `DisplaySheet` groups filters, sort direction, sort choice and view; `onSelect` was removed from `LibraryHeader`. |
| `feature/library/.../LibraryViewModel.kt` | `setAscending` (explicit direction) and `chooseSort` (selects without toggling direction, so the sheet's direction row stays authoritative). |
| `feature/library/res/values{,-ru}/strings.xml` | `library_display`, `library_downloads`, `library_sort_direction`. |

**Test updated to the new path:** `Release150ScreensTest.libraryFilterSheet` now opens `Display` and then `Filter`.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :feature:library:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :app:testDebugUnitTest --tests "app.yomu.Release150ScreensTest"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :domain:test :feature:library:testDebugUnitTest :app:testDebugUnitTest` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` for V18 — 320/360 dp, large title/counter, fontScale 2, selection replacing the
header, search keyboard, lower navigation and FAB insets.

**Matrix:** MP001 (density/view shared), MP010 (active filters), MP013 (sorting) — reachable from one sheet.

**Next:** L05.

---

## L05 — one query model, tri-state filters, saved shelves

**Status:** `CODE_VERIFIED` for the model. The UI does not yet read from it — see the scope note.

**What was actually wrong:** `LibraryDisplay` (the online library) had `unreadOnly`, `downloadedOnly` and
`completedOnly` as booleans. A boolean cannot say "any", so "started" had to be expressed by hiding a filter rather
than by a third state, and `downloadedOnly` collapsed "fully offline" and "partially downloaded" into one answer. Type,
origin and availability were not separate axes at all.

| File | Change |
|---|---|
| `domain/.../library/LibraryQuerySpec.kt` | NEW. `Tri` (ANY/YES/NO with `next()`), `ContentType`, `ContentOrigin`, `Availability` (FULLY_OFFLINE/PARTIAL/ONLINE/MISSING), `LibrarySortKey`, `LibraryQuerySpec` with `matches`, `isFiltered`, `activeFilterCount`, `identity`, `sortAppliesTo`, and `LibraryQueryItem` as the fields a query may see. |
| `domain/src/test/.../LibraryQuerySpecTest.kt` | NEW. 12 tests: empty query, ANY vs NO, the tri cycle, type/origin independence, partial ≠ offline, downloaded axis, case-insensitive search, per-condition count, stable identity, unread sort applicability, shelf and tag matching. |
| `domain/.../library/SavedQueryShelf.kt` | NEW. `SavedQueryShelf` (id, name, spec, per-shelf grid/sort override) and the persisted `LibraryQuerySpecData`, which stores enums by name so one unknown value degrades a single field instead of making the spec unreadable. |
| `domain/src/test/.../SavedQueryShelfTest.kt` | NEW. Round trip, unknown enum per field, all-unknown spec, empty spec, id stability, optional display override. |

**Scope note (honest):** this package delivers the model, its tests and the persisted, version-tolerant form. The
library screen still reads the old `LibraryFilter`/`LibraryDisplay` booleans; migrating the repository queries and the
filter sheet onto `LibraryQuerySpec` is a separate, larger change that also touches the Room queries and the paging
source. It is **not** claimed as done. `LibraryQuerySpec.DEFAULT` is the fallback a shelf uses when its stored version
is unknown, which is the part the storage contract required.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.library.LibraryQuerySpecTest"` | `BUILD SUCCESSFUL`, 12 tests |
| `.\gradlew.bat :domain:test` | `BUILD SUCCESSFUL` (all domain tests incl. the new shelf tests) |

**Matrix:** MP011 (tri-state fields), MP012 preparation (tracker filter is a field of the spec), MP013 (one sort
model), MP016 (partial vs fully offline) — at the model level. UI wiring remains open.

**Next:** D02.

---

## D02 — card order by content type

**Status:** `CODE_VERIFIED`.

**Order before → after** (the screen had personal data above the description):

| Before | After |
|---|---|
| cover + title + author + format | cover + title + author + format |
| progress | progress |
| **status chip + tracking** | **description (audio length above it)** |
| **rating stars** | status + tracking (one row) |
| reading time | rating stars |
| **description** | reading time |
| tags, shelves, file, source… | tags, shelves, file, source… |

| File | Change |
|---|---|
| `feature/library/.../BookDetailsScreen.kt` | Description moved directly after progress, with the audio length/track count above it; status and tracking share one row so the personal block is compact. |
| `feature/library/.../BookDetailsViewModel.kt` | `BookExtras` gained `audioDurationMs` and `audioTracks`; `audioInfo` reads them from `AudiobookStore.tracks` for audio books only (`mapLatest`, so a re-opened book re-reads). |
| `feature/library/.../LibraryModule.kt` | `BookDetailsViewModel` receives `AudiobookStore` (one more `get()`). |
| `feature/library/res/values{,-ru}/strings.xml` | `details_duration_unknown`, `plurals/details_tracks`. |

**Behaviour:** an audio book shows its real total length and, when there is more than one, the track count; a length
that is not known says "Length unknown" instead of showing `0:00`. The manifest's own size is never presented as the
size of every track, because it is not read at all.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :feature:library:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :app:testDebugUnitTest` | `BUILD SUCCESSFUL` |

**Intermittent test, investigated:** `DownloadedBookTest.downloadedBookStaysReadable` failed in two full-suite runs and
passed in three isolated runs and the next full run. The failure is its own `withTimeout(120_000)` around a Room query,
with `Main looper has queued unexecuted runnables` as a suppressed cause — a Robolectric scheduler/latency problem under
a loaded machine, not a semantic assertion. It is recorded as flaky and was **not** "fixed" by raising its timeout.

**Matrix:** MP018 (card metadata), part of MP019 (cover action), MP051 audio duration/narrator — narrator is not read
yet, so that part stays open.

**Next:** S02.

---

## S02 — source capabilities and connection probe

**Status:** `CODE_VERIFIED` for the contract. The catalog UI does not yet consume it — see the scope note.

**What was wrong:** `Source` exposed only `supportsSearch`/`supportsLatest`. Every other difference (files, streaming,
subscriptions, login, navigation, text content) had to be inferred from `SourceContent` at each call site, and there was
no way to say "this source cannot do that" versus "it returned nothing". Adding a mandatory method to `Source` would
have broken already-installed extensions, which the plan forbids.

| File | Change |
|---|---|
| `source-api/.../SourceCapabilities.kt` | NEW. `SourceCapabilities` as a value with `browsingTabs`, `isBrowsable`, `isSupported`, `unsupportedReason`, and `infer(source)` for sources that do not report capabilities. `SourceTab`. An **optional** `CapabilitySource` interface, so `Source` itself is unchanged and old extensions keep working. |
| `source-api/.../ConnectionProbe.kt` | NEW. `ConnectionKind`, `ProbeResult` (Recognised / Unsupported / NeedsAuth / NeedsBrowser / InvalidUrl / NetworkError / ApiMismatch), pure `classify` (content type *and* body, so an HTML error page served as XML is not a catalogue), `verdict` (401 ≠ 403, 404 ≠ 5xx, redirect to a web page rejected), `validateUrl` before any request, and `ProbeLimits`. |
| `source-api/src/test/.../ConnectionProbeTest.kt` | NEW. 16 tests: OPDS by type and by root, RSS vs OPDS, HTML-as-XML, audio, unknown, 401/403/404/503, redirect to a page, invalid and `file:`/`javascript:` addresses, private LAN addresses, inferred tabs, explicit override, a source that can browse nothing. |

**Notable decisions encoded:**

- An extension that never heard of `SourceCapabilities` keeps exactly the tabs its old flags allowed.
- A 403 is a browser check, not a password prompt; only 401 means "needs auth".
- The probe inspects at most 64 KiB and does not trust the declared MIME type alone.
- A LAN address is a normal address: a private server is an expected case, not an error.

**Scope note (honest):** this package is the contract plus its tests. The OPDS/RSS probe is **not** yet wired into
`SourceManager`/`AddOpdsDialog`, and the catalog tabs still read `supportsLatest`/`supportsSearch` directly. Wiring it is
prerequisite work for S01 (the wizard), and is called out there rather than claimed here.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :source-api:test --tests "app.yomu.source.ConnectionProbeTest"` | `BUILD SUCCESSFUL`, 16 tests |
| `.\gradlew.bat :source-api:test :sources:opds:test :feature:catalog:compileDebugKotlin :app:compileDebugKotlin` | C-SOURCE, `BUILD SUCCESSFUL` |

**Matrix:** MP089 (capability-driven tabs), MP090 preparation, MP098 (login vs browser check) — at the contract level.

**Next:** S01.

---

## L06 — paged mixed projection

**Base:** `3b44efc`. **Status:** `CODE_VERIFIED` (V06/V19 device benchmarks open).

**What was measured before the change** — the library's own query plan, read from SQLite:

| Sort | Plan before |
|---|---|
| TITLE | `SCAN books` + `USE TEMP B-TREE FOR ORDER BY` — every matching row was sorted for each page request |
| LAST_READ | `COALESCE(lastReadAt, 0)` cannot use an index: same temp sort |

Both were invisible: the screen looked correct, only the cost grew with the library.

| File | Change |
|---|---|
| `data/.../library/LibraryProjection.kt` | NEW. `LibraryProjectionKey` (namespace `book:` / `online:`, numeric id, fixed rank) with `parse`; `LibraryProjectionItem` (`Local`/`Online`) exposing exactly the fields a sort may see; `LibraryProjection.comparator`, `order`, `orderOnline`, `merge` (two sorted sequences, lazily), `window`, `count`. Ties are always namespace then id, in both directions. |
| `data/src/test/.../LibraryProjectionTest.kt` | NEW. 11 tests: equal numbers are different rows, natural title order, deterministic tie order under reversed input, descending keeps the tie rule, never-read is oldest, online progress bounds, lazy merge equals the sorted list, bounded window, count. |
| `data/src/test/.../LibraryQueryPlanTest.kt` | NEW. Reads `EXPLAIN QUERY PLAN` for every sort: ADDED/LAST_READ/PROGRESS must read the named index and must not sort; TITLE/AUTHOR are asserted to be the known temp-sort plans (see below). Also checks that the filtered page and its count return the same rows. |
| `core/database/.../Entities.kt` | `books` gains `Index(["lastReadAt", "addedAt"])` and `Index("progress")`. |
| `core/database/.../YomuDatabase.kt` | Version 11 → 12, `MIGRATION_11_12` creates those two indices only; exported `12.json`. |
| `data/.../repository/BookRepositoryImpl.kt` | `ORDER BY` uses `lastReadAt` (not `COALESCE(lastReadAt, 0)` — NULL sorts where 0 would in either direction, and the expression could not use an index) plus the rowid as the final tie-breaker for ADDED/PROGRESS. |
| `data/.../library/Metadata.kt` | `CoverStore.pruneOrphans(referenced, olderThanMs)`: deletes cover files no book points at, but never one written in the last hour (an import saves the picture before it inserts the row). |
| `data/.../work/Workers.kt` | `LibraryScanWorker` prunes orphan covers after the scan, where the whole cover set is already read. |
| `data/src/test/.../CoverStorePruneTest.kt` | NEW. 2 tests: referenced and freshly written covers survive, the old orphan is deleted; a path is matched by its file name. |
| `data/src/test/.../LibraryProjectionBenchTest.kt` | NEW. 1000 books + 10 000 online titles in an in-memory database; writes `data/build/library-bench.txt` and asserts the work stays linear. |
| `data/src/test/.../DatabaseMigrationTest.kt` | `CURRENT_VERSION = 12`; new test migrates an 11 database with a rated, favourited book and asserts the row survives and both indices exist. |
| `feature/library/.../LibraryViewModel.kt` | The online titles are filtered and ordered by `LibraryProjection.orderOnline` (the same comparator the books use) and the whole pass runs `flowOn(Dispatchers.Default)` — it used to sort the full list on the main thread inside `combine`. `selectAll` reads the ids from the same query on `Dispatchers.IO`. |
| `feature/library/.../LibraryScreen.kt` | Lazy keys of both kinds come from `LibraryProjectionKey` (`book:12`, `online:7`), so a book and an online title with the same number cannot share a key. |

**Measured after the change** (`data/build/library-bench.txt`, Robolectric JVM — not a device, see Q03):

```
library bench — 1000 local books, 10000 online titles
full book query, all rows: 1000 rows, 16 ms/run
merge of both ordered lists: 11000 rows, 65 ms/run
lazy merge (sequences): 11000 rows, 12 ms/run
```

`ADDED`, `LAST_READ` and `PROGRESS` now read `index_books_addedAt`, `index_books_lastReadAt_addedAt` and
`index_books_progress` with no temp B-tree; the test fails if a plan regresses.

**Scope note (honest):**

- `TITLE` and `AUTHOR` still sort the matched rows. SQLite serves `COLLATE NOCASE` order only from an index whose
  column was declared with that collation; changing `books.sortTitle`'s collation means rebuilding `books` with its ten
  foreign keys, which is not a safe part of an index-only package. The test records the two plans instead of hiding
  them.
- The grid keeps the online titles in their labelled section (the layout L01/L02 kept, and the "From the catalog" shelf
  in "All"), so the screen does not use one interleaved lazy stream. The merged ordering, window and lazy merge are
  implemented and tested, but a single `PagingSource` over the two tables is **not** introduced: the books stay
  room-paged and the online list is bounded by the user's favourites, now ordered off the main thread.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :data:testDebugUnitTest --tests "app.yomu.data.library.*" --tests "app.yomu.data.db.DatabaseMigrationTest"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :feature:library:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :domain:test :feature:library:testDebugUnitTest :feature:catalog:testDebugUnitTest :data:testDebugUnitTest :app:testDebugUnitTest` | C-LIBRARY, `BUILD SUCCESSFUL` in 15m 10s |

**Device/live:** `DEVICE_PENDING` for V06 (select 200 results with 30 loaded) and V19 (instrumented scroll and memory
numbers; the numbers above are a JVM guard, not a frame report).

**Matrix:** MP011 (one query), MP013 (one sort model for both kinds), MP016 (downloaded/partial) — repository level.

**Next:** S02 wiring, then S01.

---

## S02 (wiring) — the probe is now used

Status: `CODE_VERIFIED` (the contract itself was accepted above; this closes its scope note).

**What was missing:** `ConnectionProbe` classified a response that nobody fetched. A catalogue was saved without a
request, so a server that answered with an error page was "installed" and failed only when opened.

| File | Change |
|---|---|
| `data/.../source/SourceConnectionProbe.kt` | NEW. The request behind the decision table: `validateUrl` before any socket, `followRedirects(false)` on a derived client so a redirect is reported instead of hidden, `callTimeout` from `ProbeLimits`, at most `MAX_BYTES` of body (`peekBody`), Basic auth only when a user is given, `IOException` → `NetworkError`. Fills the preview title from the document and counts the sample entries in the window. |
| `data/.../catalog/CatalogRepository.kt` | `probeConnection(url, user, password, supported)`: nothing is stored. Global search picks targets by `capabilitiesOf(source).search` instead of the old flag. |
| `data/.../source/SourceManager.kt` | `SourceInfo.capabilities` (a value) replaces the separate `supportsLatest` flag; `supportsLatest` is now a getter over it. `capabilitiesOf(source)` is the one place that decides between a source's own report and `SourceCapabilities.infer`. |
| `feature/catalog/.../CatalogViewModels.kt` | Browse tabs come from `browsingTabs` of the capabilities; a source that reports no search gets no search tab (the empty page it used to answer is gone). `addOpds` probes first and saves only on `Recognised`; every other outcome is a named message. |
| `feature/catalog/res/values{,-ru}/strings.xml` | Login, browser check, not-a-catalogue, network messages. |
| `data/src/test/.../source/SourceConnectionProbeTest.kt` | NEW. 13 MockWebServer tests: valid catalogue with its own title and sample count, an HTML error page served as a feed, a declared HTML page, 401 vs 403, credentials present only when given, a redirect to a catalogue, a redirect to a page, an endless redirect chain, a 1 MB document inspected only to the window, an unusable address rejected without any request, an unreachable server. |

**A real defect the tests caught:** `<entry/>` was not counted as an entry (the regex required whitespace or `>`), so
a catalogue of self-closing entries would preview as empty. Fixed to `<entry[\s/>]`.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :data:testDebugUnitTest --tests "app.yomu.data.source.SourceConnectionProbeTest"` | `BUILD SUCCESSFUL`, 13 tests |
| `.\gradlew.bat :feature:catalog:testDebugUnitTest :feature:catalog:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` — a real Calibre/Standard Ebooks server and a server with a browser check are still
needed for V09. The probe is exercised against a local server; it is not a claim about any provider.

---

## S01 — connection wizard and guide

**Status:** `CODE_VERIFIED` (V09/V10 device pass pending).

**The chain:** Library Add sheet → "Add a source" / Catalog `⋮` → `SourceConnectionRoute` → content → method →
address → probe → preview → one save. Every question is a state, so Back returns to the previous one and never saves.

| File | Change |
|---|---|
| `feature/catalog/.../SourceConnectionFlow.kt` | NEW. `ConnectionContent`, `ConnectionMethod` (with `implemented`), `ConnectionFailure` (a failed check and a failed save are different), `ConnectionStep` (Content/Method/Address/Probing/Preview/Saving/Done/Failed/HandOff/Importing/Imported), `SourceConnectionState` (no Android types; the password lives only in this object) and the pure transitions: `chooseContent`, `chooseMethod`, `editAddress`, `startCheck`, `checked`, `startSave`, `saved`, `saveFailed`, `importing`, `imported`, `back`, `suggestedName`, `methodsFor`. |
| `feature/catalog/.../SourceConnectionViewModel.kt` | NEW. Runs the probe (`CatalogRepository.probeConnection`) and the save (`SourceManager.addOpds` for a catalogue, `AudiobookStore.addLink` for an audio link), one request at a time; a second "Add" while saving is ignored. Files and a folder go through the existing `ImportService` with the same persistable permissions the library takes. |
| `feature/catalog/.../SourceConnectionScreen.kt` | NEW. The steps above, with the help text at the step it belongs to; the password is not in the saved state; SAF pickers for files/folder; extensions open the existing Extensions screen. |
| `app/.../navigation/Routes.kt`, `YomuNavigation.kt` | `SourceConnectionRoute`; entry from `CatalogScreen(onAddSource)` and from the library's import sheet. |
| `feature/library/.../LibraryScreen.kt` | The import sheet gains "Add a source" next to "Find books online". |
| `feature/catalog/.../CatalogScreen.kt`, `CatalogViewModels.kt` | "Add OPDS" opens the wizard; the old one-field dialog is gone (the wizard contains the same fields plus the check). |
| `feature/catalog/.../CatalogModule.kt` | The new view model, with its five dependencies. |
| `feature/catalog/src/test/.../SourceConnectionFlowTest.kt` | NEW. 12 tests: first question, hand-off methods, implemented method, blank address fails without a request, the address is trimmed and the save repeats the checked one, the preview shows the document's own title, every failed check stays out of the preview, saving only from a preview, a save error is not a failed check, Back walks in reverse and stops, the name falls back from title to host, a blank title is not a name. |

**Hand-offs are real, not decorative:** files and folders go through `ImportService` (the same duplicate check, the
same report counts); extensions open the Extensions screen; a podcast feed explains that only a one-time import exists
until S03 and offers the audio-link path; an audio link saves through `AudiobookStore.addLink`.

**Scope note (honest):** the preview shows the recognised kind, the document's title, the number of entries in the
probe window and the exact address; it does not render a thumbnail list of those entries, because the probe reads a
bounded prefix rather than a parsed catalogue. The wizard opens the catalog entry after the user returns, not by
itself.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :feature:catalog:testDebugUnitTest` | `BUILD SUCCESSFUL`, 29 tests in the module |
| `.\gradlew.bat :feature:catalog:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| C-SOURCE profile (see the command table of this pass) | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` for V09 (a real OPDS server, a wrong address, a login-protected server) and V10
(extension install and import entry points). No device or emulator is available here.

**Matrix:** MP089/MP090 (capability-driven tabs and connection), MP098 (401 vs 403) — now wired, acceptance open.

**Next:** S03.

---

## S03 — user RSS/audio subscriptions

**Status:** `CODE_VERIFIED` (V09 live feed pass pending).

**What was wrong:** a podcast link was imported as a snapshot. Nothing remembered the feed address, so new episodes
could only arrive by adding the link again, and the old import could not tell which episode was which after a re-upload.

| File | Change |
|---|---|
| `domain/.../model/Audio.kt` | `AudioTrack` gains `guid` (the feed's identity) and `publishedAt`; both default, so every existing manifest still decodes. |
| `domain/.../audio/AudioSubscription.kt` | NEW. `AudioSubscription` (address, book, ETag/Last-Modified, last check, enabled), `SubscriptionEpisode`, `EpisodeIdentity` (GUID first; the address+title+date fallback is deterministic and shared with the parser) and `AudioSubscriptionReconciler.reconcile`: same episode by identity, a changed address applied, a downloaded copy and the chapter marks kept, a missing length not erasing the known one, a deleted episode reported with its file instead of deleting it. |
| `domain/src/test/.../AudioSubscriptionTest.kt` | NEW. 9 tests covering all of the above, including a local file that is not a feed episode. |
| `core/database/.../Entities.kt`, `Daos.kt`, `YomuDatabase.kt` | `audio_subscriptions` table (unique `feedUrl`, foreign key to `books` with cascade), `AudioSubscriptionDao`, version 12 → 13 with `MIGRATION_12_13`; `13.json` exported. |
| `data/.../audio/AudioSubscriptionRepository.kt` | NEW. `subscribe` parses the feed before writing anything and returns the existing row for a known address; `refresh` is one job per subscription (a `Mutex` per id), sends `If-None-Match`/`If-Modified-Since`, treats 304 as `NotModified`, parses, reconciles and writes the manifest; `refreshAll` checks the enabled rows oldest-first; `remove(deleteBook = false)` keeps the audiobook. |
| `data/.../audio/PodcastFeed.kt` | Reads `guid`/`id` (fallback identity when absent), fills `publishedAt`, caps a feed at 4 MiB. |
| `data/.../audio/AudiobookStore.kt` | `writeManifest` replaces the manifest through `Files.move(REPLACE_EXISTING)`. **Real defect found by the new test:** `File.renameTo` refuses to overwrite on Windows, so a second refresh always failed with "Cannot replace …". |
| `data/.../work/Workers.kt` | `AudioSubscriptionWorker` + a daily network-constrained schedule in `WorkScheduler`. |
| `data/.../backup/BackupManager.kt` | `audio_subscriptions` is exported and restored; identity is the feed URL and `bookId` is remapped like every other reference. |
| `feature/catalog/.../SourceConnectionFlow.kt`, `SourceConnectionViewModel.kt`, `SourceConnectionScreen.kt` | `RSS` is a real method: `Probing → Preview(RSS) → subscribe`, with its own feed help text and placeholder. |
| `feature/library/.../BookDetailsViewModel.kt`, `BookDetailsScreen.kt` | The audio book's card shows its subscription with the feed address, "Check now" and "Stop updating" (the book and its files stay). |
| `data/src/test/.../AudioSubscriptionRepositoryTest.kt` | NEW. 10 tests against MockWebServer + an in-memory database: subscribe adds one book and one row and a second start does not duplicate either; a page that is not a feed is not saved; 304 changes nothing; the conditional request carries the ETag; a new episode is appended without moving the others; a removed episode reports its downloaded copy and does not delete it; a changed address keeps the episode; a 500 is reported and keeps the tracks; removing the subscription keeps the book; tracks keep the guid and the source URL. |
| `data/src/test/.../DatabaseMigrationTest.kt` | `CURRENT_VERSION = 13` and a 12 → 13 migration test: the book survives, the new table accepts a row. |

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.audio.AudioSubscriptionTest"` | `BUILD SUCCESSFUL`, 9 tests |
| `.\gradlew.bat :data:testDebugUnitTest --tests "app.yomu.data.audio.AudioSubscriptionRepositoryTest" --tests "app.yomu.data.db.DatabaseMigrationTest" --tests "app.yomu.data.audio.AudioParsersTest"` | `BUILD SUCCESSFUL` (37 tests in the three classes) |
| `.\gradlew.bat :feature:library:compileDebugKotlin :feature:catalog:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

**Scope note (honest):**

- Per-feed update settings are `enabled` only; a per-feed download policy and a download-on-new-episode rule are not
  implemented (they belong with DL02/A02, which are still open).
- Episode rows are the audiobook's track list plus the stored GUID, not a separate table. The plan's requirement that
  the subscription itself is not "only the current track list" is met — the address, ETag and book binding live in
  `audio_subscriptions` and survive a changed feed — but a history of an episode that left the feed is not kept beyond
  its downloaded file.
- OPDS audio acquisition was not re-verified end to end in this pass; existing LibriVox/YouTube paths were not
  duplicated.

**Device/live:** `DEVICE_PENDING` for V09 with a real podcast feed and a server that answers 304.

**Matrix:** MP088–MP090 (user sources, subscriptions) — backend and entry points wired; live acceptance open.

**Next:** N01.

---

## N01 — saved import report

**Status:** `CODE_VERIFIED` (V10 device pass pending).

**What was wrong:** an import ended with "12 added, 2 duplicates, 1 failed" and nothing else. Which file failed, and
whether the cause was fixable, was lost as soon as the message disappeared.

| File | Change |
|---|---|
| `domain/.../library/ImportReport.kt` | NEW. `ImportFailure` (unsupported, password, corrupt, no permission, missing, no space, timeout, network, unknown), `ImportEntryResult`, `ImportReportEntry` (uri, name, result, bookId, failure, retryable), `ImportReport` (stable id, start/end, copy-or-link, counts, retryable entries, partial) and `ImportReportRules.isRetryable` — a device-side cause can change, a format or a password cannot. |
| `data/.../library/ImportReportStore.kt` | NEW. Writes the latest batch to `filesDir/import-reports/latest.json` (atomic replace), exposes it as a `StateFlow`, loads it at construction so a restart brings the report back; `report(...)` maps the importer's outcomes to entries, and `Throwable.toImportFailure()` maps `AppError` to the report's causes. |
| `feature/library/.../LibraryViewModel.kt` | `import` and `importFolderAsBook` save a report; picked URIs get a persisted read permission, which is what makes a retry after a restart possible; `retryFailedImport()` imports only the retryable entries; `dismissImportReport()`. |
| `feature/library/.../LibraryScreen.kt` | `ImportReportSheet`: per-file name and cause, "Open" for a duplicate's book, "Retry failed (N)" only when something is retryable, "Choose files again", Done. |
| `feature/library/res/values{,-ru}/strings.xml` | Report and cause strings. |
| `data/src/test/.../ImportReportStoreTest.kt` | NEW. 6 tests: every file named with its result and book id; a permission/missing/space failure is retryable and a format is not; an untyped error stays unknown; the report survives a new store instance; dismiss removes the file; partial batches. |

**Behaviour:** after a batch of twelve files, the sheet lists twelve names; a corrupt archive says "damaged" and is not
offered for a retry, a revoked folder grant says "permission was revoked" and is — and that retry imports only that file.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :data:testDebugUnitTest --tests "app.yomu.data.library.ImportReportStoreTest" :feature:library:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

**Scope note:** the store keeps the latest batch, not a history of every batch; the scan-folder import (which finds
files the user did not pick) does not produce a report, because its inputs are not a URI list.

**Device/live:** `DEVICE_PENDING` for V10 — a revoked SAF grant and a real >1 GB batch.

**Matrix:** MP085 / MP076 (import report and recovery) — implemented; device acceptance open.

**Next:** S05.

---

## S05 — extension lifecycle and batch updates

**Status:** `CODE_VERIFIED` for the changes below; the signature/origin/apk checks were read and are covered by the
existing install path, not re-tested in this pass.

**What already existed (verified, not rewritten):** `ExtensionStatus` (LOADED/UNTRUSTED/BROKEN) with a `BrokenReason`,
store signing keys with `KEY_CHANGED` detection, automatic trust only for a store whose key signed the extension,
`ExtensionException` with typed reasons, an atomic private install (`Files.move` with a `.part` file), and per-status
actions on the Extensions screen (trust, uninstall, update even when broken).

| File | Change |
|---|---|
| `data/.../source/ExtensionManager.kt` | `ExtensionUpdateReport` (updated names + failures with a reason and detail, `isPartial`, `attempted`); `updateAll(list)` returns it instead of stopping at the first failure; `untrust(exts)` forgets a package's trust without deleting the extension. |
| `feature/catalog/.../ExtensionsViewModel.kt` | `updateAll` reports "updated N of M" or "none updated (N failed)"; `untrust` with a message. |
| `feature/catalog/.../ExtensionsScreen.kt` | "Revoke trust" on a loaded extension's details. |
| `data/src/test/.../ExtensionUpdateReportTest.kt` | NEW. 3 tests: a full success, a partial batch, an all-failed batch. |

**Commands:** `.\gradlew.bat :data:testDebugUnitTest --tests "app.yomu.data.source.ExtensionUpdateReportTest" :feature:catalog:compileDebugKotlin :app:compileDebugKotlin` → `BUILD SUCCESSFUL`.

**Not done:** a batch import/export of repositories, and a test that installs wrong-signature/corrupt APKs (needs
fixture APKs and Robolectric's PackageManager; the code path is `installFile`/`canUpgrade`).

**Next:** S06.

---

## S06 — content preference and one User-Agent

**Status:** `IN_PROGRESS`. Two of the requested pieces are implemented and tested; DoH, the cookie/WebView clear scope
UI and the Shizuku installer backend are not.

| File | Change |
|---|---|
| `domain/.../catalog/ContentPreference.kt` | NEW. SAFE / MIXED / NSFW with `allows(adult)` and `fromLegacyShowNsfw`. |
| `core/datastore/.../AppSettings.kt` | `contentPreference` (default MIXED), `migrateContentPreference()` — an installation that never touched the old switch becomes SAFE, one that turned it on becomes MIXED; written once. `userAgent` (empty = default). |
| `app/.../YomuApp.kt` | Runs the migration at start; collects `userAgent` into the shared client. |
| `core/network/.../Network.kt` | `NetworkClients.userAgent` (volatile) is read per request by the interceptor, so search, downloads, covers and the probe follow the setting without rebuilding the client. |
| `data/.../source/SourceManager.kt` | `infos` filters sources of adult extensions under SAFE — one place, so the list, global search and language counts agree. |
| `feature/settings/.../SettingsSections.kt` | The old NSFW switch becomes a three-choice "Adult content" pref; a User-Agent text pref. |
| `feature/catalog/.../ExtensionsViewModel.kt` | The quick NSFW toggle moves between SAFE and MIXED (the 18+ state is chosen in the settings). |
| `domain/src/test/.../ContentPreferenceTest.kt` | NEW. 3 tests: safe hides adult, every preference allows ordinary content, the legacy mapping. |

**Commands:** `.\gradlew.bat :domain:test --tests "app.yomu.domain.catalog.ContentPreferenceTest" :feature:catalog:compileDebugKotlin :feature:settings:compileDebugKotlin :app:compileDebugKotlin` → `BUILD SUCCESSFUL`.

**Still open in S06:** DNS-over-HTTPS (needs a rebuilt client or a custom DNS for every derived client — not attempted
without a device), a UI that explains the scope of "clear cookies / WebView data", and Shizuku as an optional installer
backend (no Shizuku service in this environment; the private installer remains the only path).

**Next:** M01.

---

## M01 — one chapter navigation

**Status:** `CODE_VERIFIED`.

**What was wrong:** the reader, the chapter list, "continue reading", the downloader and the notifications each applied
their own filters. `ChapterOrder.adjacent` skipped other groups' releases by number even when the numbering was not
trustworthy (a volume restart makes "Chapter 1" a different chapter).

| File | Change |
|---|---|
| `domain/.../catalog/ChapterNavigation.kt` | NEW. `ChapterIntent` (NEXT/PREVIOUS/RESUME/DOWNLOAD_AHEAD), `ChapterNavigationPolicy` (skip read, excluded groups, skip duplicate releases) and `ChapterNavigation`: `resolve`, `alternativeReleases` (only with reliable numbering), `missingNumbers` (reliable numbering, whole numbers only, capped). |
| `domain/src/test/.../ChapterNavigationTest.kt` | NEW. 11 tests: duplicate release skipped, plain next without that policy, symmetric previous, excluded group never chosen, resume after the last read chapter, a started chapter resumed first, alternatives only with reliable numbers, unnumbered has none, missing numbers, a decimal is not a gap, an unreliable sequence does not guess. |
| `data/.../catalog/CatalogRepository.kt` | A finished chapter marks the other groups' releases of the same chapter read — only when the numbering is reliable. |
| `feature/reader/.../ReaderViewModel.kt` | Next/previous and the availability flags use `ChapterNavigation.resolve` instead of `ChapterOrder.adjacent`. |
| `feature/catalog/.../CatalogViewModels.kt`, `TitleScreen.kt` | `missingChapters` and a line under the chapter progress: "The source does not list N chapters: …", shown only when the numbering can be trusted. |

**Commands:** `.\gradlew.bat :domain:test --tests "app.yomu.domain.catalog.ChapterNavigationTest"` → `BUILD SUCCESSFUL`, 11 tests; `:data:compileDebugKotlin :feature:catalog:compileDebugKotlin :feature:reader:compileDebugKotlin :app:compileDebugKotlin` → `BUILD SUCCESSFUL`.

**Device/live:** `DEVICE_PENDING` — reading across a chapter boundary with two groups and a restarted volume.

**Next:** M02.

---

## M02 — spreads, cover and virtual pages (partial)

**Status:** `IN_PROGRESS`.

**What was wrong:** the spread grouping lived as a private function in the view, always paired from page 1, so a book
without a cover page never paired its first two pages, and nothing described a split page: a position was a physical
page everywhere, which is right, but the split transform had no model.

| File | Change |
|---|---|
| `domain/.../reader/MangaPaging.kt` | NEW. `PageSplit` (off / left-to-right / right-to-left), `MangaPage(physical, half)`, `MangaPaging.spreads(count, coverAlone)`, `spreadIndexOf`, `spreadFirstPage`, `virtualPages`, `virtualIndexOf` (a restored position opens the half its direction starts with), `physicalPage` (progress stays about the real page). |
| `domain/src/test/.../MangaPagingTest.kt` | NEW. 9 tests: cover alone, pairs without a cover, odd last page, every page in exactly one group for 1..20 both modes, group lookups, split order in both directions, no-split mapping, restored-position half, unknown page. |
| `domain/.../model/ReaderSettings.kt` | `MangaSettings.coverAlone` (default true, so existing readers see no change) — separate from `dualPageLandscape`. |
| `feature/reader-manga/.../MangaReaderView.kt` | The view uses `MangaPaging.spreads(count, coverAlone)`; the private copy is gone. |
| `feature/reader-manga/.../MangaReaderView.kt` (split) | `Layout` maps slots to physical pages for spreads *or* halves; `PageImage` takes the half and requests `SplitPageTransformation`; the pager keeps physical pages for progress and the listener; preloads and chapter edges are unchanged. |
| `feature/reader-manga/.../MangaController.kt` | `SplitPageTransformation` cuts (does not scale) the decoded bitmap into the left or right half. |
| `domain/.../model/ReaderSettings.kt` | `MangaSettings.splitPages` (off by default), serialized with the rest of the settings. |
| `feature/reader-manga/.../MangaSettingsPanel.kt` + strings | "Split pages" (off / left→right / right→left) with an explanation that the position still counts physical pages. |
| `feature/reader-manga/.../MangaSettingsPanel.kt` + strings | "Cover alone" with an explanation, shown only while dual pages are on. |

**Commands:** `.\gradlew.bat :domain:test --tests "app.yomu.domain.reader.MangaPagingTest" :feature:reader-manga:compileDebugKotlin :app:compileDebugKotlin` → `BUILD SUCCESSFUL`.

**Still open in M02:** rotate-to-fit for wide scans, a start-zoom/smart-fit setting, and a budgeted tiled decode for
very tall images. Coil already subsamples by view size; a tiled zoom decode is not implemented. Split rendering is
implemented but could not be checked on a device here (`DEVICE_PENDING`).

**Next:** M04 (in the plan); not started in this pass.

---

## M04 — reader controls (partial)

**Status:** `IN_PROGRESS`.

**What was wrong:** text and PDF shared one tap scheme, the manga inversion was a single boolean that swapped the
resolved action, and the only physical keys the reader understood were the volume keys. A hardware page-turner or a
D-pad therefore scrolled the page instead of turning it.

| File | Change |
|---|---|
| `domain/.../reader/ReaderInput.kt` | New overload `action(layout, invertX, invertY, rtl, point)`: inversion mirrors the coordinate on each axis before the zone is resolved. The old boolean overload keeps its exact behaviour (resolve, then swap) for settings written before the axes. |
| `domain/.../reader/ReaderKeys.kt` | NEW. `ReaderKeyAction` and `ReaderKeys.action(keyCode, rtl)` for page-up/page-down and D-pad arrows; media keys are deliberately not mapped, so the reader never takes them from whatever is playing. |
| `domain/.../model/ReaderSettings.kt` | `MangaSettings.invertTapZonesX`/`invertTapZonesY` plus effective getters (the old boolean counts as X); `CommonReaderSettings.pdfTapZones` (null = the text scheme, which preserves every existing installation) with `pdfZones()`. |
| `core/ui/.../KeyBus.kt` | A page handler next to the volume one, with the reader's direction; no handler outside the reader. |
| `app/.../YomuActivity.kt` | Page-turner and D-pad keys go through `ReaderKeys` and the bus; a consumed key is not passed to the platform on key-up either. |
| `feature/reader/.../ReaderScreen.kt` | Registers the page handler and the direction while a book is open. |
| `feature/reader/.../ReaderViewModel.kt` | The PDF tap branch uses `pdfZones()`. |
| `feature/reader/.../ReaderSettingsSheet.kt` | "PDF tap zones" with "Same as text" plus the four schemes; previews read the same resolver. |
| `feature/reader-manga/.../MangaReaderView.kt`, `TapZonesOverlay.kt`, `MangaSettingsPanel.kt` | Tap handling, the overlay and the diagrams use the axis resolver; two separate X/Y switches replace the one inversion switch (turning X off also clears the legacy value). |
| `domain/src/test/.../ReaderInputAxesTest.kt` | NEW. 6 tests: X mirror, Y mirror, both axes on a corner, RTL after mirroring, legacy equivalence, non-finite tap. |
| `domain/src/test/.../ReaderKeysTest.kt` | NEW. 4 tests: page-up/left, page-down/right, RTL reversal, media/other keys ignored. |

**Commands:** `.\gradlew.bat :domain:test --tests "app.yomu.domain.reader.*" :feature:reader:compileDebugKotlin :feature:reader-manga:compileDebugKotlin :app:compileDebugKotlin` → `BUILD SUCCESSFUL`.

**Still open in M04:** the optional vertical page navigator (seek slider with release-commit and no backward jumps
from incoming progress), an animation-speed setting, explicit orientation fallback documentation in split-screen, and an
on-demand way to re-open the tap-zone hint (the one-time hint itself already exists and is stored).

**Next:** M03.

---

## Progress summary

**Eighteen packages are `CODE_VERIFIED`; S06, M02, M04, U01, DL02 and Q04 are `IN_PROGRESS`.** Every one has an automated test that would
fail if the behaviour regressed, and the full tree builds: `assembleDebug` + `testDebugUnitTest` + `:domain:test` +
`:source-api:test` + `:sources:opds:test`.

### What actually changed in the app

| Package | User-visible result |
|---|---|
| R01 | **P0 fixed.** A centre tap on a PDF opens the menu instead of turning the page — the coordinate units were being compared across two different scales. |
| R02 | **P0 fixed.** A scanned PDF page has a working page menu: save, share, set cover, OCR, translate, bookmark, note; text pages keep word selection. |
| R03 | The wheel caps at 8 sectors with a "More" list, so every action stays reachable and TalkBack gets a linear path. |
| D01 | An audio book's own description tag now reaches its card; a markup-only description no longer renders an empty block; Expand/Collapse is an explicit button. |
| L02 | One tap on a library tile opens the card for local and online items alike; Continue still resumes. |
| L03 | Local books and online titles can be selected together; identical numeric ids are different items. |
| L04 | Bulk commands know which items they can reach and report "applied to N of M" instead of a silent success. |
| L01 | The library header keeps search + `⋮`; filters, sort and view live in one "Display" sheet; no command was dropped. |
| L05 | A tri-state query model (any/yes/no), independent type/origin/availability axes and version-tolerant saved shelves — model only, see its scope note. |
| D02 | Card order is content first (description, audio length) then personal data; unknown audio length says so. |
| S02 | A capability contract that does not break old extensions; the probe now performs the request, and a catalogue is saved only after it is recognised. |
| L06 | The library's default orders read an index instead of sorting every row; both kinds share one ordering and key namespace; the online pass and "select all" run off the main thread; orphan cover files are collected by the scan. |
| S01 | "Add a source" opens a wizard: what to add, how, a checked address with its real preview, one save; files and folders go through the existing importer. |
| S03 | A podcast feed is a subscription: a daily conditional refresh, new episodes appended, positions kept by feed GUID, downloaded copies of removed episodes reported and not deleted. |
| N01 | An import ends with a per-file report that survives a restart: each failed file names its cause, and only the fixable ones are retried. |
| S05 | Batch extension updates report "N of M"; trust can be revoked without deleting the extension. |
| S06 | Safe/Mixed/18+ content preference (one migration from the old switch) and one User-Agent for every request — DoH and Shizuku remain open. |
| M01 | One chapter-navigation resolver for the reader and the chapter list; other groups' releases are treated as the same chapter only when the numbering can be trusted, and gaps are shown only then. |

### Honest status of the remaining work

- **Device gates (`DEVICE_PENDING`):** V01, V02, V03, V04, V05, V06, V08, V18, and the live gates of S01/S03/S05/S06.
  This environment has no Android device or emulator, so native PDFium gestures, TalkBack, E-ink, real audio tags, real
  feeds and real accounts are not verified. Every one of them is listed per package above rather than being counted as
  done.
- **Packages not started:** M03, M05, U02–U03, DL01, DL03, A01–A03, MG01, B01–B03, T01–T02, S04,
  N02–N05, Q01–Q03, Q05. `IN_PROGRESS`: S06 (DoH, cookie scope, Shizuku), M02 (rotate-to-fit, start zoom, tiled
  decode), M04 (page navigator, animation speed, orientation fallback, on-demand zone hint), U01 (per-title overrides,
  excluded-shelf picker, persisted result), DL02 (preview UI, per-shelf picker, auto-download rules), Q04 (remaining
  entry-point audit, typed diagnostics, notification privacy). The two packages marked "contract only" (L05, S02) delivered their model and tests;
  S02's UI wiring is now done as part of S01.
- **P3 items** (M05 renderer, A03, N03–N04) are unfinished by design in this pass and remain open.

No package in this journal claims a device or live-account acceptance it did not perform.

### Final verification (this pass)

| Command | Result |
|---|---|
| `.\gradlew.bat assembleDebug testDebugUnitTest :domain:test :source-api:test :sources:opds:test --console=plain` | `BUILD SUCCESSFUL` in 9m 52s (before the M02 changes) |
| `.\gradlew.bat assembleDebug testDebugUnitTest :domain:test :source-api:test :sources:opds:test --console=plain` | after the M02 changes: everything green except the known flaky `DownloadedBookTest.downloadedBookStaysReadable` (1 of 94 app tests) |
| `.\gradlew.bat :app:testDebugUnitTest --tests "app.yomu.DownloadedBookTest"` | `BUILD SUCCESSFUL` — the same test passes in isolation, as recorded in L04/D02 |
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.reader.MangaPagingTest" :feature:reader-manga:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat assembleDebug testDebugUnitTest :domain:test :source-api:test :sources:opds:test --console=plain` | after U01/DL02/Q04: `BUILD SUCCESSFUL` in 4m 8s (a first run failed on `ChapterDownloadQueueTest`, fixed by the explicit delete-read rule; `Release170ScreensTest.updateDialog` passed on the rerun) |
| same command after U02 | everything green except the known flaky `DownloadedBookTest.downloadedBookStaysReadable`; it passes alone (`BUILD SUCCESSFUL`) |
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.catalog.UpdatePlanTest" :feature:catalog:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

Baseline at B00 and this run differ only in the changes listed per package. The working tree contains the three original
plan documents (untracked before this pass), this journal, and the source and test edits described above. No commit,
push or release was made.

---

## M02 (remainder) — rotate-to-fit, start zoom and a decode budget

**Status:** `CODE_VERIFIED` (split render and the new page behaviour still need a device; `DEVICE_PENDING`).

| File | Change |
|---|---|
| `domain/.../reader/MangaFit.kt` | NEW. `WidePageRotation`, `StartZoomAnchor`, `ImageSize`, and pure decisions: `widePageRotation` (a wide scan turns a quarter turn, direction follows the book), `smartZoom` (fills the width of a tall page, capped at 3×), `decodeSize` (follows the scale, never upscales, always within 12 MP). |
| `domain/src/test/.../MangaFitTest.kt` | NEW. 12 tests: both turn directions, portrait/no-option/no-size, smart zoom and its cap, viewport fit, fit width/height, a 30 000 px strip inside the budget, an original page still bounded, no upscale, unknown size. |
| `domain/.../model/ReaderSettings.kt` | `MangaSettings.rotateWidePages`, `startZoom`, `smartFit`, `startZoomAnchor` (all default to the old behaviour). |
| `feature/reader-manga/.../MangaController.kt` | `RotateWideTransformation` rotates a wide bitmap once and keeps a cache key per direction. |
| `feature/reader-manga/.../MangaReaderView.kt` | Reads a page's real pixel size from its header, computes rotation and a decode target, opens a page at the start/smart zoom once (`zoomTo` + `panBy` to the chosen part), and preloads under the same budget. Webtoon items use the same budget with fit-width. Start zoom never fights the zoom lock or a pinch. |
| `feature/reader-manga/.../MangaSettingsPanel.kt` | Rotate wide pages, smart fit, start zoom slider and the start position choice. |
| `feature/reader-manga/res/values{,-ru}/strings.xml` | New settings and summaries. |

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.reader.MangaFitTest"` | `BUILD SUCCESSFUL`, 12 tests |
| `.\gradlew.bat :feature:reader-manga:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` — a wide scan rotated in portrait and landscape, a webtoon page taller than 20 000 px, and the start zoom after rotation/restore.

## M04 (remainder) — navigator, animation speed, orientation fallback, zone hint

**Status:** `CODE_VERIFIED` (V01/V18 device passes open).

| File | Change |
|---|---|
| `domain/.../reader/ReaderNavigator.kt` | NEW. `NavigatorSide`, `NavigatorSeek` (the thumb follows the finger while dragging; incoming progress cannot pull it back; release commits once; cancel keeps the reading place) and `ReaderNavigator.pageAt/fractionOf`. |
| `domain/src/test/.../ReaderNavigatorTest.kt` | NEW. 9 tests: idle thumb, old progress during a drag, commit on release, release without a drag, cancel, moves outside a drag, fractional↔page mapping, NaN. |
| `domain/.../model/ReaderSettings.kt` | `CommonReaderSettings.navigator` (off by default) and `animationSpeed` (1 = normal). |
| `feature/reader/.../VerticalNavigator.kt` | NEW. Optional vertical slider: local drag state, commit on release, haptic on commit, TalkBack progress semantics and `setProgress`. |
| `feature/reader/.../ReaderScreen.kt` | Shows the navigator on the chosen side while a book is open; passes the animation speed to manga; the "how to control" menu now forces the zone diagram even though the stored one-time hint says it was shown. |
| `feature/reader/.../ReaderTapZonesHint.kt` | `force` bypasses the one-time hint store on request. |
| `feature/reader/.../ReaderWindow.kt` | Orientation is not requested in split-screen/freeform windows; a configuration listener keeps that true when the window mode changes mid-read. |
| `feature/reader/.../ReaderSettingsSheet.kt` | Navigator side choice and animation-speed slider in Display. |
| `feature/reader-manga/.../MangaReaderView.kt` | Pager and webtoon animations use the speed (duration clamped to 60–1200 ms). |
| `feature/reader-epub/.../EpubController.kt`, `assets/yomu/reader.js` | Animation speed reaches the text reader; page turns are animated frame by frame with the requested duration, fade timings scale too. |
| `feature/reader/res/values{,-ru}/strings.xml` | Navigator, speed and position strings. |

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.reader.*"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :feature:reader:compileDebugKotlin :feature:reader-epub:compileDebugKotlin :feature:reader-manga:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` — navigator drag with slow pages, TalkBack setProgress, animation speed in text/manga, split-screen orientation.

---

## U01 (remainder) — per-title schedule, excluded shelves, persisted run

**Status:** `CODE_VERIFIED`.

| File | Change |
|---|---|
| `domain/.../catalog/UpdatePolicy.kt` | `UpdateSkip.EXCLUDED_TITLE`; `UpdateTitleState.intervalOverrideHours`/`excludedFromUpdates`; an explicit exclusion holds even for a forced run; a title override is used as written and is not stretched by the upload-rhythm prediction. |
| `domain/src/test/.../UpdatePolicyTest.kt` | 4 new tests: the override interval decides, an explicit interval is not stretched, an excluded title is never checked, plus the existing table. |
| `core/database/.../Entities.kt`, `Daos.kt`, `YomuDatabase.kt` | `catalog_items.updateIntervalHours` (nullable) and `updateExcluded` (default false); `MIGRATION_14_15`; schema version 15. |
| `core/database/schemas/.../15.json` | Exported. |
| `domain/.../model/Catalog.kt`, `data/.../Mappers.kt` | `CatalogEntry` carries the two fields; the mapper keeps them. |
| `data/.../catalog/CatalogRepository.kt` | Both policy call sites pass the fields; `setUpdateInterval`, `setUpdateExcluded`, `shelves()`. |
| `domain/.../catalog/UpdateRun.kt` | NEW. Persisted `UpdateRunReport`/`UpdateRunTitle` with counts, partial flag and duration. |
| `core/datastore/.../AppSettings.kt` | `update_last_run` JSON. |
| `data/.../work/UpdateRunStore.kt` | NEW. Decodes a tolerant report, records before the notification, can clear it. |
| `data/.../work/Workers.kt` | `UpdateWorker` records checked/skipped/new/failed/queued counts and per-title lines. |
| `data/.../di/DataModule.kt`, `feature/catalog/.../CatalogModule.kt`, `feature/settings/.../SettingsModule.kt` | Registration of the store and the new view-model dependency. |
| `feature/catalog/.../CatalogViewModels.kt`, `TitleScreen.kt` | The title menu opens "Update schedule": check automatically on/off and an interval (app default, 3/6/12/24/48/168 h). |
| `feature/settings/.../SettingsViewModel.kt`, `SettingsSections.kt` | Excluded-shelf picker with the user's shelves; an empty list says so. |
| `feature/catalog/res/values{,-ru}/strings.xml`, `feature/settings/res/values{,-ru}/strings.xml` | New strings. |
| `data/src/test/.../DatabaseMigrationTest.kt` | `CURRENT_VERSION = 15`; a 14 → 15 test proves the row survives, the interval is null and the exclusion false, and both can be written. |

## U02 (remainder) — shelf filter and an awaited manual check

**Status:** `CODE_VERIFIED`.

| File | Change |
|---|---|
| `domain/.../catalog/UpdatePlan.kt` | `PlannedCheck.shelfIds`. |
| `data/.../catalog/CatalogRepository.kt` | The plan carries each title's shelves. |
| `feature/catalog/.../UpcomingScreen.kt` | Shelf filter chips with an "N of M" indicator and a filtered empty state; the persisted last run (time, checked, new, failed, queued, first failures); "Check now" is disabled while the unique work is enqueued/running, and the screen reloads when it finishes (`getWorkInfosForUniqueWorkFlow`). |
| `feature/catalog/res/values{,-ru}/strings.xml` | Filter, run summary and reason strings. |

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.catalog.UpdatePolicyTest" --rerun-tasks` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :data:testDebugUnitTest --tests "app.yomu.data.db.DatabaseMigrationTest"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :feature:catalog:compileDebugKotlin :feature:settings:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` — a title excluded from checks stays untouched by the worker, the plan filter with real shelves, and a manual run that finishes while the screen is open.

---

## U03 (remainder) — grouped feeds, multi-select, new-chapters widget

**Status:** `CODE_VERIFIED` (widget rendering is covered by Robolectric; a launcher pass is `DEVICE_PENDING`).

| File | Change |
|---|---|
| `feature/catalog/.../CatalogScreen.kt` | The updates and history feeds are grouped by date or title (`HistoryView.group`) with a grouping chip row. Long press starts a selection; while selecting, a bar offers mark-read, download and (history only) remove; the selection is intersected with the visible rows on refresh and uses the chapter id as identity. |
| `feature/catalog/.../CatalogViewModels.kt` | `setChaptersRead`, `downloadChapters` (grouped by title) and a bulk `removeHistory`. |
| `core/database/.../Daos.kt` | `NewChapterRow` projection and `observeNewChapterRows(limit)`: new, unread chapters of favourites, newest first, a local query only. |
| `data/.../catalog/CatalogRepository.kt` | `observeNewChapters(limit)`. |
| `app/.../widget/NewChaptersWidget.kt` | NEW. Provider plus `NewChaptersWidgets`: collects the projection, hides incognito/locked, renders three rows with covers, opens the chapter on tap, an empty state without any network request, size-aware row count. |
| `app/src/main/res/layout/widget_new_chapters.xml`, `res/xml/widget_new_chapters_info.xml`, `AndroidManifest.xml` | NEW widget layout/info and receiver. |
| `app/.../AppModule.kt`, `YomuApp.kt` | Chapter-row intents open the chapter; the widget starts with the app process. |
| `app/res/values{,-ru}/strings.xml` | Widget label, description and empty state. |
| `feature/catalog/res/values{,-ru}/strings.xml` | Grouping, selection and action labels. |
| `app/src/test/.../NewChaptersWidgetTest.kt` | NEW. Robolectric: only new unread chapters are shown in newest-first order, a read chapter and a non-new one are excluded, incognito hides the widget, and clearing the new flags shows the empty state. |

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :feature:catalog:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :app:testDebugUnitTest --tests "app.yomu.NewChaptersWidgetTest"` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` — adding the widget to a launcher, resizing it, incognito/lock behaviour, and a tap opening the chapter after the process was killed.

---

## DL01 (remainder) — a server's Retry-After and source fairness

**Status:** `CODE_VERIFIED`.

| File | Change |
|---|---|
| `source-api/.../Models.kt` | `HttpStatusException.retryAfterMs`; `RetryAfter.parse` accepts delay-seconds or an HTTP date, rejects nonsense and caps the wait at 24 h. |
| `source-api/src/test/.../RetryAfterTest.kt` | NEW. 4 tests. |
| `source-api/.../HttpSource.kt`, `data/.../catalog/PageFetcher.kt`, `data/.../download/DownloadRepository.kt` | The header is read where the non-success response exists. |
| `data/.../download/DownloadPolicy.kt` | `retryAfterMs` and `waitBeforeRetryMs`: the server's advice wins when it is longer, never shortens the normal backoff. |
| `data/src/test/.../DownloadPolicyTest.kt` | A Retry-After inside a 429 is honoured; short advice does not shorten backoff; no advice uses the exponential wait. |
| `core/database/.../Entities.kt`, `Daos.kt`, `YomuDatabase.kt` | `downloads.retryAt`, schema 16/migration; queued queries only return a task whose time has come; `nextRetryAt`. |
| `data/.../download/DownloadRepository.kt` | A retry is persisted (`requeueAt`) instead of sleeping in a worker; the worker wakes for the next scheduled retry. `claimNext` first takes a task from a source with nothing running, so one source cannot hold every slot. |
| `data/src/test/.../ChapterDownloadQueueTest.kt` | NEW fairness test: a second source with one chapter runs before a six-chapter source finishes; `DownloadRepairTest` and the existing notification tests stay green. |
| `data/src/test/.../DatabaseMigrationTest.kt` | `CURRENT_VERSION = 16`; a 15 → 16 migration test. |

**Commands:** `:source-api:test --tests RetryAfterTest`, `:data:testDebugUnitTest --tests ChapterDownloadQueueTest --tests DownloadPolicyTest --tests DatabaseMigrationTest` → `BUILD SUCCESSFUL` (the fairness test failed once until the source-first claim was added; recorded, not hidden).

## DL02 (remainder) — preview, protected shelves, auto-download scope

**Status:** `CODE_VERIFIED`.

| File | Change |
|---|---|
| `data/.../download/RetentionPreview.kt` | NEW. Named chapters, bytes and the count per keep reason; nothing is deleted by a preview. |
| `data/.../download/DownloadRepository.kt` | `retentionPreview`, `applyRetention` (re-checks ownership/bookmark/protected shelf at apply time), `autoDownloadShelfAllows`, and `onChapterRead` now also performs download-ahead. |
| `data/.../catalog/CatalogRepository.kt` | `chaptersReadListeners` list instead of a single listener; retention and tracking both receive the event. |
| `data/.../di/DataModule.kt` | Both listeners are registered; retention runs off the caller's thread. |
| `core/datastore/.../AppSettings.kt` | `auto_download_shelves`. |
| `feature/settings/.../SettingsViewModel.kt`, `SettingsSections.kt` | Protected-shelf and auto-download-shelf pickers, a cleanup preview dialog (names, bytes, protected reasons), and a delete action. |
| `feature/settings/res/values{,-ru}/strings.xml` | Preview, picker and result strings. |
| `data/src/test/.../ChapterDownloadQueueTest.kt` | NEW: the preview names a file, a bookmark added after the preview prevents deletion, apply deletes after it is removed; auto-download honours its shelf and download-ahead queues the next two after reading. |

## DL03 (remainder) — index repair, unavailable SAF root, ComicInfo round-trip

**Status:** `CODE_VERIFIED` (a real SAF provider/card removal is `DEVICE_PENDING`).

| File | Change |
|---|---|
| `data/.../download/DownloadRepository.kt` | `repairIndex()` matches the stable `[chapterId]` in file/folder names: re-attaches a file the database lost, marks missing files not downloaded, corrects a moved path, reports orphans without deleting them. App-owned download directories now really get `.nomedia`. |
| `data/.../download/DownloadRepairReport.kt` | NEW. restored/missing/orphans. |
| `data/src/test/.../DownloadRepairTest.kt` | NEW. A file on disk is re-attached, a deleted one is marked missing with a null path, an orphan is reported and still exists. |
| `domain/.../library/ScanReport.kt` | NEW. Per-folder added/unavailable/reason; a missing root is "temporarily unavailable", never mass deletion. |
| `data/.../library/ImportService.kt` | `scanFolder` returns `FolderScan` and turns a revoked grant/unmounted card into `unavailable` with the reason. |
| `data/.../library/ScanReportStore.kt` | NEW. Persists the last scan. |
| `data/.../work/Workers.kt` | `LibraryScanWorker` records the report for every folder instead of only logging failures. |
| `feature/settings/.../SettingsViewModel.kt`, `SettingsSections.kt` | Library section shows the last scan and each unavailable folder; Downloads section has "check download index". |
| `core/datastore/.../AppSettings.kt` | `scan_last_report`. |
| `data/.../library/ComicInfo.kt` | NEW. One ComicInfo parser/writer for the download and import paths, unknown elements preserved, `<UploadDate>` carried. |
| `data/.../library/Metadata.kt`, `.../ImportService.kt` | The importer reads the shared format and stores the upload date. |
| `data/.../download/DownloadRepository.kt` | The CBZ writer uses the same `ComicInfoXml`. |
| `core/database/.../Entities.kt`, `YomuDatabase.kt`, schema 17 | `books.uploadDate`, migration 16 → 17. |
| `data/src/test/.../ComicInfoTest.kt`, `data/src/test/.../DatabaseMigrationTest.kt` | NEW round-trip tests (Unicode, HTML-escaped summary, pages, unknown fields, upload date, corrupt XML); 16 → 17 migration. |

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :source-api:test --tests "app.yomu.source.RetryAfterTest"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :data:testDebugUnitTest --tests "app.yomu.data.download.*" --tests "app.yomu.data.db.DatabaseMigrationTest" --tests "app.yomu.data.library.ComicInfoTest"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :feature:settings:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` — a revoked SAF root in the settings, a real card removal, and a launcher-level widget/notification pass. The index repair is covered by real files in Robolectric.

---

## Q04 (remainder) — the incognito contract, typed stages, notification privacy

**Status:** `CODE_VERIFIED`.

| File | Change |
|---|---|
| `domain/.../privacy/IncognitoPolicy.kt` | NEW. `PersistenceEvent` (auto progress, auto read mark, history, session stats, tracker update, user edit, titled notification) and `persists`/`notificationText`: the contract the entry points are audited against. A manual edit is never suppressed. |
| `domain/src/test/.../IncognitoPolicyTest.kt` | NEW. 4 tests: every automatic event is off in incognito, a user edit stays, outside incognito everything is kept, a notification loses its title. |
| `core/common/.../ErrorLog.kt` | `DiagStage` with a short code; a report always names its stage; the stage follows the current route family; `handled(stage, t)`. |
| `core/common/src/test/.../ErrorLogStageTest.kt` | NEW. A report carries `Stage: DOWNLOAD` and a `token=` value is masked before the file exists; a reader/library route selects its stage. |
| `core/common/build.gradle.kts` | Robolectric and the test core for the new test. |
| `data/.../work/NotificationRules.kt` | `NotificationPrefs.privacy` and `incognito`. |
| `core/datastore/.../AppSettings.kt` | `notify_privacy`. |
| `data/.../work/Workers.kt` | `notify` applies `VISIBILITY_SECRET` for privacy or incognito and replaces title/text with a generic one in incognito; prefs are watched for both switches. |
| `feature/settings/.../SettingsNotifications.kt` | "Hide content on the lock screen" switch. |
| `data/src/test/.../NotificationRulesTest.kt` | NEW test: privacy and incognito are separate switches and neither touches quiet hours. |
| `data/res/values{,-ru}/strings.xml`, `feature/settings/res/values{,-ru}/strings.xml` | Private notification text and the privacy switch. |

**A real defect found here:** two reports written within the same second could get the same file name and overwrite each other (the name used `System.nanoTime() % 1000`); the test for the stage exposed it and the journal now uses a monotonic sequence.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :domain:test --tests "app.yomu.domain.privacy.*"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :core:common:testDebugUnitTest --tests "app.yomu.core.common.ErrorLogStageTest"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :data:testDebugUnitTest --tests "app.yomu.data.work.NotificationRulesTest"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :feature:settings:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` — a real lock screen with privacy on, and notifications arriving while incognito is on.

---

## S06 (remainder) — DNS-over-HTTPS and the cookie/WebView scope

**Status:** `CODE_VERIFIED` for DoH and the clear-scope UI; the Shizuku installer is `BLOCKED` with its reason.

| File | Change |
|---|---|
| `core/network/.../ResolvingDns.kt` | NEW. `DohProvider` (system/Cloudflare/Google/AdGuard) and a `Dns` that reads the choice per lookup, sends literal/bare/private names to the system resolver, and falls back to the system answer when the endpoint fails. Built once per chosen provider. |
| `core/network/.../Network.kt` | The shared client uses the resolver; the DoH client has short timeouts and is built separately, so an answer cannot wait for the request timeout. |
| `core/network/.../CookieScope.kt` | NEW. `ClearScope` (one host / all sites) with a description used by the dialog, and a host/count projection that never keeps a cookie value. |
| `core/network/.../Network.kt` | `WebViewCookieJar.names(url)` (names only) and `clear(url)` for a single site. |
| `core/network/src/test/.../{ResolvingDnsTest,CookieScopeTest}.kt` | NEW. Public vs private names and literals, provider tolerance, grouping by host, no secret kept. |
| `core/datastore/.../AppSettings.kt`, `app/.../YomuApp.kt` | `dns_provider`; the client follows the setting without rebuilding. |
| `feature/settings/.../SettingsSections.kt`, `SettingsViewModel.kt` | The DNS choice in Catalog; a "clear site data" dialog with the scope chosen before the action, one-host or every site (also `WebStorage` and WebView form data), and the same scope passed to the clearer. |
| `feature/settings/res/values{,-ru}/strings.xml` | DNS and site-data strings. |

**Shizuku — blocked, not faked:** the public Shizuku 13.1.5 API exposes `pingBinder`/`checkSelfPermission` but no process or install call (`newProcess` is private); installing a system APK otherwise needs hidden `IPackageManager` stubs. The plan forbids a decorative setting, so no Shizuku switch is added and the private installer remains the only route. Recorded as `BLOCKED` with this reason.

**Commands:**

| Command | Result |
|---|---|
| `.\gradlew.bat :core:network:testDebugUnitTest --tests "app.yomu.core.network.*"` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat :feature:settings:compileDebugKotlin :app:compileDebugKotlin` | `BUILD SUCCESSFUL` |

**Device/live:** `DEVICE_PENDING` — a real LAN server while DoH is on, and a WebView site cleared by host vs all sites.



