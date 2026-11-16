# Yomu: инструкция выполнения плана для AI-агента

Статус: **задания для будущей реализации; код приложения по этому документу пока не менялся**.

Основа: Yomu `0120bbc4a67baa494953d2ba33f579b5e8fac9b4`, версия приложения 2.8.0, схема Room 11. Эти значения подтверждены в исходниках 16 ноября 2026. Перед исполнением проверить их снова.

Этот файл — **точка входа для исполнителя**. [Продуктовый план](DEVELOPMENT_PLAN_2026-11-16.md) объясняет цели и содержит приёмку V01–V20. [Матрица Mihon](MIHON_PARITY_2026-11-16.md) содержит MP001–MP128 и источники сравнения. При расхождении технического порядка между документами использовать порядок этого файла. Новые указания пользователя имеют приоритет.

## 1. Договор исполнения

1. Выполнять задачи последовательно по разделу 7. Не пытаться реализовать все 47 задач одним большим изменением. Перейти к следующей после проверки текущей; отдельно записывать недоступную аппаратную/сетевую приёмку.
2. До правки прочитать указанный символ целиком, непосредственных вызывающих и ближайшие тесты. Существующий файл из реестра искать по точному пути. Если путь отсутствует в новом HEAD, найти замену через `rg --files` и `rg -n`; зафиксировать соответствие, не создавать одноимённый дубль.
3. Метки `EXISTING`, `NEW`, `HYPOTHESIS`, `EXTERNAL` имеют буквальный смысл. Названия NEW не являются уже доступными API. HYPOTHESIS сначала воспроизводится. EXTERNAL требует проверенного контракта провайдера.
4. Не считать номер строки контрактом: искать имя класса/функции. Номера из аудита относятся только к указанному commit.
5. Переиспользовать существующие Koin, Room, DataStore, Coroutines/Flow, Compose, Media3 и систему ресурсов. Не переводить проект на Hilt, другую навигацию, другой database engine или JS framework.
6. Не создавать вторые RadialMenu, download queue, quick panel, backup manager, source registry и собственные несогласованные модели прогресса. У расширения существующего механизма должна быть одна точка владения состоянием.
7. UI не вызывает SQL/HTTP непосредственно. Чистая логика не импортирует Compose `Offset`, Android Context, Room entity или bitmap. Единицы координат и идентификаторов отражать в контракте.
8. Никакого «починил» по одному успешному compile. Для поведения нужны assertions/действия; для native PDF, SAF, MediaSession и жестов — соответствующая Android-проверка.
9. Не ослаблять тесты ради зелёного отчёта: не удалять assertions, не добавлять безусловный skip, не увеличивать timeout без анализа причины, не переснимать goldens вместо разбора незапланированного отличия.
10. Не менять source IDs, namespace ID книг/глав, applicationId, ключ подписи или формат существующих backup без отдельной совместимой миграции.
11. Не удалять пользовательские изменения. Не выполнять hard reset, clean пользовательских данных, принудительное обновление зависимостей, commit/push/release без соответствующего задания пользователя. Не копировать лицензированный код с удалением notices.
12. Если проверка невозможна, писать конкретно: команда, ошибка среды, что проверено другим способом, что осталось. Недоступный телефон/аккаунт не является ни успехом, ни основанием объявить функцию отсутствующей.

### Статусы и продолжение работы

Для каждой задачи вести запись в **новом при начале исполнения** `docs/execution/2026-11-16/STATUS.md`. В этом проходе этот журнал не создаётся, чтобы план не выглядел начатой реализацией.

Статусы: `TODO`, `IN_PROGRESS`, `CODE_VERIFIED`, `ACCEPTED`, `BLOCKED`. `CODE_VERIFIED` означает, что код и доступные автоматические проверки завершены, но обязательная приёмка ещё нет. `ACCEPTED` ставить только после всех обязательных проверок задачи. `BLOCKED` относится к конкретной задаче, а не автоматически ко всему плану.

Запись задачи содержит: ID, текущий HEAD, статус, изменённые файлы, реальные команды и результаты, найденный baseline failure, screenshot/device evidence, оставшуюся проверку, следующий шаг. После паузы сначала прочитать журнал и diff, затем продолжить с последнего незавершённого шага. Не выполнять уже принятые задачи заново.

Общая остановка нужна при угрозе потери данных/непонятной смене scope. Отсутствие ключей конкретного трекера блокирует только live-проверку этого трекера: продолжить независимые задачи, сохранить незакрытую проверку. Не заменять сервис фиктивной успешной реализацией.

Для перехода к зависимому пакету достаточно `CODE_VERIFIED`, если недостающая device/live-проверка не проверяет его обязательное техническое предусловие. Если без неё нельзя установить корректность identity, сохранность данных или работоспособность используемого API, зависимый путь не выполнять. Поздняя интеграционная приёмка не превращает предшествующие pending-проверки в успешные автоматически.

## 2. Обязательный старт: B00

Это подготовка исполнения, она не входит в 47 продуктовых задач.

1. Прочитать актуальные AGENTS.md, этот документ, разделы 1–4 и 14–15 продуктового плана, `settings.gradle.kts`, соответствующие build.gradle.kts и `docs/VISUAL_TESTS.md`.
2. Выполнить `rtk git status --short`, `rtk git rev-parse HEAD`, `rtk git log -5 --oneline`. Записать исходные изменения. Если HEAD другой, повторно проверить факты F01–F12 ниже.
3. Проверить JDK и SDK: `rtk proxy java -version`, `rtk proxy .\gradlew.bat --version`. Проверить наличие SDK/config локально, но не печатать содержимое файлов с секретами. Не менять версии инструментов автоматически.
4. Проверить существование файлов реестра. Отсутствующие тестовые классы, помеченные NEW, создавать только в своей задаче. Не запускать `--tests` на ещё не созданном классе и не трактовать «No tests found» как прохождение.
5. Запустить baseline: `rtk proxy .\gradlew.bat :app:assembleDebug`. Затем только существующие профильные тесты для ближайшей задачи. Полный baseline CI-набора нужен перед крупной волной, не перед каждой мелкой правкой.
6. При доступном устройстве записать версию APK, настройки textTapZones/tapToTurn/pageMenuStyle/selectionMenuStyle, ориентацию, ширину окна, наличие системных inset и конкретный PDF. Снять исходные состояния библиотеки и меню. При отсутствии устройства записать `DEVICE_PENDING`.
7. Создать STATUS.md и сохранить список baseline failures отдельно от новых. Существующий сбой не игнорировать; если он затрагивает проверяемое поведение, сначала разобраться с причиной.

**Выход B00:** известны исходный HEAD, модификации, инструменты, доступные проверки и ближайший исполняемый пакет. APK не переустанавливать с очисткой данных ради подготовки.

## 3. Проверенные факты и границы знания

| ID | Класс факта | Что известно | Где перепроверить |
|---|---|---|---|
| F01 | EXISTING | DI — Koin, feature convention подключает Koin | BUILD-FEATURE, APP-DI, DATA-DI |
| F02 | EXISTING | Room version=11, destructive fallback не включён | DB, DB-DAOS, DB-ENTITIES, DB-MIG-TEST |
| F03 | EXISTING | `PdfListener.onTap` получает px, `TapZones.action` ждёт доли | PDF-VIEW → READER-VM.onTap → MANGA-CTRL.TapZones |
| F04 | EXISTING | PDF long press выделяет текст; callback меню страницы отсутствует | PDF-VIEW.PdfListener/PdfPage |
| F05 | EXISTING | `pageFile()` возвращает файл только для Manga | READER-VM.pageFile/runPageAction |
| F06 | EXISTING | `QuickActionMenu(radial=true)` уже использует `RadialMenu` | QUICK-MENU, WHEEL |
| F07 | EXISTING | Description уже выводится при непустом `Book.description` | BOOK-DETAILS, BOOK-MODEL |
| F08 | EXISTING | `AudioMeta.extract` не возвращает description | AUDIO-META |
| F09 | EXISTING | Локальный selection — Set<Long>; online скрывается при selecting | LIB-VM, LIB-SCREEN.onlineTiles, CATALOG-HOME.OnlineLibraryPane |
| F10 | EXISTING | Источники AUDIO, RSS import, LibriVox/YouTube уже есть | SOURCE-MODELS, SOURCE-MANAGER, AUDIO-STORE, RSS |
| F11 | EXISTING | Online chapter switch заменяет контент через Loading | READER-VM.openChapter/openOnline |
| F12 | EXISTING | Quick settings/preview/presets/undo уже существуют | READER-QUICK, READER-SETTINGS |
| H01 | HYPOTHESIS | Root/window anchor может смещать selection wheel на отдельных конфигурациях | ANNOTATION-MENU → QUICK-MENU; проверить на Android |
| H02 | UNKNOWN | В конкретном аудиофайле со скриншота могло вообще не быть описания | Исходный файл/теги и запись BookEntity; без файла причину не утверждать |
| H03 | UNKNOWN | Все API, OAuth и личные серверы работают с настоящими аккаунтами | Только реальное подключение; MockWebServer недостаточен |
| H04 | UNKNOWN | Есть полное совпадение поведения с Mihon | Матрица — инвентаризация; ручной остаточный проход обязателен |

Сравнение Mihon зафиксировано в companion matrix. Ветка `main` и стабильная версия — разные ориентиры. Не искать произвольную текущую реализацию upstream и не подменять ею согласованную спецификацию Yomu.

## 4. Реестр существующих файлов

Каждый алиас ниже указывает **один точный существующий путь от корня репозитория**. Они проверены при детализации плана. Алиасы используются в пакетах для компактности, это не имена Gradle-модулей.

### Приложение, DI и данные

| Алиас | Путь |
|---|---|
| NAV | `app/src/main/kotlin/app/yomu/navigation/YomuNavigation.kt` |
| ROUTES | `app/src/main/kotlin/app/yomu/navigation/Routes.kt` |
| SHELL | `app/src/main/kotlin/app/yomu/navigation/NavShell.kt` |
| TABS-HOST | `app/src/main/kotlin/app/yomu/navigation/TabsHost.kt` |
| APP-DI | `app/src/main/kotlin/app/yomu/AppModule.kt` |
| DATA-DI | `data/src/main/kotlin/app/yomu/data/di/DataModule.kt` |
| ONBOARDING | `app/src/main/kotlin/app/yomu/ui/Onboarding.kt` |
| WIDGET | `app/src/main/kotlin/app/yomu/widget/ContinueReadingWidget.kt` |
| BUILD-FEATURE | `build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt` |
| BUILD-COMMON | `build-logic/convention/src/main/kotlin/Common.kt` |
| DB | `core/database/src/main/kotlin/app/yomu/core/database/YomuDatabase.kt` |
| DB-DAOS | `core/database/src/main/kotlin/app/yomu/core/database/Daos.kt` |
| DB-ENTITIES | `core/database/src/main/kotlin/app/yomu/core/database/Entities.kt` |
| SETTINGS | `core/datastore/src/main/kotlin/app/yomu/core/datastore/AppSettings.kt` |
| NETWORK | `core/network/src/main/kotlin/app/yomu/core/network/Network.kt` |
| ERROR-LOG | `core/common/src/main/kotlin/app/yomu/core/common/ErrorLog.kt` |
| BOOKS-REPO | `data/src/main/kotlin/app/yomu/data/repository/BookRepositoryImpl.kt` |
| MAPPERS | `data/src/main/kotlin/app/yomu/data/repository/Mappers.kt` |
| IMPORT | `data/src/main/kotlin/app/yomu/data/library/ImportService.kt` |
| METADATA | `data/src/main/kotlin/app/yomu/data/library/Metadata.kt` |
| LIB-TABS | `data/src/main/kotlin/app/yomu/data/library/LibraryTabs.kt` |
| WORKERS | `data/src/main/kotlin/app/yomu/data/work/Workers.kt` |
| BOOK-MODEL | `domain/src/main/kotlin/app/yomu/domain/model/Book.kt` |
| CATALOG-MODEL | `domain/src/main/kotlin/app/yomu/domain/model/Catalog.kt` |
| READER-MODEL | `domain/src/main/kotlin/app/yomu/domain/model/ReaderSettings.kt` |
| QUICK-MODEL | `domain/src/main/kotlin/app/yomu/domain/model/QuickActions.kt` |
| AUDIO-MODEL | `domain/src/main/kotlin/app/yomu/domain/model/Audio.kt` |
| TRACK-MODEL | `domain/src/main/kotlin/app/yomu/domain/tracking/Tracking.kt` |

### UI, библиотека и читалки

