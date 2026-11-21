# Что осталось доделать

Срез: `main`, релиз `v2.10.0`. Аудит 17 ноября закрыт по коду; проход удобства чтения и источников — [REPORT_2026-11-21_READER_UX.md](REPORT_2026-11-21_READER_UX.md). Здесь только открытые пункты; шаги, файлы, приёмка и
стоп-условия — в [PLAN_2026-11-18.md](PLAN_2026-11-18.md); отчёты проходов —
[REPORT_2026-11-18.md](REPORT_2026-11-18.md) и [REPORT_2026-11-19.md](REPORT_2026-11-19.md) (B03/N04/N05/Q01);
аудит прохода — [AUDIT_2026-11-20.md](AUDIT_2026-11-20.md); паритет с Mihon —
[MIHON_PARITY.md](MIHON_PARITY.md); контракты T01 — [TRACKER_CONTRACTS.md](TRACKER_CONTRACTS.md);
отчёт паритетного прохода — [REPORT_2026-11-20.md](REPORT_2026-11-20.md).

Проверено в проходе 19 ноября (без устройства): `:domain:test`, `:data:testDebugUnitTest`, тесты всех затронутых
feature-модулей, `:app:testDebugUnitTest` — 543 теста зелёные; `:app:assembleFast` собирает `arm64-v8a` и `x86_64`.
`detekt` — только унаследованные style-замечания. Ни один пункт §1 этим не закрывается.

## 1. Аппаратные и live-гейты

Не закрываются кодом и тестами; нужен телефон/планшет, реальные аккаунты или сервер.

- [ ] **Установка 2.9.4 поверх 2.9.1** с библиотекой, скачанными главами и настройками; проверить миграцию и чтение.
- [ ] **Установка fast/debug поверх прежней совместимой APK** без data clear; release signing не подменять debug key.
- [ ] **WebView-сессии:** очистка одного сайта с cookie на `/` и вложенном пути; второй сайт остаётся в системе.
- [ ] **Приватность:** шторка и lock screen для аудио в incognito и при «скрывать на экране блокировки».
- [ ] **Манга (M03a):** свайп, аппаратные клавиши, слайдер и номер страницы после single/split/спред, RTL, переходы глав,
  память при трёх главах, ошибка сети/пустая глава.
- [ ] **Онлайн-новеллы (M03b):** поворот, process recreation, глава без текста, re-center без Loading.
- [ ] **E-ink:** вспышка при быстром листании, TalkBack, реальный ghosting.
- [ ] **PDFium/TalkBack:** PDF-жесты, меню страницы, выделение, доступность, crop preview на реальном PDF.
- [ ] **Аудио (A01/A02/A03):** focus/звонок, Bluetooth/гарнитура, speed на границе главы; 200/206, истёкший URL, смена
  ETag, cancel/no space; metadata/gain на реальных файлах; виджет после reboot; linked editions.
- [ ] **Android Auto (A03d):** head unit и protocol-тесты `MediaLibraryService` (browse tree, auth, offline). Сейчас
  сервис — `MediaSessionService`, superclass без этих тестов не меняется.
- [ ] **S03:** живая RSS-лента (код есть, проверена только mock/fixture).
- [ ] **S04/T02:** реальный сервер Audiobookshelf/Komga/Kavita/Suwayomi.
- [ ] **Производительность Q03:** замеры на устройстве, fast/release, наборы 1000 книг/10000 online/500+ PDF; методика —
  в [PERFORMANCE.md](PERFORMANCE.md).
- [ ] **Q02 visual:** TalkBack-путь, ширины 320/360/411/tablet, fontScale 1/1.3/2, light/dark/AMOLED, просмотр
  screenshot (не только запись).
- [ ] **B01/B02:** restore поверх живой библиотеки, реальные WebDAV/облака, два устройства с подписками и настройками.

## 2. Открытый код

### Волна 3 — данные

