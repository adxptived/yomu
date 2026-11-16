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

## Progress summary

**Ten packages are `CODE_VERIFIED`.** Every one has an automated test that would fail if the behaviour regressed, and
the full tree builds: `assembleDebug` + `testDebugUnitTest` + `:domain:test` + `:source-api:test` + `:sources:opds:test`
all succeed on the current HEAD.

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
| S02 | A capability contract that does not break old extensions and a probe that names its failure cause — contract only, see its scope note. |

### Honest status of the remaining work

- **Device gates (`DEVICE_PENDING`):** V01, V02, V03, V04, V05, V06, V08, V18. This environment has no Android device
  or emulator, so native PDFium gestures, TalkBack, E-ink, real audio tags and real accounts are not verified. Every
  one of them is listed per package above rather than being counted as done.
- **Packages not started:** L06, S01, S03, N01, S05, S06, M01–M05, U01–U03, DL01–DL03, A01–A03, MG01, B01–B03, T01–T02,
  S04, N02–N05, Q01–Q05. The two packages marked "contract only" (L05, S02) delivered their model and tests but their
  UI wiring is prerequisite work for later packages, not a finished feature.
- **P3 items** (M05 renderer, S06, A03, N03–N04) are unfinished by design in this pass and remain open.

No package in this journal claims a device or live-account acceptance it did not perform.

### Final verification (this pass)

| Command | Result |
|---|---|
| `.\gradlew.bat assembleDebug testDebugUnitTest :domain:test :source-api:test :sources:opds:test --console=plain` | `BUILD SUCCESSFUL` |

Baseline at B00 and this run differ only in the changes listed per package. The working tree contains the three original
plan documents (untracked before this pass), this journal, and the source and test edits described above. No commit,
push or release was made.