| Алиас | Путь |
|---|---|
| LIB-SCREEN | `feature/library/src/main/kotlin/app/yomu/feature/library/LibraryScreen.kt` |
| LIB-VM | `feature/library/src/main/kotlin/app/yomu/feature/library/LibraryViewModel.kt` |
| BOOK-DETAILS | `feature/library/src/main/kotlin/app/yomu/feature/library/BookDetailsScreen.kt` |
| BOOK-DETAILS-VM | `feature/library/src/main/kotlin/app/yomu/feature/library/BookDetailsViewModel.kt` |
| WHEEL | `core/ui/src/main/kotlin/app/yomu/core/ui/components/RadialWheel.kt` |
| QUICK-MENU | `core/ui/src/main/kotlin/app/yomu/core/ui/components/QuickMenu.kt` |
| COVERS | `core/ui/src/main/kotlin/app/yomu/core/ui/components/CoverCache.kt` |
| ANNOTATION-MENU | `feature/annotations/src/main/kotlin/app/yomu/feature/annotations/QuickMenus.kt` |
| ANNOTATION-BAR | `feature/annotations/src/main/kotlin/app/yomu/feature/annotations/SelectionToolbar.kt` |
| READER-SCREEN | `feature/reader/src/main/kotlin/app/yomu/feature/reader/ReaderScreen.kt` |
| READER-VM | `feature/reader/src/main/kotlin/app/yomu/feature/reader/ReaderViewModel.kt` |
| READER-QUICK | `feature/reader/src/main/kotlin/app/yomu/feature/reader/ReaderQuickPanel.kt` |
| READER-SETTINGS | `feature/reader/src/main/kotlin/app/yomu/feature/reader/ReaderSettingsSheet.kt` |
| READER-HINT | `feature/reader/src/main/kotlin/app/yomu/feature/reader/ReaderTapZonesHint.kt` |
| PDF-VIEW | `feature/reader-pdf/src/main/kotlin/app/yomu/feature/reader/pdf/PdfReaderView.kt` |
| PDF-CTRL | `feature/reader-pdf/src/main/kotlin/app/yomu/feature/reader/pdf/PdfController.kt` |
| MANGA-VIEW | `feature/reader-manga/src/main/kotlin/app/yomu/feature/reader/manga/MangaReaderView.kt` |
| MANGA-CTRL | `feature/reader-manga/src/main/kotlin/app/yomu/feature/reader/manga/MangaController.kt` |
| MANGA-SETTINGS | `feature/reader-manga/src/main/kotlin/app/yomu/feature/reader/manga/MangaSettingsPanel.kt` |
| EPUB-CTRL | `feature/reader-epub/src/main/kotlin/app/yomu/feature/reader/epub/EpubController.kt` |

### Каталог, источники и настройки

| Алиас | Путь |
|---|---|
| CATALOG-HOME | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/CatalogScreen.kt` |
| CATALOG-VMS | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/CatalogViewModels.kt` |
| SOURCE-BROWSE | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/SourceBrowseScreen.kt` |
| GLOBAL-SEARCH | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/GlobalSearchScreen.kt` |
| TITLE | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/TitleScreen.kt` |
| EXT-SCREEN | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/ExtensionsScreen.kt` |
| EXT-VM | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/ExtensionsViewModel.kt` |
| CATALOG-REPO | `data/src/main/kotlin/app/yomu/data/catalog/CatalogRepository.kt` |
| SOURCE-MANAGER | `data/src/main/kotlin/app/yomu/data/source/SourceManager.kt` |
| SOURCE-CALLS | `data/src/main/kotlin/app/yomu/data/source/SourceCalls.kt` |
| EXT-MANAGER | `data/src/main/kotlin/app/yomu/data/source/ExtensionManager.kt` |
| EXT-STORES | `data/src/main/kotlin/app/yomu/data/source/ExtensionStores.kt` |
| SOURCE-API | `source-api/src/main/kotlin/app/yomu/source/Source.kt` |
| SOURCE-MODELS | `source-api/src/main/kotlin/app/yomu/source/Models.kt` |
| OPDS | `sources/opds/src/main/kotlin/app/yomu/sources/opds/OpdsSource.kt` |
| OPDS-PARSER | `sources/opds/src/main/kotlin/app/yomu/sources/opds/OpdsParser.kt` |
| LIBRIVOX | `sources/opds/src/main/kotlin/app/yomu/sources/opds/LibriVoxSource.kt` |
| CHAPTER-ORDER | `domain/src/main/kotlin/app/yomu/domain/catalog/ChapterOrder.kt` |
| CHAPTER-FILTER | `domain/src/main/kotlin/app/yomu/domain/catalog/ChapterDisplay.kt` |
| ONLINE-FILTER | `domain/src/main/kotlin/app/yomu/domain/catalog/LibraryDisplay.kt` |
| SETTINGS-UI | `feature/settings/src/main/kotlin/app/yomu/feature/settings/SettingsScreen.kt` |
| SETTINGS-V2 | `feature/settings/src/main/kotlin/app/yomu/feature/settings/SettingsV2.kt` |
| SETTINGS-VM | `feature/settings/src/main/kotlin/app/yomu/feature/settings/SettingsViewModel.kt` |
| SETTINGS-SEARCH | `feature/settings/src/main/kotlin/app/yomu/feature/settings/SettingsSearch.kt` |
| SYNC-UI | `feature/settings/src/main/kotlin/app/yomu/feature/settings/SyncProblemsSection.kt` |
| APP-UPDATE-UI | `feature/settings/src/main/kotlin/app/yomu/feature/settings/Updates.kt` |

### Аудио, загрузки, перенос и редактор

| Алиас | Путь |
|---|---|
| AUDIO-META | `data/src/main/kotlin/app/yomu/data/audio/AudioMeta.kt` |
| AUDIO-STORE | `data/src/main/kotlin/app/yomu/data/audio/AudiobookStore.kt` |
| RSS | `data/src/main/kotlin/app/yomu/data/audio/PodcastFeed.kt` |
| YOUTUBE-SOURCE | `data/src/main/kotlin/app/yomu/data/audio/YouTubeSource.kt` |
| AUDIO-DOWNLOAD | `data/src/main/kotlin/app/yomu/data/audio/AudiobookDownloader.kt` |
| AUDIO-SERVICE | `feature/audio/src/main/kotlin/app/yomu/feature/audio/AudioPlaybackService.kt` |
| AUDIO-CTRL | `feature/audio/src/main/kotlin/app/yomu/feature/audio/AudioController.kt` |
| AUDIO-SCREEN | `feature/audio/src/main/kotlin/app/yomu/feature/audio/AudioPlayerScreen.kt` |
| AUDIO-VM | `feature/audio/src/main/kotlin/app/yomu/feature/audio/AudioPlayerViewModel.kt` |
| AUDIO-SETTINGS | `feature/audio/src/main/kotlin/app/yomu/feature/audio/AudioSettings.kt` |
| AUDIO-HISTORY | `feature/audio/src/main/kotlin/app/yomu/feature/audio/AudioHistory.kt` |
| PLAYER-MATH | `feature/audio/src/main/kotlin/app/yomu/feature/audio/PlayerMath.kt` |
| DOWNLOAD-REPO | `data/src/main/kotlin/app/yomu/data/download/DownloadRepository.kt` |
| DOWNLOAD-UI | `feature/downloads/src/main/kotlin/app/yomu/feature/downloads/Downloads.kt` |
| TRACK-MANAGER | `data/src/main/kotlin/app/yomu/data/tracking/TrackerManager.kt` |
| BACKUP | `data/src/main/kotlin/app/yomu/data/backup/BackupManager.kt` |
| MIHON-IMPORT | `data/src/main/kotlin/app/yomu/data/backup/MihonImporter.kt` |
| SYNC-MANAGER | `data/src/main/kotlin/app/yomu/data/backup/SyncManager.kt` |
| SYNC-MODELS | `data/src/main/kotlin/app/yomu/data/backup/SyncModels.kt` |
| SYNC-MERGER | `data/src/main/kotlin/app/yomu/data/backup/SyncMerger.kt` |
| TRACK-SYNC | `data/src/main/kotlin/app/yomu/data/backup/TrackLinkSync.kt` |
| EPUB-EDITOR | `feature/editor/src/main/kotlin/app/yomu/feature/editor/EpubWorkspace.kt` |
| MANGA-EDITOR | `feature/editor/src/main/kotlin/app/yomu/feature/editor/MangaWorkspace.kt` |

### Существующие опорные тесты

| Алиас | Точный путь |
|---|---|
| DB-MIG-TEST | `data/src/test/kotlin/app/yomu/data/db/DatabaseMigrationTest.kt` |
| NAV-TEST | `app/src/test/kotlin/app/yomu/NavigationUxScreensTest.kt` |
| CATALOG-UI-TEST | `app/src/test/kotlin/app/yomu/CatalogScreensTest.kt` |
| PERSIST-TEST | `app/src/test/kotlin/app/yomu/ReadingDataPersistenceTest.kt` |
| BOOK-PANE-TEST | `app/src/test/kotlin/app/yomu/BookPaneScreensTest.kt` |
| QUEUE-TEST | `data/src/test/kotlin/app/yomu/data/download/ChapterDownloadQueueTest.kt` |
| DL-POLICY-TEST | `data/src/test/kotlin/app/yomu/data/download/DownloadPolicyTest.kt` |
| AUDIO-PARSER-TEST | `data/src/test/kotlin/app/yomu/data/audio/AudioParsersTest.kt` |
| AUDIO-RESUME-TEST | `data/src/test/kotlin/app/yomu/data/audio/AudiobookResumeRulesTest.kt` |
| CHAPTER-TEST | `domain/src/test/kotlin/app/yomu/domain/catalog/ChapterOrderTest.kt` |
| FILTER-TEST | `domain/src/test/kotlin/app/yomu/domain/catalog/LibraryDisplayTest.kt` |
| WHEEL-TEST | `core/ui/src/test/kotlin/app/yomu/core/ui/components/RadialSectorTest.kt` |
| TRACKER-TEST | `data/src/test/kotlin/app/yomu/data/tracking/TrackersTest.kt` |
| BACKUP-SAFETY-TEST | `data/src/test/kotlin/app/yomu/data/backup/SafeBackupRestoreTest.kt` |
| RESTORE-TEST | `data/src/test/kotlin/app/yomu/data/backup/MihonImporterTest.kt` |
| MERGE-TEST | `data/src/test/kotlin/app/yomu/data/backup/SyncMergerTest.kt` |
| QUICK-PANEL-TEST | `feature/reader/src/test/kotlin/app/yomu/feature/reader/ReaderQuickPanelTest.kt` |

Для неуказанного файла вспомогательного кода использовать `rtk proxy rg --files <точный-модуль>/src`, затем `rtk proxy rg -n '<Symbol>' <точный-модуль>/src`. Не угадывать файл по имени класса: например, DAOs уже собраны в DB-DAOS, а несколько ViewModel — в CATALOG-VMS.

Для UI-строк использовать существующие пары файлов: `feature/library/src/main/res/values/strings.xml` и `feature/library/src/main/res/values-ru/strings.xml`; такие же пары в `feature/catalog`, `feature/reader`, `feature/audio` и `core/ui`. Общие строки принадлежат `core/ui`, специфичные — своему feature. Если требуется ресурс другого модуля, сначала найти его фактический путь, не импортировать автоматически `app.yomu.R` во все features.

## 5. Новые контракты и фиксированные решения

### 5.1. Предлагаемые новые файлы

Ни один файл в таблице не объявляется существующим. Перед созданием проверить, не появилась ли эквивалентная реализация в актуальном HEAD. Если появилась, расширить её и записать mapping в STATUS.md. Точные публичные сигнатуры согласовать с реальными вызывающими, сохранив описанную семантику.

| NEW-алиас | Планируемый путь | Владелец и назначение |
|---|---|---|
| INPUT | `domain/src/main/kotlin/app/yomu/domain/reader/ReaderInput.kt` | R01: нормализованная позиция, действия и чистая логика зон |
| INPUT-TEST | `domain/src/test/kotlin/app/yomu/domain/reader/ReaderInputTest.kt` | R01: регрессия px/fractions и границы |
| PAGE-CONTEXT | `domain/src/main/kotlin/app/yomu/domain/reader/PageActionContext.kt` | R02: тип engine, стабильная identity страницы и capabilities |
| PAGE-EXECUTOR | `feature/reader/src/main/kotlin/app/yomu/feature/reader/PageActionExecutor.kt` | R02: Android-экспорт/OCR через адаптеры движков |
| OPEN-RESOLVER | `domain/src/main/kotlin/app/yomu/domain/library/LibraryOpenResolver.kt` | L02: решение details/resume без NavController |
| SELECTION | `domain/src/main/kotlin/app/yomu/domain/library/LibrarySelection.kt` | L03: типизированный ключ и чистые переходы выбора |
| BULK-EXECUTOR | `data/src/main/kotlin/app/yomu/data/library/LibraryBulkExecutor.kt` | L04: координация применимости и результата по элементам |
| LIB-QUERY | `domain/src/main/kotlin/app/yomu/domain/library/LibraryQuerySpec.kt` | L05: общие фильтры/availability/sort |
| LIB-PROJECTION | `data/src/main/kotlin/app/yomu/data/library/LibraryProjection.kt` | L06: смешанная страничная выдача и ключи |
| SOURCE-CAPS | `source-api/src/main/kotlin/app/yomu/source/SourceCapabilities.kt` | S02: совместимое расширение capabilities |
| SOURCE-WIZARD | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/SourceConnectionScreen.kt` | S01: мастер подключения |
| SOURCE-WIZARD-VM | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/SourceConnectionViewModel.kt` | S01: состояние и проверка подключения |
| SUBSCRIPTIONS | `data/src/main/kotlin/app/yomu/data/audio/AudioSubscriptionRepository.kt` | S03: persisted RSS subscription и reconciliation |
| SESSION-WINDOW | `domain/src/main/kotlin/app/yomu/domain/reader/ReaderSessionWindow.kt` | M03: identity/окно нескольких глав, без bitmap/Context |
| UPDATE-POLICY | `domain/src/main/kotlin/app/yomu/domain/catalog/UpdatePolicy.kt` | U01: чистое решение проверить/пропустить/когда |
| UPCOMING | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/UpcomingScreen.kt` | U02: список/календарь проверок |
| MIGRATION-PLAN | `domain/src/main/kotlin/app/yomu/domain/catalog/SourceMigrationPlan.kt` | MG01: соответствия, ambiguity и выбранные категории переноса |
| MIGRATION-UI | `feature/catalog/src/main/kotlin/app/yomu/feature/catalog/SourceMigrationScreen.kt` | MG01: preview и подтверждение |