- [ ] **B03 · мастер sync** (`LIVE_PENDING`) — сделано по коду: `SyncWizard` state machine
  (backend→input→probe→categories→schedule→preview→ready) с гейтом `canAdvance`, `SyncFailure`/recovery-виды,
  классификация ошибки `classifySyncFailure`, единый `SyncStatusSummary` в настройках. Мастер проведён по всем
  шагам (`SyncWizardUi.kt`: выбор backend, ввод реквизитов, реальная проверка соединения на PROBE, категории,
  расписание, preview с подтверждением, публикация настроек только после Finish), recovery-баннер и карточка
  показывают auth/network/storage/battery-совет и действие (retry vs open setup). Тесты: `SyncWizardTest`
  (canAdvance, классификация). Добавлено: редактирование любого поля реквизитов или папки через `TextPref.onChanged`
  сбрасывает `probePassed`/`previewConfirmed`, поэтому проверку нельзя пропустить после смены данных; тест
  `SyncWizardInputResetTest`. Осталось: прогнать live (auth/network/storage/battery на реальном backend).

### Волна 4 — внешнее (live-гейты)

- [ ] **T01 · новый трекер** (`LIVE_PENDING`) — service contract по официальной документации, один сервис,
  capabilities, pending queue, mock-тесты, затем live auth/search/bind/update/logout. Без аккаунта live не закрыт.
- [ ] **S04 · Audiobookshelf** (`LIVE_PENDING`) — контракт, stable server/library/item/track IDs, secure credentials,
  browse→manifest→playback, двусторонний progress, offline через A02; нужен тестовый сервер.
- [ ] **T02 · серверный tracking** (`LIVE_PENDING`) — отдельные контракты Komga/Kavita/Suwayomi, mapping
  item/chapter/page, revisions/loop prevention, live на реальном сервере.

### Волна 5 — язык и редактор

- [ ] **N02 остаток** (`IN_PROGRESS`) — сделано: реестр `ReaderOptions` по engine/mode, TTS voice sample, PDF crop
  preview. Осталось: fontScale 1/1.3/2 и process-restore тесты (device/visual). Scope и общие настройки закрыты.
- [ ] **N04 · слова/контекст** (`IN_PROGRESS`) — сделано по коду: переход из слова к book+locator, фильтр слов по
  книге, retry перевода. Добавлено: глоссарий `GlossaryEntry`/`GlossaryIndex` с identity по нормализованным именам в
  книге, merge/replace и export/import, Category `GLOSSARY` в backup (пересчёт ID), вкладка «Glossary» в словах;
  opt-in fulltext-индекс (FTS4) `FulltextIndexRepository`/`IndexPlan` на базе `LongTask`: estimate/rebuild/cancel/
  incremental, поиск честно помечен heading до построения индекса, удаление вместе с книгой, настройка по умолчанию
  OFF. Тесты: `GlossaryIndexTest`, `IndexPlanTest`, `GlossaryRepositoryTest`, `FulltextIndexRepositoryTest`,
  `GlossaryBackupTest`, миграция 18→19. Добавлено: глоссарий в field-complete sync (`SyncGlossaryEntry`, hash-identity,
  newest-wins, tombstones, remap bookId, неизвестные категории сохраняются), opt-in `syncGlossary` (по умолчанию OFF)
  и переключатель в настройках/мастере; `FulltextIndexRepository.indexText()` — публичный API для индексации текста
  книги. Тесты `GlossarySyncTest`, `SyncIdentityMigrationTest`, `FulltextIndexRepositoryTest`. Осталось: подключить
  вызов `indexText()` из ридера (пока индексируются сохранённые highlights/notes/contexts + OCR), device/kill проверка.
- [ ] **N05 · длинные операции** (`IN_PROGRESS`) — сделано по коду: `LongTask`/`TaskRegistry` (progress/cancel/
  partial errors/output, cancelled ≠ done), merge/split глав (`EpubWorkspaceTest`). Добавлено: `RecoveryJournal`/
  `RecoveryPlan` + `publishPlan` guard с персистом в черновике, `CleaningLayer` (staged brush с undo/redo без записи в
  оригинал), merge/split томов в `EpubWorkspace`, staged→validate→atomic publish в `saveToBook` с отменой и очисткой
  temp-файлов; тесты `RecoveryJournalTest`, `CleaningLayerTest`, volume-тесты в `EpubWorkspaceTest`, UI-кнопки и
  strings RU/EN. Добавлено: двухфазный publish (`SavePublisher`, `TaskRegistry.beginPublish`, `publishing` flag) —
  отмена до захвата записи прерывает операцию, после захвата запись живёт в `appScope` и не может быть перемаркирована
  в cancelled; терминальный статус, journal и уборка temp-файлов в одном `NonCancellable`. Тесты `SavePublisherTest`,
  `LongTaskTest`. Осталось: device/kill-mid-task проверка.

