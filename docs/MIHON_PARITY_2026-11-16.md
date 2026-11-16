# Yomu ↔ Mihon: матрица возможностей и задач

Дата: 16 ноября 2026. Основной документ: [план реализации](DEVELOPMENT_PLAN_2026-11-16.md).

AI-агенту: выполнение начинать с [пошаговой инструкции и реестра точных путей](AGENT_EXECUTION_PLAN_2026-11-16.md). Строка матрицы задаёт сравниваемую возможность, а не готовый API или доказательство, что её уже проверили.

Yomu: `0120bbc`. Mihon: стабильная документация 0.21.3 и исходники `main` на `4823ba69ca65fa707fb89406f3a4c43c9e0053dd`. Статусы отражают чтение кода и документации, **не сравнительную проверку двух приложений на телефоне**. Утверждение «есть» означает наличие реализации; её качество и точные edge cases должны пройти приёмку.

Обозначения:

- **Есть** — базовая возможность реализована; задача относится к проверке, согласованию UX или расширению.
- **Частично** — основа есть, но отсутствует часть сценария/настроек либо есть подтверждённый дефект.
- **Добавить** — в изученных моделях и путях нет соответствующего механизма; перед реализацией повторить поиск в актуальном HEAD.
- **Проверить** — данных статического прохода недостаточно для честного вывода о паритете.
- **Main/эксперимент** — обнаружено в текущей ветке разработки; не приписывать автоматически стабильной версии.

ID задач R/D/L/S/M/U/DL/T/MG/B/A/N/Q отсылают к основному плану. Статус «Есть» не означает, что всю строку можно закрыть без проверки, и не требует переписывать работающую функцию.

## 1. Библиотека и организация