Новые тесты, кроме INPUT-TEST, создавать рядом с проверяемой логикой в соответствующем `src/test/kotlin` с тем же package и суффиксом `Test`. В отчёте указывать фактически созданный полный путь. Для Android-native тестов использовать раздел 6, а не имитировать PDFium на desktop JVM.

### 5.2. Обязательные продуктовые значения

- Обычная библиотечная плитка открывает карточку; Resume открывает сохранённое место. На новой установке long press включает выбор. Если пользователь явно назначал wheel, сохранить его предпочтение и добавить «Выбрать» первым действием. В режиме выбора тап/long press работают только с выбором.
- Заголовок Library: название, поиск, `⋮`. Импорт — одна кнопка «Добавить». Входы в выбор/загрузки/управление полками доступны из `⋮`; активная очередь дополнительно видна строкой состояния.
- Единый sheet «Отображение»: Фильтры / Сортировка / Вид. Чипы отражают фильтры, а не создают вторую несогласованную панель настроек.
- Description скрыт при `null`, пустой строке или HTML без текста. По умолчанию 5 строк; явная кнопка «Развернуть» / «Свернуть». Сохранённая ручная правка имеет приоритет над обновлением источника.
- Не более 8 секторов wheel, включая «Ещё». При избытке: первые 7 включённых в пользовательском порядке + «Ещё»; оставшееся — доступный список. Геометрия и haptic общие, цвет смыслового выделения можно сохранить.
- Зона по умолчанию для text/PDF — LEFT_RIGHT с sideFraction=0.25. Не принуждать центральную зону альтернативных Kindle/L-shape схем к другому поведению: показывать пользователю фактическую схему.
- Мультивыбор local/online использует разные типы ключей. Несколько source browse results до сохранения имеют ключ `(sourceId, item.url)` и отдельный тип, не поддельный DB ID.
- «Выбрать всё» фиксирует множество результатов текущего запроса на момент команды. Новые записи, пришедшие позже, не выбираются сами. Смена сортировки сохраняет множество, сбрасывает range anchor. Смена фильтра/полки сохраняет пересечение с новым результатом, сообщает новый счётчик.
- «Все результаты» означает и невидимые страницы Paging. При выборе диапазона порядок фиксируется снимком текущей сортировки, не порядком завершения загрузки обложек.
- Downloaded-only означает доступность всего нужного контента офлайн; для частичного есть отдельный фильтр/индикатор. Локальный доступный файл всегда офлайн. Недоступный SAF URI не является офлайном.
- Статус публикации сайта и личный статус чтения — разные данные. Избранное, личный рейтинг и tag пользователя тоже не должны изменять metadata провайдера.
- Наличие будущей главы — прогноз проверки, а не обещанная дата выхода. Ручное «Проверить сейчас» обходит прогноз, но не rate limit, отсутствие сети и авторизацию.
- Incognito: автоматические history/session/statistics/remote tracking и автоматические persistent progress/read updates не записываются; внутри текущей сессии позиция работает. Явные ручные изменения пользователя сохраняются. Секреты и текст книги в диагностике не записываются независимо от incognito. Проверить миграцию существующего поведения; не стирать уже сохранённые данные при включении режима.

### 5.3. Семантика координат и действий

| Данные | Единицы / область | Разрешённое использование |
|---|---|---|
| Input position | Доли 0…1 от viewport читалки | Tap zone resolution |
| Menu anchor | Window px | Popup размещение/hit test |
| PDF hit point | PDF page coordinates | Символ, ссылка, annotation |
| Scroll anchor | chapter/page identity + внутренний offset | Сохранение/восстановление места |

Преобразование input: root point → local point viewport → доли. Размер страницы, Activity и viewport — разные величины. Для width/height≤0, NaN/Infinity и касания вне viewport навигацию игнорировать. Clamp пикселя 540 в 0…1 не является нормализацией.

Приоритет обычного PDF tap: ссылка/annotation → закрытие существующего выделения → navigation zone. После pan/pinch/selection drag не выполнять synthetic navigation tap. Long press текстового символа запускает выделение; long press свободной области/скана — меню страницы; асинхронный hit test после смены страницы не открывает меню старой страницы.

## 6. Проверки: команды и доказательства

Все команды ниже — **инструкция**, а не журнал успешных запусков. На Windows запускать из корня репозитория. На Linux применять тот же Gradle task с `./gradlew`; RTK использовать, если он есть в среде, его отсутствие не менять в продукте. Список tasks проверять при изменившейся конфигурации.

| Профиль | Команда |
|---|---|
| C-DOMAIN | `rtk proxy .\gradlew.bat :domain:test` |
| C-READER | `rtk proxy .\gradlew.bat :core:ui:testDebugUnitTest :feature:reader:testDebugUnitTest :feature:reader-pdf:testDebugUnitTest :feature:reader-manga:testDebugUnitTest :app:compileDebugKotlin` |
| C-LIBRARY | `rtk proxy .\gradlew.bat :domain:test :feature:library:testDebugUnitTest :feature:catalog:testDebugUnitTest :data:testDebugUnitTest :app:testDebugUnitTest` |
| C-SOURCE | `rtk proxy .\gradlew.bat :source-api:test :sources:opds:test :feature:catalog:testDebugUnitTest :data:testDebugUnitTest :app:compileDebugKotlin` |
| C-AUDIO | `rtk proxy .\gradlew.bat :feature:audio:testDebugUnitTest :data:testDebugUnitTest :app:compileDebugKotlin` |
| C-DATA | `rtk proxy .\gradlew.bat :domain:test :data:testDebugUnitTest :app:testDebugUnitTest` |
| C-UI | `rtk proxy .\gradlew.bat :core:ui:testDebugUnitTest :app:testDebugUnitTest` |
| C-EDITOR | `rtk proxy .\gradlew.bat :feature:editor:testDebugUnitTest :data:testDebugUnitTest :app:compileDebugKotlin` |
| C-RELEASE | `rtk proxy .\gradlew.bat assembleDebug testDebugUnitTest :domain:test :source-api:test :sources:opds:test :sources:mangadex:test :sources:novels:test lintDebug detekt assembleFast` |

Во время разработки сначала ограничить запуск конкретным существующим/созданным классом через `--tests <package.Class>`. В конце пакета/волны выполнять весь затронутый профиль, не прогонять весь C-RELEASE после каждой строки. `No tests found`, skipped tests, запись PNG без сравнения и compile не равны тестовому прохождению.

Примеры уже существующих классов: `app.yomu.data.db.DatabaseMigrationTest`, `app.yomu.NavigationUxScreensTest`, `app.yomu.CatalogScreensTest`, `app.yomu.data.download.ChapterDownloadQueueTest`, `app.yomu.core.ui.components.RadialSectorTest`.

**Схема БД:** экспорт `core/database/schemas/app.yomu.core.database.YomuDatabase/<новая-версия>.json`; добавлять Migration в `YomuDatabase.MIGRATIONS` и тесты в DB-MIG-TEST. Базовая версия 11 не означает, что все пакеты должны независимо создавать MIGRATION_11_12: номер определяется текущим состоянием после предыдущего пакета. Старые schema JSON не переписывать.

**Android-native:** BUILD-COMMON уже объявляет `androidx.test.runner.AndroidJUnitRunner`. Наличие декларации runner не означает наличие готового androidTest набора. Перед первым native-тестом проверить зависимости `androidTestImplementation`, добавить недостающие через version catalog и создать новый `app/src/androidTest/kotlin/app/yomu/reader/PdfInteractionTest.kt`. Android fixtures размещать в новом `app/src/androidTest/assets/pdf/`. Затем: `rtk proxy .\gradlew.bat :app:connectedDebugAndroidTest`. Без подключённого устройства этот профиль остаётся pending; не заменять его пустым заглушечным тестом.

**Fixtures:** существующие `data/src/test/resources/pdf/{classic,rotated,predictor,objstm,foreign}.pdf` предназначены для parser/writer. Не считать их автоматически подходящими для pointer/menu приёмки. Для неё создать известный текстовый PDF со ссылкой/аннотацией и PDF-скан без text layer; добавить происхождение fixture и ожидаемые page/text/link counts. Не использовать пользовательскую книгу как общедоступный тестовый asset без разрешения.

**UI:** поведенческие assertions в app-тестах с текущими Compose/Robolectric паттернами, очисткой Koin в teardown и без реальной сети. Goldens записывать и проверять по `docs/VISUAL_TESTS.md`; утверждённые baseline — с Linux CI. Просмотреть screenshot, а не только наличие файла.

**Внешние интеграции:** fake/mock доказывает контракт клиента с fixture, live — контракт с провайдером. Для каждого сервиса хранить оба статуса. Не включать live-тесты в обычный offline CI и не печатать реальные credentials.

## 7. Канонический порядок и зависимости

Ниже линейный порядок, допустимый для одного агента. Dependencies в пакетах уточняют минимальные условия. Сквозные Q02/Q04 и локальное выделение ответственности Q01 выполняются внутри каждой задачи; их финальные пакеты закрывают общий контроль, а не впервые добавляют доступность/безопасность.

| Шаг | Пакеты |
|---|---|
| Подготовка | B00 |
| Исправления читалки | R01 → R02 → R03 → D01 |
| Библиотека | L02 → L03 → L04 → L01 → L05 → D02 → L06 |
| Источники | S02 → S01 → S03 → N01 → S05 → S06 |
| Чтение глав | M01 → M02 → M04 → M03 |
| Обновления и офлайн | U01 → U02 → U03 → DL01 → DL02 → DL03 → A02 |
| Перенос и сервисы | MG01 → B01 → T01 → S04 → T02 → B02 → B03 |
| Комфорт и качество | A01 → A03 → N02 → N03 → N04 → N05 → M05 → Q01 → Q02 → Q03 → Q04 → Q05 |

P3 не означает разрешение забыть задачу: если полный объём остаётся заданием пользователя, строка остаётся открытой до исполнения/обоснованной блокировки или явного исключения пользователем. EXTERNAL может породить несколько последовательных сервисных подпакетов с собственными evidence.

## 8. Исполняемые пакеты: PDF, меню и метаданные

### R01 — PDF input и нормализация

**Зависит:** B00. **Код:** PDF-VIEW, READER-VM.onTap, MANGA-CTRL.TapZones, MANGA-VIEW, READER-HINT; NEW INPUT, INPUT-TEST. **Проверки:** C-DOMAIN, C-READER, V01.