### Волна 6 — качество

- [ ] **M05 · экспериментальный рендерер** (`TODO`) — только после Q03 baseline и M03: конкретный bottleneck,
  opt-in prototype за интерфейсом, обычный fallback, сравнение качества/latency; нет выигрыша — не включать.
- [ ] **Q01 · границы компонентов** (`IN_PROGRESS`, код закрыт) — реестр owners в [COMPONENTS.md](COMPONENTS.md).
  `ReaderInputRouter`, `ChapterNavigator`, `PageActionExecutor`; `ReaderViewModel` делегирует им. Добавлено:
  `ChapterWindowState` + `SavedStateHandle` — окно глав сохраняется/восстанавливается после process death (deep link
  имеет приоритет); `SourceConnectionResolver` — фасад source/capability без новых Koin-биндингов; тесты
  `ChapterWindowStateTest`, `SourceConnectionResolverTest`. `ReaderModule` имеет fallback на пустой `SavedStateHandle`
  для plain-Koin тестов. Осталось: device-проверка process restoration и визуальное сравнение с Mihon (у Mihon свой
  vertical chapter navigator — [MIHON_PARITY.md](MIHON_PARITY.md)).
- [ ] **Q02 · a11y/visual** (`IN_PROGRESS`) — сделано: 48 dp кнопки виджета, semantics у crop preview, подписи
  полок. Осталось: TalkBack-путь, ширины/fontScale и просмотр screenshot (device).
- [ ] **Q03 · perf/memory** (`IN_PROGRESS`) — методика, fixtures, метрики и шаблон отчёта в
  [PERFORMANCE.md](PERFORMANCE.md). Осталось: прогнать baseline на устройстве; цифры без замера не заявляются.
- [ ] **Q05 · документация/матрица** (`IN_PROGRESS`) — README/USER_GUIDE/KNOWN_LIMITATIONS/ARCHITECTURE обновлены по
  фактическому HEAD; добавлены [REPORT_2026-11-18.md](REPORT_2026-11-18.md), [COMPONENTS.md](COMPONENTS.md),
  [PERFORMANCE.md](PERFORMANCE.md), паритет с Mihon — [MIHON_PARITY.md](MIHON_PARITY.md), контракты T01 —
  [TRACKER_CONTRACTS.md](TRACKER_CONTRACTS.md). По паритету закрыто кодом: формат времени 12/24, skip-version,
  Wi-Fi-предзагрузка, вертикальный навигатор, resumable загрузки изображений, логический поиск библиотеки. Осталось:
  установка поверх прежней APK без data clear (device), live-трекеры T01 и финальный release-отчёт.

### Открытые хвосты

- [ ] **S03** (`LIVE_PENDING`) — только живая RSS-лента; код и mock-тесты есть.
- [ ] **S06 Shizuku** (`BLOCKED`) — в публичном Shizuku API нет install-вызова; декоративную настройку не добавлять.

## 3. Закрыто по коду — не переделывать

Device/live-проверки этих пунктов — в §1; здесь только код и тесты.

- **M03a · манга** — `ReaderWindow`, `SessionPageSource`, chapter-aware pager с разделителями, bounded-окно
  текущей ±1 без `UiState.Loading`, anchor/progress по видимой главе, повтор у границы.
- **M03b · онлайн-новеллы** — `WindowHtmlDocument` текущей ±1 с мостом section↔chapter ID, offset-anchor в памяти,
  прогресс по видимой главе, re-center без Loading.
- **A02 · офлайн-объём** — ALL/NEXT_N/SELECTED, оценка с unknown, свободное место, состояние по трекам, частичная
  доступность, выборочное удаление без потери stream reference/позиции; Range/ETag и атомарная запись были.