Основа сравнения: upstream `LibraryTab`, `LibraryPreferences`, официальные [Library FAQ](https://mihon.app/docs/faq/library) и [Categories](https://mihon.app/docs/guides/categories). Yomu: `LibraryScreen`, `LibraryViewModel`, `OnlineLibraryPane`, `LibraryFilter`, `LibraryDisplay`, пользовательские tabs.

| ID | Возможность Mihon | Yomu сейчас | Что делать | Приоритет / задача |
|---|---|---|---|---|
| MP001 | Сетка/список и плотность обложек | Есть | Общая настройка для local и online, отдельно портрет/ландшафт | P1 / L01, L05 |
| MP002 | Категории, порядок, выбор при добавлении | Есть | Проверить смешанные полки, порядок, default category и пакетное назначение | P1 / L04–L05 |
| MP003 | Отображение категорий/количества элементов | Частично | Согласовать полки и счётчики; не считать только загруженные плитки | P2 / L05–L06 |
| MP004 | Параметры отображения на категорию | Частично | Вид, сортировка, фильтры на полку с глобальным fallback | P2 / L05 |
| MP005 | Long press → мультивыбор | Частично | Единый режим для локальных, online и результатов каталога | P1 / L03–L04 |
| MP006 | Выбрать всё/инверсия/диапазон | Частично | Распространить существующий выбор глав на библиотеку; полная область Paging-запроса | P1 / L03 |
| MP007 | Массовая отметка read/unread, категории, загрузки, удаление | Частично | Общие capabilities, preview применимости, Undo и отчёт | P1 / L04 |
| MP008 | Миграция выбранных тайтлов | Частично | Одиночная миграция есть; пакетная через индивидуальные планы | P2 / MG01 |
| MP009 | Resume на библиотечной плитке | Частично | Один договор открытия и отдельный Resume для всех типов | P1 / L02 |
| MP010 | Поиск в библиотеке и активные фильтры | Есть | Единый запрос local+online и сохранение UI после Back | P2 / L05 |
| MP011 | Tri-state downloaded/unread/started/bookmarked/completed | Частично | Общие tri-state поля вместо несовпадающих bool-фильтров | P2 / L05 |
| MP012 | Фильтр по трекеру и индивидуальному интервалу | Добавить | По привязке к сервису и override update interval | P2 / L05, U01 |
| MP013 | Сортировка: title/update/unread/last-read/added/chapters | Частично | Объединить существующие local/online варианты, определить неподходящие типы | P2 / L05 |
| MP014 | Случайная сортировка/открыть случайный тайтл | Добавить | Опционально: стабильный random seed на сессию и действие из меню полки | P3 / L05 |
| MP015 | Badges unread/downloaded/local/language | Частично | Общие badge capabilities и настройки; не перегружать обложку | P2 / L05, D02 |
| MP016 | Глобальный downloaded-only | Частично | Отличить полностью офлайн/частично; применить к Resume и поиску | P2 / L05, M01 |
| MP017 | «Скрыть уже в библиотеке» при browsing | Проверить | Добавить/согласовать фильтр и индикацию наличия, не дублировать запись | P3 / S06 |

Умные полки по сохранённому запросу в L05 — собственное развитие Yomu. Не считать их доказанной отсутствующей функцией Mihon.

## 2. Карточка, главы и миграция

Основа: upstream manga screen, `LibraryPreferences`, руководства [Source migration](https://mihon.app/docs/guides/source-migration) и [Reader settings](https://mihon.app/docs/guides/reader-settings). Yomu: `TitleScreen`, `BookDetailsScreen`, `ChapterOrder`, `ChapterDisplay`, `CatalogRepository.migrate()`.

| ID | Возможность | Yomu сейчас | Что делать | Приоритет / задача |
|---|---|---|---|---|
| MP018 | Описание/автор/жанры/источник/обложка | Есть | Сохранение метаданных по всем веткам; компактная общая карточка | P1 / D01–D02 |
| MP019 | Смена обложки и страница как обложка | Частично | Проверить все движки; добавить получение PDF-страницы | P1 / R02, D02 |
| MP020 | Сортировка глав по source/number/date и направление | Есть | Одна последовательность для списка, Resume, reader и download | P2 / M01 |
| MP021 | Фильтры read/downloaded/bookmarked, defaults | Частично | Расширить существующие bool-фильтры tri-state и явным defaults UI | P2 / M01 |
| MP022 | Выделение глав, диапазон, выбрать непрочитанные | Есть | Проверить частичную загрузку/обновление списка, не сбрасывать выбор | P2 / M01, U03 |
| MP023 | Отметить предыдущие прочитанными | Есть | Предпросмотр диапазона и общий порядок глав | P2 / M01 |
| MP024 | Исключить группы перевода и дубликаты | Есть | Единая политика с next/previous, без угадывания ненадёжных номеров | P2 / M01 |
| MP025 | Синхронная отметка альтернативных релизов | Частично | Независимая настройка mark duplicates read с надёжным matching | P2 / M01 |
| MP026 | Индикаторы пропущенных глав | Добавить | Только для надёжной нумерации, отключаемый индикатор | P2 / M01 |
| MP027 | Swipe actions в списке глав | Проверить | Настраиваемые read/bookmark/download; защита от случайного удаления | P2 / M01 |
| MP028 | Открыть WebView/поделиться source URL | Есть | Единый вход из local downloaded и online карточки, безопасные ссылки | P1 / D02, S02 |
| MP029 | Миграция одного тайтла или из списка источника | Частично | Preview соответствий, данных и сохранённого офлайна | P2 / MG01 |
| MP030 | Выбор переносимых данных при миграции | Добавить | Progress/bookmarks/categories/track links/user metadata отдельно | P2 / MG01 |

В Yomu особенно важно отделить собственный ReadingStatus от статуса публикации сайта. Поле source status нельзя использовать как замену личному «Читаю/Прочитано».

## 3. Читалка и графические режимы

Основа: официальное руководство читалки и upstream `ReaderPreferences`. Yomu: `ReaderSettings`, `MangaReaderView`, `PdfReaderView`, `ReaderScreen`, `ReaderSettingsSheet`, `ReaderQuickPanel`.

| ID | Возможность | Yomu сейчас | Что делать | Приоритет / задача |
|---|---|---|---|---|
| MP031 | Paged LTR/RTL/vertical | Есть | Проверить порядок страниц/клавиш и сохранение режима на тайтл | P2 / M01, M04 |
| MP032 | Long strip / long strip with gaps | Есть | Не добавлять повторно; проверить поля, масштаб и прогресс | P2 / M02–M03 |
| MP033 | Переходы между соседними главами | Частично | Сейчас замена контента с Loading; добавить session window/preload | P2 / M03 |
| MP034 | Показ разделителя/перехода главы | Частично | Управляемый разделитель и понятная ошибка следующей главы | P2 / M03 |
| MP035 | Skip read / filtered / duplicates | Частично | Разделить настройки и согласовать с ChapterOrder | P2 / M01 |
| MP036 | Tap zones и просмотр схемы | Частично | Исправить единицы PDF, сохранить существующие presets/preview | P0 / R01, M04 |
| MP037 | Разные tap zones paged и webtoon | Частично | Хранить независимые варианты с совместимой миграцией defaults | P2 / M04 |
| MP038 | Инверсия зон по X/Y | Частично | Сейчас общая bool-инверсия; добавить независимые оси | P2 / M04 |
| MP039 | Кнопки громкости и инверсия | Есть | Согласовать с общими клавишами и текущим engine | P2 / M04 |
| MP040 | Orientation/fullscreen/cutout/keep-screen-on | Частично | База есть; сравнить reverse portrait и sensor/locked варианты, проверить inset и окно | P2 / M04, N02, Q02 |
| MP041 | Чёрный/серый/белый/автоматический фон | Частично | Базовые три есть; auto background с ограниченным анализом картинки | P2 / M02 |
| MP042 | Номер страницы и индикация режима при открытии | Частично | Не перекрывать страницу; показывать режим по настройке/первому запуску | P2 / M04 |
| MP043 | Анимация перелистывания и скорость zoom | Частично | Анимации есть; расширить независимую настройку double-tap | P2 / M02, M04 |
| MP044 | Fit screen/width/height/original/stretch/smart fit | Частично | Четыре режима есть; остальные адаптировать с объяснением искажения | P2 / M02 |
| MP045 | Начальная позиция zoom | Добавить | Left/right/center/auto, учитывать RTL и широкую страницу | P2 / M02 |
| MP046 | Pan wide image до перехода страницы | Частично | Явное pan-before-next и автоматический fit для landscape | P2 / M02 |
| MP047 | Crop borders и отдельная политика webtoon | Частично | Crop есть, разделить настройки по режимам, не резать текст | P2 / M02 |
| MP048 | Split wide pages и порядок половин | Добавить | Virtual pages с сохранением реального page ID и прогресса | P2 / M02 |
| MP049 | Rotate wide pages to fit, инверсия | Добавить | Отдельные параметры paged/webtoon и согласование со split | P2 / M02 |
| MP050 | Dual page modes / смещение обложки | Частично | Две страницы есть; добавить offset/cover и верные развороты | P2 / M02 |
| MP051 | Webtoon side padding и double-tap zoom | Есть | Не реализовывать заново; проверить зум после rotation/split-screen | P2 / M02, Q02 |
| MP052 | Порог скрытия UI при скролле | Добавить | Несколько понятных значений без дёргания панели | P2 / M04 |
| MP053 | Webtoon disable zoom out | Добавить | Опциональная блокировка минимального масштаба | P2 / M04 |
| MP054 | Вертикальный page navigator, сторона/размер | Добавить | Доступный навигатор с точным locator и без захвата scroll | P3 / M04 |
| MP055 | Яркость/цветовой фильтр/grayscale/invert | Есть | Согласовать общие и image-фильтры; не удваивать обработку | P2 / N02, M02 |
| MP056 | Long-tap save/share/copy/set cover | Частично | Общие page capabilities, PDF fallback, единый RadialMenu | P0/P1 / R02–R03 |
| MP057 | Сохранение страниц в отдельные папки | Проверить | Предсказуемый MediaStore/SAF export и настройка группировки | P3 / R02, DL03 |
| MP058 | Preload pages, Wi-Fi-only load ahead | Частично | Предзагрузка/экономия трафика есть; отдельное policy и UI по сети | P2 / M03, S02 |
| MP059 | Очень высокие страницы без OOM | Проверить | Tiling/subsampling, бюджет OCR и prefetch, измерение peak memory | P2 / M02, Q03 |
| MP060 | E-ink page flash: interval/duration/color | Добавить | Опциональный программный режим, проверка на устройстве | P3 / N03 |
| MP061 | Быстрые настройки/настройки конкретного тайтла | Есть | Проверить область сохранения и engine-specific controls | P2 / N02 |
| MP062 | WebGPU/quality renderer, shaders/up/downscalers | Main/эксперимент | Собственный прототип, профилирование, fallback; не часть готового stable parity | P3 / M05 |
| MP063 | Новые continuous gap/min-width/cutout/dual animation параметры backend | Main/эксперимент | Рассматривать вместе с renderer, не переносить неподходящие переключатели в обычный UI | P3 / M05 |

Фикс PDF R01 — самостоятельный дефект Yomu. Наличие похожих tap zones в другом приложении не доказывает ни его причину, ни исправление. Причина установлена по цепочке текущего кода Yomu.

## 4. Обновления и история

Основа: `LibraryPreferences`, `UpdatesPreferences`, `UpcomingPreferences`, `UpdatesTab`, `HistoryTab`, руководства [Smart updates](https://mihon.app/docs/faq/updates/smart) и [Upcoming](https://mihon.app/docs/faq/updates/upcoming).

| ID | Возможность | Yomu сейчас | Что делать | Приоритет / задача |
|---|---|---|---|---|
| MP064 | Фоновое расписание обновлений | Есть | Проверить worker constraints, восстановление и объяснимый результат | P2 / U01 |
| MP065 | Ограничения по устройству/сети/полкам | Частично | Расширить Wi-Fi настройками включения/исключения и charging policy | P2 / U01 |
| MP066 | Smart updates: started/ongoing/no-unread/prediction | Добавить | Собственный UpdatePolicy, причины пропуска, ручной override | P2 / U01 |
| MP067 | Индивидуальный интервал и прогноз следующей проверки | Добавить | Persisted schedule с безопасным fallback | P2 / U01 |
| MP068 | Upcoming calendar | Добавить | Календарь проверок; не обещать дату выпуска главы | P2 / U02 |
| MP069 | Фильтры категорий календаря | Добавить | Общие правила included/excluded и видимый active filter | P2 / U02 |
| MP070 | Обновление metadata/cover и политика смены title | Частично | Пользовательские overrides не затираются; refresh обложек по смыслу | P2 / D01, U01, L06 |
| MP071 | Лента обновлений с массовыми действиями | Частично | Выбор, download/read/bookmark, сортировка по дате/тайтлу | P2 / U03 |
| MP072 | Updates tri-state filters и excluded groups/categories | Частично | Единая модель фильтров и badge активного фильтра | P2 / U03 |
| MP073 | Resume/history и удаление отдельной записи/всего | Есть | Проверить отличие history clear от progress reset и общий router | P2 / U03, L02 |
| MP074 | Виджет обновлений | Добавить | Дополнить существующий Yomu continue widget отдельной поверхностью новых глав | P3 / U03 |

## 5. Загрузки и хранение

Основа: upstream `DownloadPreferences`, `DownloadQueueScreen`, [Downloads FAQ](https://mihon.app/docs/faq/downloads). Yomu: `DownloadRepository`, `Downloads`, `AppSettings`, `AudiobookDownloader`.

| ID | Возможность | Yomu сейчас | Что делать | Приоритет / задача |
|---|---|---|---|---|
| MP075 | Очередь, pause/resume/retry/cancel | Есть | Единое состояние chapters/audio, процесс/перезагрузка/уведомления | P2 / DL01 |
| MP076 | Reorder/группы и сортировка очереди | Частично | Приоритеты есть; drag reorder, source/title groups, fairness | P2 / DL01 |
| MP077 | Лимиты источников и страниц в параллели | Есть | Разделить настройку источников/страниц, проверить реальные лимиты | P2 / DL01 |
| MP078 | Wi-Fi-only downloads | Есть | Проверить ручные и auto задания, stream/prefetch policy отдельно | P2 / DL01, S02 |
| MP079 | Следующие N/непрочитанные/все | Есть | Согласовать порядок и duplicates, не повторять реализацию | P2 / M01, DL01 |
| MP080 | Auto-download new + category include/exclude | Частично | Общая автозагрузка есть; добавить политики полок/непрочитанного | P2 / DL02 |
| MP081 | Auto-download while reading | Есть | Проверить chapters-ahead, сеть и восстановление очереди | P2 / DL01, M03 |
| MP082 | Delete after read/mark read, retain slots | Частично | AutoDeleteRead есть; оставлять N, время удаления, preview | P2 / DL02 |
| MP083 | Защита bookmarked и excluded categories при удалении | Проверить | Полный договор, одинаковый для всех путей отметки | P2 / DL02 |
| MP084 | CBZ export и ComicInfo с датами | Частично | Проверить round trip и сохранение upload date | P2 / DL03 |
| MP085 | Download cache rescan/индекс и смена папки | Проверить | Восстановление индекса, правильные статусы при недоступном SAF | P2 / DL03 |
| MP086 | Коллизии имён, URL hash, Unicode/ASCII policy | Частично | Стабильные IDs внутри, безопасные имена экспорта и миграция путей | P2/P3 / DL03 |
| MP087 | `.nomedia` и отсутствие страниц в Gallery | Проверить | Проверить локальный источник и каталоговые downloads отдельно | P2 / DL03 |
| MP088 | Выбор storage location и управление свободным местом | Есть | Уточнить ownership, оценку места и действия восстановления | P2 / DL02–DL03, A02 |

Совпадение числовых лимитов потоков с Mihon не является целью: Yomu должен учитывать ограничения источника, Android и аудио. Требование — безопасная, сохраняемая и объяснимая очередь.

## 6. Каталог, источники и расширения

Основа: `SettingsBrowseScreen`, `SettingsAdvancedScreen`, source/extension screens, [Extensions FAQ](https://mihon.app/docs/faq/browse/extensions). Yomu: `SourceManager`, `SourceBrowseScreen`, `CatalogScreen`, `ExtensionStores`, `ExtensionManager`, `compat:tachiyomi`.

| ID | Возможность | Yomu сейчас | Что делать | Приоритет / задача |
|---|---|---|---|---|
| MP089 | Popular/latest/search и source filters | Есть | Capability-driven tabs, сохранение filters, актуальные исходники | P1 / S02 |
| MP090 | Global search, источник/язык, скрытие пустых результатов | Есть | Проверить отмену старого запроса и частичный retry, смешанные типы | P2 / S02, L06 |
| MP091 | Pin/hide/language source list | Есть | Согласовать с мастер-подключением и настройкой content preferences | P1/P3 / S01, S06 |
| MP092 | Source preferences без перезапуска | Частично | Применение к активным запросам и корректная инвалидация | P2 / S05 |
| MP093 | Add/remove repository, install/update/uninstall APK | Есть | Улучшить discoverability; пакетный результат, импорт списка | P1/P2 / S01, S05 |
| MP094 | Trust/revoke/signatures и source origin | Частично | Доверие есть; проверить отзыв и binding обновления к original repo | P2 / S05 |
| MP095 | Broken/incompatible extensions остаются обновляемыми | Частично | Проверяемые статусы и действия восстановления | P2 / S05 |
| MP096 | Изменения extension API, legacy compatibility | Частично | Отдельные версионированные adapters и fixtures 1.4/1.6/нового API | P2 / S05 |
| MP097 | Safe/Mixed/18+ и фильтр installed | Частично | Нынешний 18+ переключатель расширить семантическими preferences | P3 / S06 |
| MP098 | WebView/cookies/Cloudflare login | Есть | Проверить source/header isolation, понятный вход и восстановление | P2 / S02, Q04 |
| MP099 | Установка system/private/Shizuku | Частично | System/private уже есть; Shizuku — опционально, без требования к пользователю | P3 / S06 |
| MP100 | Local source и metadata ComicInfo | Есть | Проверка сломанного архива без падения всей папки, dates и rescan | P2 / DL03, N01 |
| MP101 | Guided onboarding и повторный доступ к руководству | Частично | Конкретные гайды подключения книг/аудио, возобновление нужного шага | P1 / S01 |

Mihon не предоставляет готовую книгу или произвольный сайт через сам факт установки приложения. Yomu уже имеет собственные источники книг и аудио; мастер подключения S01–S04 — адаптация под более широкий набор контента, а не обещание, что любое расширение манги сможет читать аудиосайт.

## 7. Трекеры и серверы

Список проверен по дереву upstream `data/track`, а не только по README, который может отставать. Наличие tracker в `main` не означает успешную авторизацию в данной среде. Руководство: [Tracking](https://mihon.app/docs/guides/tracking).

| ID | Возможность | Yomu сейчас | Что делать | Приоритет / задача |
|---|---|---|---|---|
| MP102 | MyAnimeList, AniList, Shikimori | Есть | Реальный вход, refresh, rate limits, даты и pending queue | P2 / T01 |
| MP103 | Kitsu, MangaUpdates | Есть | Реальный вход; native rating/privacy/date capabilities | P2 / T01 |
| MP104 | Bangumi | Добавить | Независимый API adapter, OAuth и модели соответствующих статусов | P2/P3 / T01 |
| MP105 | Hikka | Добавить | API adapter, реальные разрешения и поддерживаемый контент | P2/P3 / T01 |
| MP106 | MangaBaka | Добавить | API adapter, рейтинг и метаданные сервиса | P2/P3 / T01 |
| MP107 | Comick | Добавить | Проверка API и контракта перед интеграцией | P2/P3 / T01 |
| MP108 | Enhanced Komga/Kavita/Suwayomi tracking | Добавить | API progress adapters; OPDS browsing само по себе недостаточно | P3 / T02 |
| MP109 | Автоматическое обновление после чтения | Есть | Проверить chapter resolver, incognito и дедупликацию | P2 / T01, M01, Q04 |
| MP110 | Поиск `id:`, отмена запроса, native score system | Частично | Capabilities по сервису, запросы не путаются, rating round trip | P2 / T01 |
| MP111 | Управление датами/статусом/reread/remote removal | Частично | Не навязывать общую урезанную модель; явный choice удаления | P2 / T01 |
| MP112 | Enhanced/server matching и remote sync conflicts | Добавить | Identity mapping, ручное разрешение reset/rewind, отсутствие sync loops | P3 / T02 |

## 8. Backup, импорт, безопасность и настройки

Основа: upstream `BackupPreferences`, `SettingsAppearanceScreen`, `SettingsAdvancedScreen`, `SettingsSecurityScreen`, [Backups](https://mihon.app/docs/guides/backups) и [changelog](https://mihon.app/changelogs/).

| ID | Возможность | Yomu сейчас | Что делать | Приоритет / задача |
|---|---|---|---|---|
| MP113 | Ручные/автоматические backups, расписание | Есть | Большие библиотеки, место, выбор данных, ротация и отчёт | P2 / B01, B03 |
| MP114 | Восстановление с merge прогресса и категорий | Есть | Reread/reset, remap IDs, повторный merge, missing extensions | P2 / B01 |
| MP115 | Импорт библиотек старого Tachiyomi/Mihon | Есть | Реальные formats/fixtures старого stable и нового main, понятный результат | P2 / B01, Q05 |
| MP116 | Запуск миграций данных с progress UI | Проверить | Не показывать вечный стартовый экран; отчёт и safe failure | P2 / B01, Q05 |
| MP117 | Search settings и разделы в поиске | Есть | Индекс новых опций и пользовательские названия вместо API-терминов | P2 / N02, Q05 |
| MP118 | Light/dark/AMOLED, язык, tablet mode | Есть | Адаптация к ширине окна и визуальная приёмка | P1/P2 / Q02 |
| MP119 | Date/time/relative format и системный 12/24h | Проверить | Один formatter и настройка по системе; не хранить отформатированную дату | P3 / Q05 |
| MP120 | Biometric/PIN/idle lock/secure screen | Есть | Проверить восстановление, read state и background surfaces | P2 / Q02, Q04 |
| MP121 | Hide notification content | Проверить | Privacy policy для audio/download/new chapters и lock screen | P2 / Q04 |
| MP122 | Incognito | Есть | Чёткий общий договор для history/progress/stats/trackers/manual actions | P2 / Q04 |
| MP123 | Debug/crash logs, verbose mode, redaction | Частично | Маскировать секреты при записи, preview экспорта, безопасные stage IDs | P2 / Q04 |
| MP124 | Clear cookies/WebView data, UA/DoH | Частично | Сверить UI и фактический общий network client, scope по источнику | P3 / S06 |
| MP125 | Refresh covers / reset viewer settings / storage cleanup | Есть/проверить детали | Не терять overrides и progress; различать cache и пользовательские файлы | P2 / L06, N02, Q04 |
| MP126 | In-app update / skip release / update progress | Частично | Обновление с checksum есть; сверить skip-version и совместимость установки | P2/P3 / Q05 |
| MP127 | Background activity/notification permission help | Частично | Контекстная диагностика worker/audio, не принудительный обход battery settings | P2 / B03, Q04 |
| MP128 | Статистика, reading time и счётчики | Есть | Проверить двойной учёт/часовые пояса, сравнить показатели и фильтры | P2 / Q03–Q05 |

У upstream обнаружены настройки Firebase/Crashlytics/analytics. Yomu заявляет отсутствие телеметрии. Их не следует автоматически включать ради совпадения перечня настроек: локальная диагностика покрывается Q04, а отправка данных требует отдельного продуктового решения. Эта разница зафиксирована, а не объявлена незаметно устранённой.

## 9. Что уже шире Mihon и должно сохраниться

Это не сравнительное заявление о каждом возможном плагине или форке Mihon. В выбранном ориентире продукт сосредоточен на манге; перечисленные Yomu-сценарии нельзя потерять при адаптации UI.

| Область Yomu | Что сохранять | Доработки плана |
|---|---|---|
| EPUB/FB2/TXT/MOBI/DOCX и PDF | Полноценные текстовые форматы, конвертация, reflow, поиск и аннотации | R01–R02, N02, Q03 |
| Книги и личные каталоги | OPDS, acquisition links, метаданные и локальные папки | D01, S01–S02, N01 |
| Аудио | MediaSession, главы, таймер, offline, RSS/YouTube/LibriVox, resume | S03–S04, A01–A03 |
| Языки | Offline dictionaries, saved words, SRS, bilingual, translation/OCR | N04 |
| Редактор | Копия документа, визуальный/source mode, главы, ComicInfo, OCR/typesetting | N05 |
| Синхронизация | Собственные backend, файлы, конфликты и удалённые изменения | B02–B03 |
| Личные данные | Полки, рейтинг, заметки, цитаты, черновики | L04–L05, D02, B01–B02 |

По официальному Library FAQ у Mihon нет встроенной межустройственной синхронизации, предлагается backup/restore. Поэтому синхронизацию Yomu следует дорабатывать как собственное преимущество, а не подменять регулярным восстановлением архива. [Источник](https://mihon.app/docs/faq/library).

## 10. Как закрывать матрицу при реализации

1. Перед задачей открыть текущий HEAD Yomu и проверить, не появилась ли функция после даты матрицы.
2. Сверить конкретный сценарий со стабильным Mihon; для функций только `main` указывать этот статус отдельно. Если upstream за это время изменился, сохранить дату и commit нового сравнения.
3. Реализовать собственный сценарий Yomu, записать изменения состояния, ошибки, миграции и условия доступности. Не переносить автоматически все переключатели manga UI в текстовую/аудиочиталку.
4. Для каждой строки после реализации добавить поля: `commit`, `automated evidence`, `device evidence`, `limitations`. До фактической проверки оставлять соответствующее поле открытым.
5. «Есть» закрывать приёмкой, «Частично» — доработкой и приёмкой, «Проверить» — сначала исследованием. Не реализовывать вторую очередь, второй wheel или второй набор quick settings только из-за строки матрицы.
6. В конце пройти остаточные группы preferences/screens upstream и главные сценарии руками. Новые обнаруженные функции добавить следующими MP129+, а не заявлять заранее, что строк больше быть не может.
7. Любые осознанные исключения из полного паритета, включая analytics или экспериментальный renderer, перечислять отдельно с причиной. Не менять открытую строку на «готово» только потому, что её решили отложить.

### Критерий полного сравнительного прохода

Все строки имеют проверенный итог; нет неназванных различий по library/chapter list/reader/updates/upcoming/downloads/browse/extensions/migration/trackers/backup/history/statistics/settings. Сохранены особенности Yomu для книг, PDF, языка и аудио. Полнота относительно будущих upstream-изменений не обещается.

## 11. Первичные источники для продолжения

Все ссылки на код ниже зафиксированы на одном commit, чтобы сравнение можно было воспроизвести:

- [LibraryTab](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt).
- [LibraryPreferences](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt).
- [ReaderPreferences](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt).
- [DownloadPreferences](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/domain/src/main/java/tachiyomi/domain/download/service/DownloadPreferences.kt).
- [DownloadQueueScreen](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/app/src/main/java/eu/kanade/tachiyomi/ui/download/DownloadQueueScreen.kt).
- [UpdatesPreferences](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/domain/src/main/java/tachiyomi/domain/updates/service/UpdatesPreferences.kt), [UpcomingPreferences](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/domain/src/main/java/tachiyomi/domain/upcoming/service/UpcomingPreferences.kt).
- [UpdatesTab](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/app/src/main/java/eu/kanade/tachiyomi/ui/updates/UpdatesTab.kt), [HistoryTab](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/app/src/main/java/eu/kanade/tachiyomi/ui/history/HistoryTab.kt).
- [SettingsBrowseScreen](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsBrowseScreen.kt).
- [SettingsAdvancedScreen](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt).
- [SettingsSecurityScreen](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsSecurityScreen.kt), [SettingsAppearanceScreen](https://github.com/mihonapp/mihon/blob/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAppearanceScreen.kt).
- [Дерево трекеров](https://github.com/mihonapp/mihon/tree/4823ba69ca65fa707fb89406f3a4c43c9e0053dd/app/src/main/java/eu/kanade/tachiyomi/data/track).

Архитектура личных серверов и новых трекеров перед кодированием должна быть сверена с официальными API самих сервисов. Наличие адаптера в другом приложении не гарантирует актуальность его API и не заменяет проверку.