1. Выписать все реализации/вызовы `onTap` в PDF/Text/Manga; указать единицы на каждой границе. EPUB может уже передавать доли — не делить их повторно.
2. Написать INPUT-TEST с таблицей: viewport=1080×1920; точки (0,960)→PREV, (269,960)→PREV, (270,960)→MENU, (540,960)→MENU, (810,960)→MENU, (811,960)→NEXT, (1080,960)→NEXT при LEFT_RIGHT/0.25. Добавить origin viewport=(80,120): root=(620,1080) даёт тот же центр. Нулевой размер/NaN/outside игнорируются.
3. Извлечь чистый TapZones resolver в INPUT, сохранить все текущие layouts/RTL/inverted semantics; обновить manga-вызовы и preview без смены defaults. Старая feature-функция допустима только как временный делегирующий адаптер.
4. Измерять viewport PDF сверху над PAGED/SCROLL/DUAL/reflow; преобразовывать page/root point через координаты layout в viewport fractions. Hit test PDF по прежнему использует page coordinates.
5. Проверить приоритет ссылки/annotation/selection и отмену tap после pan/pinch. Не лечить ошибку выключением tapToTurn или удалением центральной зоны.
6. Запустить тесты и compile; Android fixture — центр/края до/после crop/zoom/rotation/reflow/dual. Сохранить фактический результат V01.

**Выход:** один документированный coordinate contract, без повторной нормализации. **Стоп:** реальный callback уже изменён в новом HEAD — сначала перепроверить воспроизведение; не применять старый патч механически.

### R02 — PDF page menu и действия

**Зависит:** R01. **Код:** PDF-VIEW.PdfListener/PdfPage, PDF-CTRL, READER-VM.PageMenuRequest/runPageAction/pageFile, READER-SCREEN, QUICK-MODEL; NEW PAGE-CONTEXT, PAGE-EXECUTOR. **Проверки:** C-READER, V02.

1. Добавить явное событие long press страницы с page identity/window anchor; текстовый hit запускает выделение, miss — page menu. Gesture generation token отменяет устаревший async hit test при смене страницы/закрытии reader.
2. Контекст хранит engine, book/catalog identity, physical page identity и доступные действия; не только голый integer page. Не возвращать PDF как фиктивный Manga.
3. Разделить получение изображения страницы: Manga — существующий file, PDF — render страницы в cache с pixel budget; перед экспортом проверить актуальность контекста и наличие файла. Ошибка рендера даёт сообщение, а не тихий `return`.
4. Подключить SAVE/SHARE/SET_COVER/BOOKMARK/NOTE и COPY_TEXT/TRANSLATE по capabilities: PDF text layer использовать при наличии, скан — OCR. Не показывать неработающую команду. Сохранение картинки не меняет оригинальный PDF.
5. Добавить поведенческие тесты menu request и dispatch на правильный engine/page; native проверка stationary long press/drag text/scan/annotation, до и после rotation.
6. Проверить 10 повторных render/OCR без накопления bitmap/job/temp-файлов; share file существует до чтения получателем и очищается по определённой cache policy.

**Выход:** текст выделяется, скан имеет menu, каждый видимый action выполняется. **Стоп:** отсутствует безопасный API рендера или native fixture — исследовать adapter; не выдавать null-return за поддержку PDF.

### R03 — общий wheel

**Зависит:** R02. **Код:** WHEEL, QUICK-MENU, ANNOTATION-MENU, ANNOTATION-BAR, LIB-SCREEN, CATALOG-HOME, AUDIO-SCREEN, READER-SCREEN, SETTINGS; WHEEL-TEST. **Проверки:** C-UI, C-READER, V03.

1. Через `rg` собрать все radial/quick-menu call sites и записать context, gesture, anchor space, style setting. Проверить H01, не объявлять root/window offset дефектом без измерения.
2. Оставить один renderer; общий max sectors/More, tokens и destructive semantics. Быстрые меню не создают собственные popup-геометрии.
3. Явно переводить selection root rect в window anchor; один вычисленный clamped center обслуживает рисование и hit test.
4. Договор жеста: release в секторе выполняет один action, release в центре оставляет меню, Back/outside закрывают, открывающее удержание не нажимает пункт второй раз. Для tap-only вызова вид тот же, gesture state иной.
5. Сохранить semantic tint highlight; для TalkBack дать линейный список с теми же enabled actions. Явно описать OFF/BAR/LIST/RADIAL по контекстам и сохранить старые настройки.
6. Тестировать 1/7/8/9/20 действий, края/углы, window inset, fontScale 2, повторное открытие и cancel. Native-выбор и UI screenshots сохранить отдельно.

**Выход:** единый стиль без регрессии drag/tap. **Стоп:** предлагается второй wheel или незаметное удаление действий ради размера — изменить архитектуру More, а не scope.

### D01 — описание и metadata pipeline

**Зависит:** B00; порядок после R03 рекомендован. **Код:** BOOK-DETAILS, BOOK-DETAILS-VM, BOOK-MODEL, METADATA, MAPPERS, IMPORT, AUDIO-META, AUDIO-STORE, RSS, OPDS-PARSER, DOWNLOAD-REPO, DB при необходимости. **Проверки:** C-DATA, C-SOURCE, V08.

1. Таблица pipeline для EPUB, PDF, OPDS download, audio file/folder, RSS, LibriVox/YouTube: вход description → parser DTO → insert/update → Book. Зафиксировать, в каких ветках поле отсутствует.
2. Добавить fixtures непустого description, HTML-only empty, null и ручного override. Проверить сохранение после readback/restart/backup.
3. Исправить потерю при импорте/скачивании; стандартный AudioMeta не наделять несуществующим `METADATA_KEY_DESCRIPTION`. Нужные ID3/MP4/Vorbis поля читать через реально поддерживаемый parser; ограничить чтение/размер тегов.
4. Если происхождение/override требует storage, добавить совместимые nullable поля или отдельную metadata-связь с migration. Политика: manual override > непустое source metadata > непустое extracted metadata; пустое refresh не стирает непустое поле. Пустое ручное удаление должно быть отличимо от «не редактировал».
5. Переиспользовать текущий description block, дать 5 строк и явное разворачивание; сохранять абзацы после safe HTML conversion. Не делать сетевое обогащение при каждом открытии карточки.

**Выход:** имеющееся описание доходит до карточки, пустое скрыто. **Стоп:** нет исходного файла пользователя — H02 оставить unknown, работу по fixtures продолжить.

## 9. Исполняемые пакеты: библиотека

### L02 — маршрут открытия

**Зависит:** B00. **Код:** NAV, ROUTES, LIB-SCREEN, LIB-VM, CATALOG-HOME, CATALOG-VMS, TITLE, AUDIO-STORE; NEW OPEN-RESOLVER. **Проверки:** C-DOMAIN, C-LIBRARY, V04; NAV-TEST.

1. Составить таблицу current callbacks для local tile/list, online tile, continue shelf, history, widget, tablet pane, source result. Отделить библиотечный тап от выбора конкретной главы и каталожной навигационной папки.
2. Resolver принимает typed identity, intent Details/Resume/Chapter, available saved position и доступность. Возвращает решение, а не запускает Android navigation. Не начинать аудио при Details.
3. Local/online плитки библиотеки направить в Details; явный Resume — в правильный ReaderRoute/Audio path через существующую маршрутизацию. Не создавать AudioRoute, пока не проверен текущий способ обработки AUDIO.
4. Сохранить поведение конкретной главы/оглавления; блокировать двойной navigation event до завершения перехода. При missing file/source показывать recovery.
5. Тест: все engine types, отсутствие прогресса, completed, missing URI, double tap, tablet, Back с прежней полкой/прокруткой.

**Выход:** единая таблица intent → route и тесты. **Стоп:** route недоступен/двойной владелец playback — сначала разобрать existing audio navigation, не добавлять второй service.

### L03 — единое выделение

**Зависит:** L02. **Код:** LIB-VM, LIB-SCREEN, CATALOG-HOME, CATALOG-VMS, NAV (создание `OnlineLibrary`), BOOKS-REPO, CATALOG-REPO, DB-DAOS; NEW SELECTION. **Проверки:** C-DOMAIN, C-LIBRARY, V05–V06.

1. Создать typed LocalBook/OnlineEntry keys и immutable selection state: mode, selected keys, range anchor, query snapshot identity. Поле `TabPick.online: Long?` расширить для нескольких online IDs.
2. Выделение local+online принадлежит одному library controller. Прокинуть его в OnlineLibraryPane; убрать условие скрытия onlineTiles при selecting. TitleViewModel selection глав остаётся отдельным контекстом и не смешивается с выбором книг.
3. Реализовать переходы из §5.2: enter/toggle/all/clear/invert/range/query changed/sort changed. Snapshot all получать из repository query, а не `itemSnapshotList`.
4. Поддержать source-result selection отдельным identity типом до upsert. Long press перестаёт незаметно toggle favorite; bulk-add станет явной командой L04.
5. Back закрывает выбор раньше navigation; временный state переживает rotation через подходящий state owner. После process death восстановить ключи и проверить их существование, не сохранять огромный набор в Bundle без лимита.
6. Тесты: local7+online7; 200 результатов/30 loaded; новый результат после SelectAll не выбран; filter intersection; range после sort; удаление выбранного извне.

**Выход:** единое доступное выделение во всех библиотечных видах. **Стоп:** selected key преобразуется через знак/магическое смещение Long — исправить типизацию, не создавать ID collision scheme.

### L04 — массовые операции

**Зависит:** L03. **Код:** LIB-VM, LIB-SCREEN, CATALOG-HOME, CATALOG-VMS, BOOKS-REPO, CATALOG-REPO, LIB-TABS, DB-ENTITIES, DB-DAOS, DB, MAPPERS, SOURCE-BROWSE; NEW BULK-EXECUTOR. **Проверки:** C-LIBRARY, DB-MIG-TEST, V07.

1. Создать capability matrix для local/online/source-result: shelves, personal status, favorite, rating, tags, download, remove library, delete managed files. Результат команды — success/failed/skipped по каждому ключу.
2. Для online personal status/rating/tags добавить отдельные user данные, если их нет; source publication status оставить неизменным. Nullable legacy значения мигрируются в прежний смысл без переписывания всего каталога.
3. Реализовать общие metadata/shelves изменения в одной транзакции; network/file jobs координировать с отдельными partial results, не держать DB transaction во время HTTP.
4. UI показывает число применимых/выбранных до запуска; 0 применимых — disabled с объяснением. Не использовать код локальных bulk-actions для online IDs напрямую.
5. Remove shelf, unfavorite/remove library, delete downloads и delete owned source file — разные commands. Undo восстанавливает links/metadata; необратимое удаление оригинала требует отдельного явно выбранного действия.
6. Source browse bulk-add делает upsert каждого выбранного remote key, сообщает дубли и ошибки, не открывает каждую карточку последовательно.
7. Тесты: mixed8/12, failure one of many, cancel, Undo после ухода с экрана, read-only SAF, duplicate bulk add, repeated command/idempotency.

**Выход:** действия local/Online согласованы и не затрагивают чужие ID/файлы. **Стоп:** неизвестно ownership файла — не удалять; вернуть конкретный unsupported result.

### L01 — компактный header и отображение

**Зависит:** L04. **Код:** LIB-SCREEN.LibraryHeader/DownloadsButton/filters/import sheet, SETTINGS, UI resources. **Проверки:** C-UI, C-LIBRARY, V18.

1. Сохранить все текущие команды в таблице old entry → new entry. Ни одна команда не должна потеряться при удалении toolbar.
2. Оставить title/search/overflow. Меню: «Выбрать», «Загрузки», «Обновить», «Управление полками», настройки библиотеки по текущему набору. Не помещать все эти элементы обратно в иконки header.
3. Одна Add-кнопка открывает существующий import sheet, позднее маршрут S01. Queue summary отдельной строкой появляется только при актуальном состоянии.
4. Sort/grid/filters объединить в «Отображение»; состояние пока использует текущие models, L05 позже расширит их. Active chips — один источник query state.
5. Проверить phone320/360, большие title/counter, fontScale2, selection replacing header, keyboard/search, lower nav/FAB inset.

**Выход:** максимум две обычные команды справа; прежние функции доступны. **Стоп:** красота достигнута скрытием действий без альтернативного входа — восстановить discoverability.

### L05 — единый query, сохранённые полки и filters

**Зависит:** L04, L01. **Код:** BOOK-MODEL, ONLINE-FILTER, LIB-VM, LIB-SCREEN, CATALOG-HOME, CATALOG-VMS, SETTINGS, DB-DAOS, DB-ENTITIES, DB; NEW LIB-QUERY. **Проверки:** C-DOMAIN, C-LIBRARY, DB-MIG-TEST.

