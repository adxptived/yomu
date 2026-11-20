# Component owners (Q01)

One owner for every cross-cutting decision. A feature may read another owner's state, but it must not create a second
instance of the same responsibility.

| Responsibility | Owner | Lives in | What it must not do |
|---|---|---|---|
| Input (tap zones, keys, swipes) | `ReaderInput`, `TapZoneLayout`, `KeyBus` | `domain/reader`, `core:ui` | know about a page engine or a ViewModel |
| Reading session / chapter window | `ReaderWindow`, `ReaderWindowPages`, `ReaderViewModel`, `SessionPageSource`, `WindowHtmlDocument` | `domain/reader`, `feature:reader*` | keep more than the current ±1 chapter, or reload on an ordinary boundary |
| Progress and anchor | `ReaderViewModel` (`flushProgress`, `onPageChanged`), `CatalogRepository.saveProgress`, `BookRepositoryImpl.saveProgress` | `feature:reader`, `data` | let a page engine write its own row |
| Text selection | `ReaderSelection`, `EvaluationPanel` actions | `feature:reader`, `feature:annotations` | change the document |
| Page actions | `PageActionExecutor` / `PageContext` capabilities (`PageCapabilitiesOf`) | `domain/reader`, `feature:reader` | offer an action the engine cannot run |
| Library query | `LibraryQuerySpec`, `LibraryQuerySpecData`, `SavedQueryShelf` | `domain/library` | be reimplemented in the screen or the SQL builder |
| Library selection and bulk work | `LibrarySelection`, `LibraryBulk`, `BulkExecutor` | `domain/library`, `feature:library` | mutate the selection from a list diff |
| Library open resolution | `LibraryOpenResolver` | `domain/library` | decide from a UI click twice |
| Source connections and capabilities | `SourceManager`, `SourceCalls`, `SourceConnectionProbe`, `SourceHealthTracker` | `data/source` | leak a source's client into a feature |
| Update policy | `UpdatePolicy`, `WorkScheduler`, `UpdateWorker` | `domain/catalog`, `data/work` | schedule a second worker for the same title |
| Downloads | `DownloadRepository`, `DownloadWorker`, `AudiobookDownloader` | `data/download`, `data/audio` | write a file outside the staging/cache directory |
| Backup / restore | `BackupManager`, `BackupOptions`, `MigrationJournal` | `data/backup`, `data/catalog` | mix a foreign library without an explicit replace or remap |
| Sync | `SyncManager`, `SyncMerger`, `SyncCodec`, `SyncBase` | `data/backup` | let an unknown category look like a deletion |
| Audio commands | `AudioPlaybackService` (single `ForwardingPlayer`) | `feature:audio` | let the screen, notification, headset and widget compute seeks separately |
| Long operations | `TaskRegistry` / `LongTask` (planned contract) | `domain/task` | report "done" after a cancel |

## Boundaries and tests

- **Domain** is pure JVM: no Android, no Room, no Compose. Tests run without Robolectric.
- **Data** owns Android/IO: Room, SAF, WorkManager, network, PDFium. Its tests use Robolectric or MockWebServer.
- **Feature** owns Compose and ViewModels: state, events, preview and accessibility.
- **Koin** has one binding per owner; `Release*Test` walks the graph and catches duplicate bindings.
- Cancellation tests exist for the chapter open mutex (`ReaderViewModel.openChapter`), sync writes (`SyncWriteTest`,
  `ProgressAtomicityTest`) and downloads (`ChapterDownloadQueueTest`). Rotation and disposal are covered by the
  Robolectric reader/PDF and library screen tests; the new audio widget is not a ViewModel and has no state to lose.

## Still open

- Extract `ReaderInputRouter`, `ReaderSession` and `PageActionExecutor` from `ReaderViewModel` where the file still
  mixes the three; the chapter window (M03) is already a separate owner.
- A `SourceConnection/CapabilityResolver` facade over `SourceManager` for features.
- The long-operation contract (`LongTask`: progress, cancel, partial errors, output, recovery journal) for the editor
  and import; see [REMAINING.md](REMAINING.md) N05.