- **A01 · управление** — rewind `OFF/ADAPTIVE/CUSTOM` с миграцией, один `ForwardingPlayer` для
  экрана/уведомления/гарнитуры/Bluetooth, `CMD_SKIP`/`CMD_REWIND`, журнал перемотки.
- **A03a–c/e · rich audio** — metadata overrides, ReplayGain без clipping (opt-in, peak headroom, кеш), виджет,
  ручная связь text↔audio. Только A03d остался (§1).
- **MG01 · миграция источника** — `planMigration`/`applyMigration`, confidence/ambiguous/missing, категории,
  ревизии, транзакция + reversible journal, batch, идемпотентность, UI-preview. Тесты: `MigrationMatcherTest`,
  `CatalogMigrationTest`.
- **B01 · backup/restore** — v2 manifest категорий, preview-счётчики, merge vs replace, remap зависимых ID, версия
  архива, лимиты, rollback. Тесты: `BackupCategoriesTest`, `SafeBackupRestoreTest`, `BackupCompatibilityTest`.
- **B02 · field-complete sync** — matrix в `ARCHITECTURE.md`, настройки/рейтинг/избранное по `metaUpdatedAt`,
  audio subscriptions с tombstone `s|`, неизвестные top-level категории (`SyncCodec`), идемпотентный merge.
  Тесты в `SyncMergerTest`.
- **L05 · умные полки** — `LibraryQuerySpec` сохраняется и восстанавливается; `SavedQueryShelf` применяется,
  сохраняется и удаляется; тест в `SavedQueryShelfTest`.
- **B03 (код)** — state machine, `canAdvance`, классификация/recovery-виды, `SyncStatusSummary` и пошаговый UI
  (`SyncWizardUi`) с реальным connection probe и публикацией после подтверждённого preview; live — §1/§2.
- **N02 (часть)** — `ReaderOptions`, TTS sample, PDF crop preview; fontScale/process — §2.
- **N04 (код)** — контекст слова и фильтр по книге, глоссарий с identity/merge/backup, opt-in FTS-индекс; sync/raw-page
  извлечение и device — §2/§1.
- **N05 (код)** — `LongTask`/`TaskRegistry`, `RecoveryJournal`/`publishPlan`, reversible `CleaningLayer`, merge/split
  томов, staged validate→atomic publish; device kill-mid-task — §2.
- **Q01 (код)** — owners registry, `ReaderInputRouter`/`ChapterNavigator`/`PageActionExecutor` с тестами
  cancellation/disposal; source facade — §2. **Q02/Q03/Q05 (части)** — точечная a11y, методика perf, документация;
  остальное — §2/§1.

## 4. Порядок

1. **Живые гейты §1** — по мере появления устройства/аккаунтов/сервера; они не блокируют код ниже.
2. **Код B03/N04/N05/Q01 закрыт и проверен** (19 ноября, [REPORT_2026-11-19.md](REPORT_2026-11-19.md)); в §2 остались
   только live/device-хвосты и уточнения: sync глоссария, raw-page extraction индекса, source facade в Q01.
3. **N02** — fontScale/process-restore (device/visual); **Q02/Q03** — device-проверки; **Q05** — итоговый release-отчёт.
4. **M05** — только после Q03 baseline.
5. **S03/S04/T01/T02** — только при живой среде; **S06** остаётся `BLOCKED`.

## 5. Уже закрыто — не переделывать

Аудит 17 ноября (2.9.4): S06-очистка, DL03-repair, M02/M04-раскладка, U01/U02-окно, N02-scope, Q04-incognito,
CSS глав. Пакеты: R01–R03, D01/D02, L01–L04/L06, S01/S02/S05, M01/M02/M04, U01–U03, DL01–DL03, N01, N03, Q04.
Robolectric-стабильность закрыта: 4.18.1, CI Java 17/21, 2.9.4 опубликован.

## 6. Вне плана

- Убрать упоминания стороннего продукта из кода/строк/документации — отдельная задача с проверкой совместимости
  импорта; юридические NOTICE не трогать без проверки лицензии.