1. Ввести type/origin/availability как независимые оси. Описать mapping EPUB/PDF/manga/audio/online novel и unknown формата, не определять availability по одному non-null localPath.
2. Единые tri-state поля с Any/Yes/No и одна sort spec; определить applicability sortUnread/sortUpdated для local. N/A не равно false в фильтре сервиса/закладок.
3. Query parser/state отделить от persisted defaults. Rotation/Back восстанавливают query через state owner; DataStore хранит постоянные предпочтения.
4. Добавить saved-query shelf рядом с manual shelf: versioned spec, счётчик по query, независимые display/sort overrides, default fallback. Переименования полок не меняют identity.
5. Расширить query исполнение репозиториев и SelectAll L03 одним predicate, чтобы выбор/счётчик/экран не расходились.
6. Случайный порядок P3: seed фиксируется на сессию/полку, явное действие обновляет его; paging не прыгает от каждой рекомпозиции. Сделать optional, не default.
7. Проверить все MP011–MP017 и сохранение legacy settings; новый фильтр в UI без реального SQL/predicate недопустим.

**Выход:** одинаковая семантика All/Online/полок. **Стоп:** нет определённого mapping поля — добавить его в query contract и тест, не угадывать по label.

### D02 — карточка по контенту

**Зависит:** D01, L02, L04. **Код:** BOOK-DETAILS, BOOK-DETAILS-VM, TITLE, общие компоненты `core/ui/src/main/kotlin/app/yomu/core/ui/components/`. **Проверки:** C-UI, BOOK-PANE-TEST, V04/V08/V18.

1. Выделить общие Header/Description/PersonalLibraryInfo блоки без переноса source-specific chapter logic в local ViewModel.
2. Порядок: header → progress+primary action → audio duration/narrator/offline при Audio → description → personal status/rating/shelves/tags → file/source → annotations/advanced.
3. На узком телефоне разрешить вертикальную шапку, длинное название переносить. Длительность/размер файла/размер downloaded tracks не смешивать; unknown size так и подписывать.
4. Нулевые счётчики annotations — компактный вход, не большая пустая карточка. Star rating personal отдельно от source/tracker score.
5. Тесты локальная без cover/с description, audio unknown duration, online chapters, tablet pane close, fontScale2 и длинные авторы.

**Выход:** описание и основная команда видны по правильной иерархии. **Стоп:** предлагают заменить всё одним CatalogEntry или Book с потерей source metadata — использовать UI model/adapters.

### L06 — страничная mixed projection

**Зависит:** L05. **Код:** DB-DAOS, DB-ENTITIES, BOOKS-REPO, CATALOG-REPO, LIB-VM, LIB-SCREEN, COVERS; NEW LIB-PROJECTION. **Проверки:** C-LIBRARY, V06/V19.

1. Зафиксировать baseline query timings/количество загруженных rows и recompositions при download tick.
2. Реализовать Room projection/стабильный merged paging с query sort keys; namespace identity и tie-breaker детерминированы. Не делать full-list `combine().sorted()` на UI/main thread.
3. Count/IDs/visible-page query используют одну семантику; индексы добавляются по query plan, не на все поля автоматически.
4. Сохранить прогресс list/grid по устойчивому key/anchor. Refresh не сбрасывает выбор/scroll и корректно даёт Paging access hints.
5. Изолировать download/audio ticks, cache revision и thumbnails. Проверить размер/eviction cover cache в filesDir и targeted refresh вместо очистки всего каталога.
6. Bench 1000 local/10000 online; сравнить сортировки, filters и selectAll. Итоговая инструментальная оценка остаётся Q03.

**Выход:** нет полных материализаций для обычного scroll/query. **Стоп:** оптимизация удаляет корректность/фильтры или заменяет access `items[index]` на `peek` без причины — отклонить такой способ.

## 10. Исполняемые пакеты: источники и импорт

### S02 — capabilities и диагностика подключения

**Зависит:** B00. **Код:** SOURCE-API, SOURCE-MODELS, SOURCE-MANAGER, SOURCE-CALLS, NETWORK, OPDS, RSS, SOURCE-BROWSE, CATALOG-VMS; NEW SOURCE-CAPS. **Проверки:** C-SOURCE.

1. Инвентаризация каждого встроенного типа: supportsSearch/latest/popular/navigation/files/stream/subscription/login. Устаревший extension API не должен получить новый обязательный abstract method.
2. Добавить optional capabilities с безопасным default/inference для existing sources, покрыть adapter contract старого источника.
3. Probe URL с лимитом bytes/time/redirects; определить OPDS/RSS/audio/поддерживаемую страницу по MIME+parse. Не сохранять connection до успешной проверки.
4. Ошибки typed: needs auth/browser, unsupported document, invalid URL, source API mismatch, timeout/network. Не считать любой 403 запросом пароля.
5. Отключить/скрыть unsupported tabs и дать понятное empty state; credentials отделить от display URL/log/history. LAN/private servers разрешены по явному адресу пользователя.
6. MockWebServer tests: misleading MIME, HTML instead feed, 401, 403, redirect, huge response, cancellation/new probe, valid OPDS/RSS.

**Выход:** capability UI отражает работающий backend. **Стоп:** нет parser подтверждённого типа — unsupported, не «успешно добавлено».

### S01 — мастер и гайд

**Зависит:** S02, L01. **Код:** CATALOG-HOME, CATALOG-VMS, EXT-SCREEN, EXT-VM, LIB-SCREEN, NAV, ROUTES, ONBOARDING; NEW SOURCE-WIZARD, SOURCE-WIZARD-VM. **Проверки:** C-SOURCE, C-UI, V09/V10.

1. State machine: ChooseContent → ChooseMethod → Input → Probing → AuthIfNeeded → Preview → Saving → Done/Error; Back возвращает на предыдущий шаг без сохранения полурабочего source.
2. Подключить Book/OPDS к existing addOpds; manga/extension — к existing extension workflow; files/folder — к ImportService; Audio/RSS — к S03. До S03 путь явно показывает разовый audio import, не ложную подписку.
3. Встроенный RU/EN help объясняет где взять OPDS/repo/RSS URL, разницу подключения и разового импорта. Ссылка help ведёт к текущему шагу мастера.
4. Preview содержит имя/тип/доступные действия и несколько реальных результатов probe. Save выполняется один раз; отмена и повтор не создают дубли.
5. Проверить state restoration без сохранения пароля в SavedState/Bundle, double submit, invalid URL и вход из обеих точек Library/Catalog.

**Выход:** гайд приводит к рабочему действию, а не README. **Стоп:** реальной поддержки пути нет — добавить backend по отдельному пакету, не показывать кнопку без результата.

### S03 — пользовательские RSS/audio sources

**Зависит:** S01, S02. **Код:** SOURCE-MANAGER, SOURCE-MODELS, RSS, AUDIO-STORE, AUDIO-MODEL, OPDS, OPDS-PARSER, WORKERS, DB, DB-DAOS, DB-ENTITIES, SOURCE-WIZARD; NEW SUBSCRIPTIONS. **Проверки:** C-SOURCE, C-AUDIO, DB-MIG-TEST, V09.

1. Схема connection/subscription/episodes: stable source identity, feed URI, ETag/LastModified, mode book-chapters/episode-series, GUID/canonical identity, per-feed update/download settings. Не хранить подписку только как текущий список треков manifest.
2. Probe возвращает parsed feed и выбор представления. Сохранение транзакционно связывает subscription и созданную книгу/записи, повтор не создаёт копию.
3. Refresh reconciliation сохраняет per-track progress/bookmarks при обновлённом enclosure; handle deleted/moved/new episodes. GUID приоритетнее expiring audio URL; при отсутствии GUID документировать deterministic fallback.
4. Conditional GET, Retry-After/backoff, один refresh job на subscription, cancel и reauth statuses; headers не передаются на чужой origin автоматически.
5. Проверить OPDS audio acquisition end-to-end и расширить только отсутствующие MIME/link ветки. Existing LibriVox/YouTube не дублировать.
6. Удаление connection отделить от удаления ранее добавленных books/files; backup schema знает новую connection без секретов.

**Выход:** источник сохраняется и обновляется после restart. **Стоп:** нельзя надёжно сопоставить обновлённый эпизод — ambiguity/review, не обнуление прогресса.

### N01 — сохраняемый отчёт импорта

**Зависит:** S01, L02. **Код:** IMPORT.ImportOutcome, LIB-VM, LIB-SCREEN, METADATA, WORKERS, BOOKS-REPO, NAV; опорные PERSIST-TEST, DB-MIG-TEST. **Проверки:** C-LIBRARY, V10.

1. Сохранить результат batch: stable job/item ID, display name, outcome, book ID, typed failure, retry eligibility. URI/секреты не попадут в публичный log.
2. UI summary+details+«Повторить ошибочные»/«Открыть дубль»/«Выбрать заново»; повтор импортирует только нужные элементы, а не всё дерево.
3. Reconnect file/folder использует существующие SAF permissions и identity guards. Временное отсутствие носителя не создаёт массовые tombstones.
4. Явные «Скопировать» / «Связать» и ownership объясняются до добавления. Worker progress и final report принадлежат одному job.
5. Fixture batch: valid, duplicate, unsupported, corrupt, password, revoked grant. Restart сохраняет отчёт и действия восстановления.

**Выход:** первый сбой больше не скрывает остальные. **Стоп:** recovery может перепривязать другую книгу без проверки identity — запросить конкретное подтверждение замены, не делать молча.

### S05 — extension lifecycle/API compatibility

**Зависит:** S01, S02. **Код:** EXT-MANAGER, EXT-STORES, EXT-SCREEN, EXT-VM, SOURCE-MANAGER, `compat/tachiyomi/src/main/`, SETTINGS, DB; существующие SourceCalls/ExtensionStores/Tachiyomi* tests. **Проверки:** C-SOURCE, V10.

1. Записать поддерживаемые API/fixtures из текущего compatibility layer; upstream main изучать только как версионированный reference, не считать все его internal API доступными.
2. Матрица state/allowed actions для installed/disabled/untrusted/broken/incompatible/updateAvailable. Broken APK остаётся доступен для update/uninstall.
3. Проверить origin binding: repository ID+package+допустимая подпись. Запрет silent switch to another repository; ручная смена origin оформляется отдельной операцией.
4. Обновление atomically меняет APK/registry и пересоздаёт затронутые adapters; preferences применяются без stale clients. Не меняются source IDs и пользовательские данные.
5. Batch update/revoke trust/import-export repositories: partial results, без секретов; cancel не оставляет в реестре «установлено» при незавершённом файле.
6. Проверки wrong package/signature/origin, corrupt APK, API mismatch, changed source languages, retry, uninstall; live fixtures отдельно по существующему opt-in.

**Выход:** рабочие старые источники сохранены, поломки восстановимы. **Стоп:** совместимость требует внутренних классов upstream — не имитировать их фиктивными значениями; явно ограничить конкретный extension и исследовать adapter.

### S06 — расширенные source/network настройки

**Зависит:** S05, S02. **Код:** SETTINGS, SETTINGS-UI, SETTINGS-V2, NETWORK, CATALOG-HOME, EXT-SCREEN, EXT-MANAGER, SOURCE-MANAGER. **Проверки:** C-SOURCE, C-UI.

1. Выписать MP017/MP091–MP099/MP124 и реальное покрытие; реализовывать пробелы отдельными небольшими подпакетами.
2. Content prefs Safe/Mixed/18+ с legacy migration и применением к installed по явному выбору; язык/pin/hide независимы от content preference.
3. Hide in-library фильтр использует canonical source identity, не совпадение названий.
4. UA/DoH/cookies/WebView clear показывают scope; probe/search/download/image clients используют согласованный network config, private server остаётся доступен.
5. Shizuku — опциональный backend установки system APK с capability detection, существующий private installer остаётся fallback. Не требовать root/новое разрешение на обычном пути.
6. Тесты миграции settings, installed hidden sources, origin header isolation, fallback без Shizuku, изменение prefs во время поиска.

**Выход:** все эти строки имеют реальный outcome. **Стоп:** библиотека/backend не поддерживает API — EXTERNAL/compat research, не добавлять декоративную настройку.

## 11. Исполняемые пакеты: чтение и обновления

### M01 — глава как единая последовательность

**Зависит:** R01, L02. **Код:** CHAPTER-ORDER, CHAPTER-FILTER, CATALOG-VMS.TitleViewModel, READER-VM, DOWNLOAD-REPO, TITLE; CHAPTER-TEST. **Проверки:** C-DOMAIN, C-READER, C-DATA.

1. Зафиксировать existing numbered/source/group/duplicate правила тестами. Номер -1 неизвестен, дробь и special chapter не округляются автоматически в «дубликат».
2. Добавить независимую policy skipRead/skipFiltered/skipDuplicate с current defaults. Resolver принимает intent Next/Previous/Resume/DownloadAhead и доступность.
3. Подключить один resolver в список, reader, notifications и downloads; excluded scanlator/duplicate preference одинаковы на всех входах.
4. Add mark-alternative-release-read только для надёжного chapter matching, missing chapters indicator только при надёжной нумерации. Swipe row actions используют ту же command logic.
5. Тесты two groups, decimal/special/unknown numbers, all filtered, downloaded-only, chapter deleted, resumed partial. Строки MP020–MP027/MP035 закрываются конкретными assertions.

**Выход:** один выбранный next chapter в UI/reader/downloader. **Стоп:** сопоставление строится по имени/номеру без confidence — сохранить альтернативы, не помечать прочитанными автоматически.

### M02 — spreads/zoom/tall images

**Зависит:** M01. **Код:** READER-MODEL, MANGA-CTRL, MANGA-VIEW, MANGA-SETTINGS, PDF-VIEW при dual parity, SETTINGS. **Проверки:** C-READER, V19.

1. Сначала virtual page model для split: physical page ID + half/spread transform. Locator не становится индексом виртуальной половины вместо настоящей страницы.
2. Реализовать split order/rotate-to-fit/cover-offset отдельно от dual-page toggle; при несовместимых опциях UI объясняет активное правило. RTL и non-RTL фиксируются тестовой таблицей.
3. Add start zoom position/smart fit/pan-before-turn/double-tap speed; существующий zoom lock/crop/side padding не потерять. При zoomed page navigation не должна преждевременно перескочить дальше pan.
4. Высокие изображения — tiled/subsampled decode с бюджетом; OCR использует отдельный ограниченный render. Размер картинки получать из bounds, не full decode.
5. Тестировать 1 cover+odd pages, two spreads, landscape portrait rotation, tile missing/retry, huge webtoon, split/read progress и backup locator.

**Выход:** MP041–MP051/MP059 закрыты без изменения физического прогресса. **Стоп:** для масштабирования нужен полный гигабайтный bitmap — реализовать budgeted adapter, не повышать heap как фикс.

### M04 — управление и навигаторы

**Зависит:** M02, R03. **Код:** INPUT, READER-MODEL, READER-SCREEN, READER-VM, READER-SETTINGS, READER-HINT, MANGA-VIEW, MANGA-SETTINGS. **Проверки:** C-DOMAIN, C-READER, V01/V18.

1. Добавить per-mode tap layouts/inversion X/Y с migration существующей bool. Preview и runtime читают одну resolver function.
2. Hide threshold, min zoom lock, animation speed и orientation options подключить к реальным handlers; fallback для split-screen/system policy.
3. Vertical navigator optional: side/height, accessible current/total, seek release commit с generation token; incoming old progress не возвращает slider назад.
4. Аппаратные keys/page-turner/mouse wheel проходят через общий intent resolver; volume controls не забираются у аудио вне чтения.
5. Onboarding overlay один раз или по запросу, отключение сохраняется; тесты scheme switch, focus, modal open, back gestures, reverse portrait.

**Выход:** настройки меняют реальное управление и доступны без скрытого жеста. **Стоп:** Android запрещает ориентацию в данном window mode — сохранить корректный fallback, не утверждать принудительную поддержку.

### M03 — session window онлайн-глав

**Зависит:** M01, M02, M04. **Код:** READER-VM.openOnline/openChapter, MANGA-CTRL, MANGA-VIEW, EPUB-CTRL, CATALOG-REPO, DOWNLOAD-REPO; NEW SESSION-WINDOW. **Проверки:** C-READER, C-DATA, V11.

1. Отдельный подпакет manga: state с current chapter и соседями, stable entry/chapter/page keys; один owner jobs/resources. Существующий local whole-book text не переделывать.
2. Ленивый preload next chapter с network policy; окно ограничено минимум current±1, дальние chapters освобождаются. Размер окна настраивается внутренним бюджетом, не бесконечной коллекцией controllers.
3. Ошибка/empty/deleted next chapter — inline retry/skip decision, текущая глава остаётся. Cancel старого preload не закрывает ресурсы отображаемой страницы.
4. Progress/read/tracker только после фактической видимости; preload ничего не отмечает. Persist anchor — chapter+physical page/offset+внутреннее положение, с fallback на сохранённую главу при missing page.
5. После успешной manga-приёмки подпакет online novels: bounded chapter DOM/window и identity bridge; не склеивать всю remote-серию одним безлимитным HTML.
6. Native tests 3 chapters forward/back, rotate/process recreation, offline/error/retry, different group, switch paged/feed, no duplicate resources. Сравнить memory plateau.

**Выход:** отсутствие Loading-replacement на обычной границе, устойчивый anchor. **Стоп:** требуется хранить все главы/битмапы в памяти или current/session owner не определён — сначала исправить lifecycle.

### U01 — policy умных обновлений

**Зависит:** L05, S02, M01. **Код:** CATALOG-REPO.checkUpdates, WORKERS.UpdateWorker/WorkScheduler, DB, DB-ENTITIES, DB-DAOS, SETTINGS; NEW UPDATE-POLICY. **Проверки:** C-DOMAIN, C-DATA, DB-MIG-TEST, V12.

1. Ввести typed CheckDecision: eligible/skip reason/nextCheckAt, clock передаётся зависимостью. Различать last attempt и last successful result.
2. Storage для per-title override/schedule, include/exclude shelves, restrictions started/completed/unread; legacy fallback не отключает уже настроенные обновления молча.
3. Прогноз по устойчивым интервалам валидных upload dates; минимум/максимум и unknown-date fallback. Пакетные релизы/hiatus не предсказывать точным обещанием.
4. Worker читает одну policy, группирует источники и соблюдает rate limit/backoff/RetryAfter/network/charging. Ручной override обходит prediction restrictions, не transport restrictions.
5. Persisted job result/counts, cancel/resume, per-title reasons. Автозагрузка вызывается только для действительно новых глав через DL policy.
6. Тесты controllable clock, completed/started/unread, excluded shelf, bad/future dates, timezone/DST display, 429, kill partial worker.

**Выход:** explainable next check и меньше ненужных запросов. **Стоп:** статистики релизов недостаточно — fixed fallback, не случайный прогноз.

### U02 — план проверок

**Зависит:** U01. **Код:** CATALOG-HOME, CATALOG-VMS, NAV, ROUTES, UPDATE-POLICY; NEW UPCOMING. **Проверки:** C-UI, C-DATA, V12.

1. Сделать repository projection nextCheckAt+reason и week/list UI; display timezone не меняет persisted UTC timestamps.
2. Filters include/exclude shelves и активный индикатор, empty/loading/error состояния. При нажатии открыть Details, manual check обновляет соответствующую запись.
3. Явный текст «Это план проверок, а не расписание выхода глав»; WorkManager не выдаёт гарантию минуты.
4. Тесты week boundary/DST, empty filter, manual reschedule, fixed/unknown date и unavailable source.

**Выход:** UI и worker используют один расчёт. **Стоп:** понадобился второй prediction алгоритм для календаря — переиспользовать policy.

### U03 — Updates/history и виджет

**Зависит:** U01, L03, M01. **Код:** CATALOG-HOME, CATALOG-VMS, CATALOG-REPO, DB-DAOS, NAV, WIDGET; CATALOG-UI-TEST. **Проверки:** C-LIBRARY, C-UI.

1. Использовать existing Updates/History, добавить filters/read/download/bookmark/group/shelves и multi selection с namespace соответствующей главы.
2. Group date/title, commands вызывают общую read/download policy, partial results и current chapter identity.
3. Clear history и reset progress — разные операции. Из history Resume следует L02; из notification конкретной главы — explicit chapter intent.
4. Виджет новых глав отдельным подпакетом после экранов: DB projection, клики, update/background restrictions, empty state без сети.
5. Тесты filtered empty, select after download update, history delete keeps progress, notification source missing, widget click и timezone grouping.

**Выход:** существующие экраны доведены без второй history БД. **Стоп:** требуется reset progress ради удаления history — исправить DAO command.

## 12. Исполняемые пакеты: загрузки

### DL01 — очередь и повтор

**Зависит:** M01, S03. **Код:** DOWNLOAD-REPO, DOWNLOAD-UI, AUDIO-DOWNLOAD, WORKERS, DB-DAOS, DB-ENTITIES, SETTINGS; QUEUE-TEST, DL-POLICY-TEST. **Проверки:** C-DATA, C-AUDIO, V13.

1. Выписать current persisted task states и owners chapter/audio queue; объединить представление/команды, не запускать вторую конкурирующую очередь.
2. Source/title grouping и reorder задают persisted priority с deterministic tie breaker. Fairness не допускает голодания других sources; pages/source concurrency раздельны.
3. Pause/cancel/title/source/all транзакционно меняют state; активные IO отменяются контролируемо, completion не возвращает cancelled задачу в success.
4. Retry классифицирует transient/reauth/no space/permission/429; RetryAfter и backoff сохраняются. Notification commands используют те же transition functions.
5. Tests kill/reboot, duplicate enqueue, reorder active job, one failing source, network loss, disk full, cancel during final write.

**Выход:** queue state, UI и уведомления согласованы. **Стоп:** ошибка «исправляется» удалением всех задач или бесконечным retry без причины — исправить state transition.

### DL02 — retention и автозагрузка

**Зависит:** DL01, L05. **Код:** SETTINGS, DOWNLOAD-REPO, WORKERS, CHAPTER-FILTER, DB, настройки полок; DL-POLICY-TEST. **Проверки:** C-DOMAIN, C-DATA.

1. Чистая retention policy: keep last N, remove-after-read/manual mark, bookmark protection, included/excluded shelves, storage cap и owned files.
2. Все read events, включая sync/manual, проверяют одну policy; default миграции сохраняет существующий autoDeleteRead смысл, не включает удаление там, где было выключено.
3. Preview возвращает IDs/bytes/защищённые исключения. Delete/download jobs получают снимок плана и повторно проверяют ownership/защиту перед фактической записью.
4. Auto-download only-unread/category rules выполняются после новой главы и чтения; существующие autoDownloadNew/downloadAhead не дублируются.
5. Tests bookmark/excluded shelf, two release groups, partial chapter, synced read, removed file, user-linked file never deleted, cancel/retry идемпотентны.

**Выход:** контролируемое освобождение места. **Стоп:** нельзя определить owner/что пользователь сохранял вручную — исключить файл и сообщить причину.

### DL03 — физический офлайн и индекс

**Зависит:** DL01, DL02. **Код:** DOWNLOAD-REPO, IMPORT, METADATA, DB-DAOS, DB-ENTITIES, storage adapter, MANGA-EDITOR. **Проверки:** C-DATA, V13.

1. Инвентаризация текущих путей/ID и legacy downloads. Существующие файлы не переименовывать без migration/rescan map.
2. New storage IDs устойчивы к source/title rename; export names отдельно очищаются по Unicode/ASCII preference с collision-safe suffix.
3. Temp write→validate→commit, скачано только при завершённом файле/индексе; источник truth для существующего downloaded состояния переопределить явно.
4. Rescan/repair index восстанавливает записи по identity; missing SAF root — temporarily unavailable, а не массовое удаление. `.nomedia` для каталоговых/локальных manga folders по корректной ownership.
5. ComicInfo metadata round-trip включая upload date, unknown fields по возможности сохранять. Tests same-name/special Unicode/rename/corrupt archive/SD removed/rescan repeated.

**Выход:** переименование не теряет downloads и export не перезаписывает соседний файл. **Стоп:** identity не восстановима — orphan review с выбором, не guessed attach к первой одноимённой книге.

### A02 — аудио: выбрать офлайн-объём

**Зависит:** S03, DL01, DL03. **Код:** AUDIO-SCREEN, AUDIO-VM, AUDIO-DOWNLOAD, AUDIO-STORE, AUDIO-MODEL, SETTINGS, DOWNLOAD-UI; AUDIO-RESUME-TEST. **Проверки:** C-AUDIO, V13/V17.

1. Sheet all/nextN/selected с именами track/chapter, estimated size/unknown, available disk и Wi-Fi setting.
2. Resolve stable track IDs в manifest; partial offline availability отдельно от book-added/stream-ready.
3. Delete selected copies сохраняет original streaming reference и progress; owned audio temp/copy отличается от source file.
4. Reuse Range/ETag/ContentRange rules. При changed remote representation не дописывать байты к старому файлу, безопасно перезапустить конкретный трек.
5. Tests offline partial boundary, unknown length, 200-vs206 response, expired URL, different ETag, cancel/no space, restart и воспроизведение доступного трека без HTTP.

**Выход:** офлайн-план совпадает с доступными треками. **Стоп:** источник не позволяет download — честный stream-only, не нерабочая кнопка.

## 13. Исполняемые пакеты: миграция, сервисы и sync

### MG01 — планирование и применение миграции

**Зависит:** L03, M01, DL03. **Код:** CATALOG-REPO.migrate, CATALOG-VMS.GlobalSearchViewModel, GLOBAL-SEARCH, TRACK-MANAGER, LIB-TABS; NEW MIGRATION-PLAN, MIGRATION-UI. **Проверки:** C-DOMAIN, C-DATA, C-UI, V14.

1. Разделить fetch/plan/apply. Plan содержит source/target identity, old/new revision, chapter matches+confidence, ambiguity/missing, переносимые data categories и downloaded files, которые остаются на старой записи.
2. Exact chapter number+group не универсален: -1 не match key, разные группы требуют review. Для page position проверить совместимость page count/version; иначе перенести read status и явно потерянный точный offset.
3. Preview позволяет отказаться/исправить matching/выбрать данные. До Apply не менять favorite/categories/progress.
4. Apply проверяет revision, делает DB transaction, связывает tracks/user fields и сохраняет reversible journal. File operations не внутри DB transaction; старый offline доступен до решения пользователя.
5. Batch migration — последовательность подтверждённых plans, с partial report, pause/restart и idempotency.
6. Tests no-op cancel, ambiguous -1, changed target while preview, failure inside transaction, repeated apply, undo, old downloads retained.

**Выход:** перенос reviewable и обратим по данным. **Стоп:** недостаточно соответствий — попросить выбрать их в UI, не автоматически mark all below max read.

### B01 — backup/restore и importer

**Зависит:** L04, S03, MG01. **Код:** BACKUP, MIHON-IMPORT, DB, MAPPERS, SETTINGS-V2, SETTINGS-VM; DB-MIG-TEST, BACKUP-SAFETY-TEST, RESTORE-TEST. **Проверки:** C-DATA, V15.

1. Перечень всех добавленных fields/tables/settings/files; backup manifest category version и отсутствующие optional sections.
2. Existing preview расширить counts/space/missing source/conflicts/merge-vs-replace. Replacement допускается только явным выбором пользователя.
3. Remap IDs всех зависимых rows, manual reset/reread сохраняется как семантическое событие, не всегда max progression.
4. Streaming decode и archive limits; staging и recovery для частичных файлов. Пароли/token credentials по умолчанию не включать.
5. Mihon fixtures с указанной version/provenance и stable/main format различиями; отсутствие неизвестного tracker adapter не портит остальные записи.
6. Tests empty/full DB, old schema→current, repeated merge, ID collision, truncated archive, zip limits, audio assets/source subscriptions и rollback failure.

**Выход:** восстановление полно и совместимо. **Стоп:** формат неизвестен — отказ до изменения БД с конкретным типом, не угадывание protobuf field mapping.

### T01 — tracker parity по одному сервису

**Зависит:** M01, B01. **Код:** TRACK-MODEL, TRACK-MANAGER, `data/src/main/kotlin/app/yomu/data/tracking/`, tracking UI в TITLE, BOOK-DETAILS и SETTINGS-V2; TRACKER-TEST. **Проверки:** C-DATA, C-UI, EXTERNAL live.

1. Для пяти existing services таблица: auth, refresh, search, bind, status, score, dates, progress, reread/privacy/delete, pending retry. Не переносить ID сервиса из чужого enum без проверки совместимости импорта.
2. Для каждого нового Bangumi/Hikka/MangaBaka/Comick сначала создать в журнале service contract: официальный URL документации, дата, API version/base URL, auth/redirect, необходимые scopes, rating/status mapping, limits и sample redacted responses. Endpoints не придумывать.
3. Adapter capabilities отражают supported fields, service-native values хранятся без потери точности. Local five-star score отдельно.
4. Pending commands сохраняются и объединяются по реальной семантике; expired token отличается от 5xx, parallel refresh один, offline updates не теряются.
5. Mock contract tests, затем live auth/search/bind/update/logout и retry. Отсутствие ключа оставляет live pending, не «логин проверен».
6. Обновить importer/backup/service mapping и UI только для реализованного adapter. Нельзя показывать provider как подключаемый до working auth.

**Выход:** отдельные принятые service подпакеты. **Стоп:** API/registration недоступны — BLOCKED конкретный provider с причиной; продолжить остальные.

### S04 — Audiobookshelf integration

**Зависит:** S02, S03, A02, B01. **Код:** SOURCE-MANAGER, AUDIO-STORE, AUDIO-MODEL, AUDIO-DOWNLOAD, AUDIO-SERVICE, SOURCE-WIZARD, credentials adapter; NEW adapter path выбрать после server contract. **Проверки:** C-SOURCE, C-AUDIO, EXTERNAL live.

1. Официальный server contract по шаблону T01: login/session/refresh, библиотеки/items, chapters/tracks/stream/download, progress API, server versions и rate limits.
2. Save connection с stable server+library+item IDs, secure credentials и connection test; url/название не единственный ID.
3. Browse/search/details → manifest → playback; expiring URL refresh не меняет track identity или position. Offline adapter использует A02.
4. Progress adapter двусторонний, loop prevention/change revision; ручной rewind/reset конфликт решается явно, не всегда max.
5. Tests expired token, changed URL, same item on another server, offline play→reconnect, two devices divergent progress и deleting connection retaining files.

**Выход:** реальная книга слушается и продолжает место на двух устройствах. **Стоп:** отсутствует тестовый сервер/документированный API — не конструировать «совместимый JSON» по памяти, оставить live gate.

### T02 — enhanced server tracking

**Зависит:** T01, S04 для общего audio progress interface; M01. **Код:** TRACK-MANAGER, TRACK-MODEL, source identity, CATALOG-REPO, credentials; новые adapters Komga/Kavita/Suwayomi. **Проверки:** C-DATA, EXTERNAL live.

1. Отдельный официальный contract на каждый server; OPDS endpoint не объявлять progress API.
2. Mapping remote item/chapter/page и local identity; capabilities server-driven. При изменении base URL проверить same-server identity прежде, чем переносить связи.
3. Bind/reconcile, offline event queue и conflict actions. Каждый outbound имеет origin/revision, чтобы inbound не порождал бесконечный resend.
4. Tests two-way completion, reread, partial page, offline reconnect, manual conflict, removed remote item, scope errors.

**Выход:** реальный server progress, не только каталог. **Стоп:** сервер не предоставляет нужную гранулярность — показать ограничение и поддерживаемую часть, не выдумывать page sync.

### B02 — field-complete sync

**Зависит:** B01, L04, S03, T01; S04/T02 только для включённых server connections. **Код:** SYNC-MODELS, SYNC-MERGER, SYNC-MANAGER, TRACK-SYNC, AUDIO-MODEL, AUDIO-STORE, BOOKS-REPO, backup models; MERGE-TEST, PERSIST-TEST. **Проверки:** C-DATA, V16.

1. Field matrix каждого объекта: persisted here, exported here, merged here, conflict rule, tombstone, legacy absence semantics. Отметить уже синхронизируемые данные, не переписывать их повторно.
2. Добавить отсутствующее audio position/stable track, bookmarks+notes, user metadata/overrides/связи, subscriptions/settings по согласованным categories. Credentials не синхронизировать автоматически.
3. Unknown fields/categories старого клиента не становятся удалением. Explicit reset/rewind/reread не затираются правилом max; предложить conflict choice.
4. Версии/identities, idempotent apply, tombstones и changed-file protection. Большие файлы и metadata sync раздельные tasks.
5. Tests двух изолированных БД: simultaneous offline edits, repeated exchange, older client, reset, deletion/edited file, new track list; затем реальные WebDAV/shared folder на двух устройствах.

**Выход:** field matrix закрыта evidence. **Стоп:** нет общего stable identity/политики конфликта — не отправлять destructive merge, сохранить conflict record.

### B03 — мастер и состояние sync/background

**Зависит:** B02, S01. **Код:** SETTINGS-V2, SETTINGS-VM, SYNC-UI, WORKERS, SYNC-MANAGER, ERROR-LOG. **Проверки:** C-UI, C-DATA, V16.

1. State machine backend → input → probe → data categories → schedule → preview first exchange → apply. Повтор/отмена не запускают два sync.
2. Один status summary: last success, current partial result, waiting conflicts, objects/bytes/errors; partial failure не маркируется «всё готово».
3. Гайды backup vs sync vs files, auth/network/storage/battery recovery по конкретному состоянию.
4. Tests invalid credentials, no permission, backend offline, pending conflict, retry after cancellation, state restore; real backend вручную.

**Выход:** настройка понятна без внешнего README. **Стоп:** внешняя проблема не воспроизводится mock — записать детали и live pending, не скрывать error banner.

## 14. Исполняемые пакеты: комфорт и дополнительные функции

### A01 — audio controls

**Зависит:** A02. **Код:** AUDIO-SETTINGS, AUDIO-SERVICE, AUDIO-CTRL, AUDIO-SCREEN, AUDIO-VM, PLAYER-MATH, AUDIO-HISTORY. **Проверки:** C-AUDIO, V17.

1. Инвентаризация existing rewind/silence/timer/end-of-chapter/history; новые реализации только для непокрытых веток.
2. Rewind defaults→per-book: off/adaptive/custom seconds, migration сохраняет прежний default. Все UI/notification/headset play/resume команды идут через service owner.
3. Fade/end-of-chapter timer и resume-after-focus уточнить по capabilities текущего Media3. Off означает действительно отсутствие rewind/fade, cancellation не перематывает.
4. Tests pause durations, skip/seek while paused, speed chapter transition, timer cancel, focus transient/permanent, disconnect headset/Bluetooth, incognito history; real audio обязательное.

**Выход:** одна позиция и один command owner. **Стоп:** UI напрямую управляет вторым player — убрать дублирующий playback, не синхронизировать два независимых экземпляра.

### A03 — rich audio и внешние surfaces

**Зависит:** A01, D01, B02. **Код:** AUDIO-MODEL, AUDIO-META, AUDIO-STORE, AUDIO-SERVICE, AUDIO-SCREEN, BOOK-DETAILS, WIDGET, NAV, DB. **Проверки:** C-AUDIO, C-UI, EXTERNAL/device.

1. Подпакет metadata: narrator/series/language/description overrides и реальная total duration/download size; backup/sync mapping тестируется.
2. Подпакет gain: измерить signal/metadata loudness, подобрать ограниченный gain без clipping, cache анализа и отключение; не заменять это максимальной громкостью.
3. Подпакет audio widget: one service commands, correct current book/position, после reboot не начинается звук сам.
4. Подпакет Android Auto: проверить текущий MediaSessionService vs MediaLibraryService, документацию Media3, browse tree/auth/offline; не менять service superclass без protocol tests.
5. Подпакет linked editions: manual text↔audio relation с source edition metadata. Без alignment — переход к связанному изданию/последней его позиции, без обещания точной фразы.

**Выход:** каждый подпакет принят отдельно. **Стоп:** внешняя surface недоступна для теста — оставить pending; не объявлять Android Auto по наличию manifest entry.

### N02 — внутричитальные settings

**Зависит:** R03, M04, A01. **Код:** READER-QUICK, READER-SETTINGS, READER-VM, MANGA-SETTINGS, PDF-CTRL, SETTINGS, QUICK-PANEL-TEST. **Проверки:** C-READER, C-UI, V18.

1. Реестр видимых options по engine и mode, current scope (book/global). Существующие preview/search/presets/undo сохранить.
2. Явный scope control, defaults наследуются; reset override означает наследовать, а не копировать старые defaults навечно.
3. Quick first, advanced collapsed, доступные numbers/segmented controls; реальные preview text/PDF crop/spread, Cancel/Undo не сохраняют невыбранные draft values.
4. TTS sample voice и описание text-layer/OCR ограничения PDF; не запускать полное чтение для preview.
5. Tests book/global migration, reset, mode-specific hidden controls, process restore и fontScale2.

**Выход:** настройка меняет только заявленную область. **Стоп:** preview использует иную модель цвета/шрифта, чем reader — исправить shared effective settings.

### N03 — accessible/E-ink preset

**Зависит:** N02, M04. **Код:** READER-MODEL, READER-SETTINGS, READER-SCREEN, input, theme. **Проверки:** C-READER, C-UI, E-ink device.

1. Пресет no animation/contrast/size применяется как набор понятных настроек и имеет Undo, не отдельный reader engine.
2. Optional software flash каждый N page changes, duration/color, off default; counted physical page event не каждый slider preview tick.
3. Специальный panel refresh только с проверенным vendor API/capability и fallback; поддерживаемые устройства перечислить явно.
4. Tests transition counts, toggle off cancels pending flash, night mode, hardware page-turner, TalkBack; реальный E-ink ghosting отдельно.

**Выход:** полезный доступный пресет, честные hardware ограничения. **Стоп:** нет vendor API — программный fallback, не утверждать аппаратное refresh.

### N04 — слова, контекст, глоссарий и индекс

**Зависит:** L02, N02, B02. **Код:** существующие каталоги `feature/dictionary/src/main/kotlin/app/yomu/feature/dictionary/`, `feature/annotations/src/main/kotlin/app/yomu/feature/annotations/`, `feature/translator/src/main/kotlin/app/yomu/feature/translator/`, DB-DAOS, DB-ENTITIES, READER-VM. **Проверки:** C-DATA, C-UI.

1. Найти DictionaryViewModels/WordsContent/WordLookup/AnnotationsViewModel и current export/SRS; не писать второй SRS.
2. Точный book+locator context и перейти в исходное место через L02; missing book → recovery, edition changed → approximate label.
3. Book-filter words, first dictionary workflow, glossary/character notes с identity и backup/sync. Translation retry отражает cache/error/model state.
4. Fulltext/OCR index отдельным optional background job: estimate/time/disk/cancel, incremental updates, remove/rebuild with book revision; title search не переименовывать в fulltext.
5. Tests original context after text reflow, deleted/relinked book, export round trip, partially built index, cancellation и secret text absent logs.

**Выход:** новые функции имеют storage/restore и действия. **Стоп:** нет stable locator после редактирования — показать ограничение/предложить reanchor, не прыгать в случайный offset.

### N05 — длинные задачи и редактор

**Зависит:** N01, B01. **Код:** EPUB-EDITOR, MANGA-EDITOR, existing editor screens/workers, DOWNLOAD-UI pattern, translator jobs. **Проверки:** C-EDITOR, C-UI.

1. Реестр операций: existing copy/save/undo/validation/translation, определить недостающие brush/merge/split/recovery. Не реализовывать повторно существующее.
2. Общий task result(progress/cancel/partial errors/output), ownership temp/workspace, recovery journal и preview before commit.
3. Merge/split chapters/volumes сохраняют image/metadata order, потоково работают с архивом; cleaning brush — reversible edit layer до export.
4. Validate staged result и atomically publish destination; оригинал не изменять до явного save. Cancellation не объявлять completed.
5. Tests edit→undo→save→open independent viewer, kill mid task, no-space, corrupt page, same-name export, cancel and recover.

**Выход:** сохраняемый результат и оригинал целы. **Стоп:** output validation не прошла — оставить рабочую копию и ошибку, не заменить оригинал.

### M05 — experimental renderer

**Зависит:** M02, M03; baseline Q03-методика выполняется до прототипа. **Код:** новый backend interface рядом с MANGA-CTRL, MANGA-VIEW, image pipeline, advanced settings. **Проверки:** C-READER, Q03 metrics, несколько GPU.

1. Зафиксировать конкретную upstream-main функцию MP062/MP063, её пользовательскую пользу и текущий Yomu bottleneck. Не переносить пачку shader controls без сценария.
2. Документированный исследовательский подпакет: Android/backend support, quality fixtures, CPU/GPU/memory/battery baseline, измеримые условия успеха.
3. Реализовать opt-in prototype за interface с обычным renderer fallback; device lost/shader failure возвращает рабочий путь, не краш.
4. Shader/upscale/downscale/filter pipeline версионирует settings, корректно чистит ресурсы и не меняет original image/progress.
5. Сравнить качество и latency на одном наборе/устройствах. Отсутствие выигрыша — эксперимент отклонён/оставлен открытым, не мнимая production-фича.

**Выход:** отдельное доказанное решение о включении. **Стоп:** нет поддерживаемого runtime/GPU evidence — не включать по умолчанию и не заявлять parity.

## 15. Исполняемые пакеты: итоговое качество

### Q01 — закрепить границы компонентов

**Зависит:** все завершённые функциональные пакеты; локальные выделения выполняются раньше. **Код:** READER-VM, READER-SCREEN, LIB-VM, LIB-SCREEN, CATALOG-VMS, NEW components, APP-DI, DATA-DI. **Проверки:** профили затронутых модулей + C-RELEASE.

1. Реестр owners input/session/selection/query/operations/source connections/update policy. Одна mutable owner state на функцию.
2. Удалить временные compatibility delegates только после обновления всех callers; public extension API не ломать.
3. Domain чистый, UI не блокирует main IO, jobs cancels не проглатываются; lifecycle ownership проверен.
4. Отдельные refactor commits/изменения только при заданной пользователем политике commit; не переписывать весь проект для уменьшения line count.
5. Test cancellation/rotation/disposal и unchanged behavior. Итоговая DI регистрация Koin без duplicate bindings.

**Выход:** архитектура соответствует фактическим компонентам и docs. **Стоп:** refactor меняет пользовательский договор вне пакетов — вернуть поведение/оформить отдельную задачу.

### Q02 — visual/a11y проход

**Зависит:** все изменённые UI-пакеты. **Код:** их screens/resources и app UI tests. **Проверки:** C-UI, V03/V18, device TalkBack.

1. Для всех новых controls content description/role/state/focus order/48dp; декоративные icons не дублируют label.
2. RU/EN resource coverage, длинные строки, no hardcoded product strings. Проверить состояние selection/rating/download/error.
3. Width320/360/411/tablet split, fontScale1/1.3/2, light/dark/AMOLED, gesture/3button navigation, keyboard+bottom sheet.
4. Поведенческий путь добавить источник→выбрать mixed items→Resume→PDF menu через TalkBack; wheel имеет linear fallback.
5. Review screenshots/goldens строго по VISUAL_TESTS, сохранить observed failures/fixes; recording не считать verify.

**Выход:** все основные действия доступны, ничего не перекрыто. **Стоп:** экран непроверяем на desktop из-за native — device gate остаётся открыт, не удалять сценарий.

### Q03 — perf/memory evidence

**Зависит:** L06, M02/M03, DL01; до M05 можно baseline. **Код:** profiling harness и выявленные реальные hotspots, не произвольная оптимизация. **Проверки:** V19 на fast/release.

1. Зафиксировать device/OS/refresh rate/build/temperature/network и версии fixture library. Наборы из продуктового плана: 1000 local/10000 online,500+ PDF,large CBZ/tall page/EPUB.
2. Измерить cold/warm start, first useful page, frame p50/p95/p99 и >50ms count, memory steady/peak, IO/network request counts.
3. Одинаковый сценарий до/после, несколько повторов; сравнивать статистику, исключать загрузку сети из local-open latency.
4. Исправлять только измеренные bottlenecks с сохранением correctness tests; no-debug/Robolectric FPS claims.
5. Report содержит сырые измерения/методику/limitations и принятые цели устройства, а не только «стало быстрее».

**Выход:** воспроизводимые цифры и отсутствие OOM/роста памяти. **Стоп:** исходные условия отличаются — не делать вывод о выигрыше, повторить сопоставимо.

### Q04 — privacy/error/recovery договор

**Зависит:** все network/state функции; исправления выполнять по мере задач. **Код:** ERROR-LOG, NETWORK, SOURCE-CALLS, READER-VM, CATALOG-REPO.saveProgress, AUDIO-SERVICE, TRACK-MANAGER, WORKERS. **Проверки:** C-DATA/C-READER/C-AUDIO, V20.

1. Проверить договор incognito §5.2 на всех прямых entry points repo/service/worker, manual vs automatic events. Toggle не удаляет существующие данные.
2. Structured safe stage ID/context и user recovery action. Mask auth/cookies/tokens/query credentials/text до write, не только export.
3. Fixtures с fake secret во всех headers/URL/error messages и user text; assertions absence в persisted log/export/UI preview.
4. Needs-auth/no-space/no-SAF/source unavailable объясняются отдельно. Silent null-return важного action заменить typed result+сообщением.
5. Notifications/lockscreen respect privacy setting; live token не нужен для проверки redaction, реальные аккаунты не выводить в отчёт.

**Выход:** безопасная диагностика и единый private-mode. **Стоп:** невозможно очистить ошибку, содержащую secret, — не писать raw throwable в persistent log; сохранить safe category/code.

### Q05 — документация, матрица и выпускная проверка

**Зависит:** все функциональные задачи имеют ACCEPTED/CODE_VERIFIED/BLOCKED с доказательствами; Q01–Q04 выполнены в доступном объёме. **Код:** docs, SETTINGS-UI, APP-UPDATE-UI, существующие date/time formatters после поиска их вызывающих. **Проверки:** C-RELEASE, V01–V20, матрица MP001–MP128.

1. Обновить USER_GUIDE/EXTENSIONS/KNOWN_LIMITATIONS/NOT_DONE/PROGRESS/ARCHITECTURE/README по одному фактическому HEAD, не объявлять отложенные P3 готовыми.
2. Остаточный проход всех upstream preference groups/screens из pinned matrix: отметить implemented/verified/different/blocked с тестом и ссылкой. Новые обнаруженные различия добавить MP129+, не скрывать для заявления «всё».
3. Date/time locale/system12/24, skip-app-version и migration progress проверить/добавить только обнаруженные gaps; не обновлять зависимости/AGP ради этого.
4. Запустить C-RELEASE и approved visual verification; установить fast/debug поверх совместимой прежней тестовой APK без data clear, проверить migration/read/resume/backup. Release signing не подменять debug key.
5. Финальный отчёт перечисляет закрытые пакеты, оставшиеся device/live gates, реальные artefacts и matrix coverage. Публикация APK выполняется только при отдельном поручении; сборка не равна опубликованному релизу.

**Выход:** проверяемый итог и честный остаток. **Стоп:** есть открытые обязательные gates — не ставить общей работе «полностью завершено» и не объявлять полный parity.

## 16. Общий протокол изменения данных

Для каждого пакета, меняющего storage, обязательна последовательность:

1. Прочитать current entities/DAO/schema и все serializers/backup/sync paths поля. Составить before→after и legacy default.
2. Сначала добавить миграционный fixture/тест, который доказывает сохранность старого значения и связей.
3. Изменить модель/DAO/Migration/registry/exported schema согласованно. Номер версии брать из HEAD после предыдущей принятой задачи.
4. Обновить mappers и decode fallback; неизвестное optional поле не должно очищать всю запись. Explicit deletion и отсутствующее поле старой версии различимы.
5. Round-trip tests: DB readback, backup restore в пустую/заполненную БД и sync двух версий там, где поле синхронизируется.
6. File side effects staging/recovery отдельно от SQL transaction. Если часть операции нельзя откатить, preview и список неатомарных side effects обязательны до запуска.

В частности, L04/L05/S03/U01/MG01/B01/B02 не могут закрыться только UI-тестом. Наличие `fallbackToDestructiveMigration` как способа пропустить тест несовместимости запрещено.

## 17. Шаблон отчёта одного пакета

```text
Пакет: ID и название
База: фактический commit; исходные пользовательские изменения
Статус: IN_PROGRESS / CODE_VERIFIED / ACCEPTED / BLOCKED
Подтверждённая цепочка: вход → функция → состояние → результат
Изменено: фактические пути; отдельно новые компоненты
Миграции: исходная/итоговая версия; legacy default; backup/sync coverage
Команды: точная команда → exit/result → где отчёт
Поведение: сценарии и наблюдаемый результат
Device/live: что действительно выполнено; что pending и почему
Матрица: MP-строки и их фактический итог
Осталось: конкретный незавершённый шаг или следующая задача по порядку
```

Шаблон — описание будущего отчёта, не заполненный результат. Фразы «должно работать», «скомпилировалось, значит исправлено» и «всё проверено» без перечня выполненных действий не являются evidence.

## 18. Готовый текст передачи следующему агенту

> Работай в репозитории Yomu. Сначала прочитай актуальные AGENTS.md, `docs/AGENT_EXECUTION_PLAN_2026-11-16.md` и существующий журнал `docs/execution/2026-11-16/STATUS.md`, если он уже создан. Выполняй B00 и затем незавершённые пакеты в каноническом порядке раздела 7; если в текущем поручении задан меньший набор, соблюдай его. Цели и V01–V20 находятся в `docs/DEVELOPMENT_PLAN_2026-11-16.md`, сравнение — в `docs/MIHON_PARITY_2026-11-16.md`. Проверяй текущий код до изменения, различай EXISTING/NEW/HYPOTHESIS/EXTERNAL, не придумывай API/файлы/успешные тесты. После каждого пакета сохраняй точные результаты и недоступные проверки в журнал. Не создавай дубли существующих систем, не меняй чужие файлы и не объявляй полный успех при открытой обязательной приёмке.
