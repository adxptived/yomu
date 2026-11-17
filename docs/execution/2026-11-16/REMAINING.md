# Остаток плана — сводка

Дата: 17 ноября 2026. Основано на [STATUS.md](./STATUS.md) (журнал пакетов) и разделе 7
[AGENT_EXECUTION_PLAN_2026-11-16.md](../../AGENT_EXECUTION_PLAN_2026-11-16.md). Полный канонический порядок — в плане.

## Файлы

- [План исполнения](../../AGENT_EXECUTION_PLAN_2026-11-16.md)
- [Продуктовый план и приёмки V01–V20](../../DEVELOPMENT_PLAN_2026-11-16.md)
- [Матрица паритета MP001–MP128](../../MIHON_PARITY_2026-11-16.md)
- [Журнал пакетов](./STATUS.md)
- [Передача следующему агенту](./HANDOFF.md)

Статусы: `CODE_VERIFIED` — код и доступные автоматические проверки готовы, приёмка (часто аппаратная) открыта;
`IN_PROGRESS` — ядро есть, перечисленный остаток не сделан; `TODO` — не начато; `BLOCKED` — упирается во внешний
сервис/устройство.

## Начато, есть ядро — нужно доделать

### S06 — источники и сеть (`CODE_VERIFIED`, Shizuku `BLOCKED`)
- [x] DNS-over-HTTPS (Cloudflare/Google/AdGuard, per-lookup setting, private names stay on the system resolver, fallback).
- [x] UI области действия «очистить cookie / WebView» (один хост или все сайты + формы WebView; описание до действия).
- [ ] Shizuku как опциональный установщик — `BLOCKED`: в публичном Shizuku API 13.1.5 нет install-вызова, а путь через `IPackageManager` требует hidden-стабов; декоративную настройку не добавляю.

### M02 — развороты, спреды, страницы (`CODE_VERIFIED`, device open)
- [x] rotate-to-fit для широких сканов.
- [x] стартовый зум / smart fit.
- [x] бюджетный tiled decode для очень высоких страниц (бюджетное субдискретизированное декодирование, 12 Мпикс).
- [ ] проверка split-рендера на устройстве (`DEVICE_PENDING`).

### M04 — управление читалкой (`CODE_VERIFIED`, device open)
- [x] вертикальный навигатор (seek slider: коммит по отпусканию, старый прогресс не дёргает ползунок назад).
- [x] настройка скорости анимации (текст и манга).
- [x] документированный fallback ориентации в split-screen (не запрашивается в многооконном режиме).
- [x] повторный вызов подсказки зон по запросу (меню «как управлять» обходит сохранённый one-time флаг).

### U01 — политика обновлений (`CODE_VERIFIED`)
- [x] per-title override/schedule (свой интервал или исключение тайтла, диалог на экране тайтла).
- [x] picker исключённых полок (настройки → каталог).
- [x] persisted job result/counts (`UpdateRunStore` → `update_last_run`, итог на экране плана).

### U02 — план проверок (`CODE_VERIFIED`)
- [x] фильтры по полкам и активный индикатор на экране плана.
- [x] ожидание результата ручной проверки (экран слушает unique work и перечитывает план после завершения).

### U03 — Updates/History (`CODE_VERIFIED`)
- [x] сгруппированные заголовки (DATE/TITLE через `HistoryView.group`).
- [x] мультивыбор записей (долгий тап, отметить прочитанным, загрузить, убрать из истории).
- [x] виджет новых глав: DB-проекция, клики, ограничения фона, пустое состояние без сети.

### DL01 — очередь загрузок (`CODE_VERIFIED`)
- [x] Retry-After/backoff в ретраях очереди (Retry-After сохраняется в `downloads.retryAt`, backoff не сокращается).
- [x] тесты fairness по источникам и команд из уведомлений (второй источник не ждёт всю очередь первого; существующие тесты кнопок дополнены очередью).

### DL02 — retention (`CODE_VERIFIED`)
- [x] preview UI: IDs/байты/защищённые исключения до удаления.
- [x] picker защищённых полок.
- [x] правила авто-загрузки only-unread/category после новой главы и чтения (полки автозагрузки; download-ahead после завершения главы; incognito/data saver учтены).

### DL03 — физический офлайн и индекс (`CODE_VERIFIED`)
- [x] rescan/repair индекса по identity (`[chapterId]`, повторная проверка ownership).
- [x] отсутствующий SAF-корень → «временно недоступно», без массового удаления (`ScanReport`, показ в настройках).
- [x] ComicInfo round-trip, включая upload date; тесты same-name/Unicode/rename/corrupt archive (общий писатель/читатель + Unicode/corrupt; repair на пропавший файл).
- Примечание: стабильные имена (`[chapterId]`) не переименовывать без миграции; `.nomedia` для owned-каталогов теперь реально ставится.

### Q04 — приватность и диагностика (`CODE_VERIFIED`)
- [x] остальной аудит incognito по всем entry points (`IncognitoPolicy` + тесты; уведомления и виджеты скрывают содержимое).
- [x] typed diagnostics с кодом стадии вместо свободного текста (`DiagStage`, стадия по маршруту и в журнале).
- [x] приватность уведомлений/lockscreen (скрытие на экране блокировки; инкогнито убирает заголовки).
- Примечание: маскирование секретов в журнале и запрет автоматической записи прогресса в incognito уже сделаны.

## Не начато (`TODO`)

### M03 — окно онлайн-глав
- [ ] состояние текущей главы и соседей, стабильные ключи entry/chapter/page, один владелец jobs/ресурсов.
- [ ] bounded preload ±1 с сетевыми ограничениями; дальние главы освобождаются.
- [ ] inline retry/skip при ошибке соседней главы; текущая страница не теряется.
- [ ] progress/read/tracker только по фактической видимости; preload ничего не отмечает.
- [ ] anchor: chapter + physical page/offset + внутреннее положение, fallback на сохранённую главу.
- [ ] после manga — ограниченное окно для онлайн-новелл.

### M05 — экспериментальный рендерер
- [ ] зафиксировать конкретный bottleneck и пользовательскую пользу (MP062/MP063).
- [ ] baseline CPU/GPU/память/батарея, методика и условия успеха.
- [ ] opt-in prototype за интерфейсом с обычным fallback; сравнение качества и latency; решение о включении.

### A01 — аудио-управление
- [ ] rewind off/adaptive/custom с миграцией прежнего default.
- [ ] fade, end-of-chapter timer, resume-after-focus по возможностям Media3.
- [ ] один владелец команд (UI/уведомления/headset), тесты pause/seek/speed/timer/focus.

### A02 — офлайн-объём аудио
- [ ] лист all/nextN/selected с именами треков, размером/неизвестно, свободным местом и Wi-Fi.
- [ ] стабильные track IDs, частичная доступность отдельно от book-added.
- [ ] удаление копий сохраняет streaming reference и прогресс.
- [ ] Range/ETag: не дописывать байты к устаревшему файлу; restart конкретного трека.

### A03 — rich audio и внешние surfaces
- [ ] metadata overrides (narrator/series/language/description), реальная длительность/размер.
- [ ] gain по измеренной громкости без clipping, cache анализа, отключение.
- [ ] аудио-виджет: одни команды, после reboot звук сам не начинается.
- [ ] Android Auto по документации Media3 (browse tree, auth, offline), protocol tests.
- [ ] linked editions text↔audio без обещания точной фразы.

### MG01 — миграция источника
- [ ] fetch/plan/apply; план: identity, revision, chapter matches+confidence, ambiguity/missing, переносимые данные и файлы.
- [ ] preview с отказом/исправлением matching и выбором данных; до apply ничего не менять.
- [ ] apply: проверка revision, DB-транзакция, reversible journal; файлы вне транзакции; старый офлайн доступен.
- [ ] batch с partial report, pause/restart, идемпотентность; тесты ambiguous -1, changed target, undo.

### B01 — backup/restore и importer
- [ ] manifest: версии категорий, отсутствующие optional-секции, counts/space/missing source/conflicts.
- [ ] replacement только явным выбором; remap всех зависимых ID; reset/reread как семантическое событие.
- [ ] streaming decode, лимиты архива, staging/recovery; пароли/токены не включать.
- [ ] Mihon fixtures с версией/происхождением; неизвестный tracker не портит остальные записи.
- [ ] тесты empty/full DB, old schema→current, повторный merge, ID collision, truncated archive, rollback failure.

### B02 — field-complete sync
- [ ] матрица полей (persisted/exported/merged/конфликт/tombstone) для всех объектов.
- [ ] добавить audio position/stable track, bookmarks+notes, user metadata/overrides, subscriptions/settings.
- [ ] unknown fields старого клиента не становятся удалением; reset/rewind/reread не затираются max.
- [ ] idempotent apply, tombstones, защита изменённых файлов; тесты двух изолированных БД + реальный backend.

### B03 — мастер и состояние sync/background
- [ ] state machine backend → input → probe → categories → schedule → preview → apply; без двух sync.
- [ ] один status summary (last success, partial, conflicts, objects/bytes/errors).
- [ ] гайды и recovery по конкретному состоянию; тесты invalid credentials/no permission/offline/conflict/restart.

### T01 — tracker parity по сервису
- [ ] service contract для нового сервиса (официальная документация, дата, API, auth/scopes, mapping, limits, redacted samples).
- [ ] adapter capabilities, native values без потери точности, pending merge, expired token ≠ 5xx.
- [ ] mock contract tests, затем live auth/search/bind/update/logout. Без ключа — live pending, не «проверено».
- Кандидаты: Bangumi, Hikka, MangaBaka, Comick (каждый — отдельный подпакет).

### T02 — enhanced server tracking
- [ ] отдельный контракт на сервер; OPDS endpoint не считать progress API.
- [ ] mapping remote item/chapter/page ↔ local identity, capabilities server-driven, same-server проверка при смене URL.
- [ ] bind/reconcile, offline event queue, conflict actions, origin/revision против бесконечного resend.
- Серверы: Komga, Kavita, Suwayomi. Без сервера — `BLOCKED` для live-части.

### S04 — Audiobookshelf
- [ ] контракт: login/session/refresh, библиотеки/items, chapters/tracks, stream/download, progress, версии, rate limits.
- [ ] stable server+library+item IDs, secure credentials, connection test.
- [ ] browse/search/details → manifest → playback; expiring URL не меняет identity/position.
- [ ] двусторонний progress с loop prevention; offline adapter через A02.
- Без тестового сервера — live gate, не конструировать «совместимый JSON».

### N02 — настройки внутри читалки (`IN_PROGRESS`)
- [x] явный scope control (чипы «Все книги / Эта книга»; возврат к «Все книги» удаляет override = наследовать).
- [ ] реестр options по engine/mode, preview/search/presets/undo и TTS sample; тесты book/global/process restore/fontScale2.

### N03 — E-ink/доступный пресет (`CODE_VERIFIED`, device open)
- [x] пресет без анимации/контраст/размер с Undo, не отдельный engine (`EinkPreset`, один composite undo).
- [x] optional software flash каждые N физических страниц (не на каждый slider tick), off по умолчанию.
- [x] vendor refresh не используется: подтверждённого API нет, остаётся программный fallback.
- [ ] тесты transition counts/night mode/TalkBack на реальном E-ink (`DEVICE_PENDING`).

### N04 — слова, контекст, глоссарий, индекс
- [ ] найти существующие catalog/annotations/translator; не писать второй SRS.
- [ ] точный book+locator контекст и переход в место через L02; missing book/edition → recovery.
- [ ] book-filter words, glossary/character notes с identity и backup/sync; translation retry состояния.
- [ ] fulltext/OCR индекс отдельным optional job: estimate/cancel/incremental/rebuild; тесты reflow, deleted book, export.

### N05 — длинные задачи и редактор
- [ ] реестр existing copy/save/undo/validation/translation; новые только для недостающих операций.
- [ ] общий task result (progress/cancel/partial errors/output), ownership temp/workspace, recovery journal.
- [ ] merge/split chapters/volumes, cleaning brush как reversible layer; validate staged → атомарная публикация.
- [ ] оригинал не меняется до save; cancel ≠ completed; тесты kill/no-space/corrupt page/same-name export.

### Q01 — границы компонентов
- [ ] реестр владельцев input/session/selection/query/operations/connections/update policy.
- [ ] убрать временные адаптеры после обновления callers; domain чистый; jobs cancels не проглатываются.
- [ ] тесты cancellation/rotation/disposal; Koin без duplicate bindings.

### Q02 — visual/a11y проход
- [ ] content description/role/state/focus/48dp для новых контролов; TalkBack линейный список wheel.
- [ ] RU/EN coverage, длинные строки; ширины 320/360/411/tablet, fontScale 1/1.3/2, light/dark/AMOLED.
- [ ] поведенческий путь источник→mixed selection→Resume→PDF menu через TalkBack.

### Q03 — perf/memory evidence
- [ ] методика: устройство/OS/refresh rate/build/температура/сеть, fixture library.
- [ ] cold/warm start, first useful page, frame p50/p95/p99, >50ms count, memory steady/peak, IO/network counts.
- [ ] одинаковый сценарий до/после, несколько повторов; исправлять только измеренные bottlenecks; отчёт с limitations.

### Q05 — документация, матрица, выпускная проверка
- [ ] USER_GUIDE/EXTENSIONS/KNOWN_LIMITATIONS/NOT_DONE/PROGRESS/ARCHITECTURE/README по фактическому HEAD.
- [ ] остаточный проход MP001–MP128: implemented/verified/different/blocked; новые различия — MP129+.
- [ ] date/time locale, skip-version и migration progress — только реальные gaps.
- [ ] C-RELEASE + approved visual verification; установка поверх прежней APK без data clear; отчёт о gates. Release signing не подменять.

## Аппаратные и live-гейты (не закрываются без среды)

- `DEVICE_PENDING`: V01–V06, V08, V09, V10, V18 — PDFium-жесты, TalkBack, E-ink, реальные аудиотеги, реальные ленты и аккаунты.
- Позже: V19 (Q03, fast/release на устройстве), V20 (Q04), live-гейты T01/T02/S04 (реальные сервисы/серверы).
- Правило: отсутствие устройства не засчитывается ни как успех, ни как основание объявить функцию отсутствующей.

## Отдельная задача «потом» (по просьбе пользователя)

Убрать упоминания стороннего продукта из репозитория. **Не делать в этом проходе.**
- [MIHON_PARITY_2026-11-16.md](../../MIHON_PARITY_2026-11-16.md) — весь документ; сначала уточнить, нужен ли он.
- `docs/execution/2026-11-16/STATUS.md` — одна строка-ссылка на матрицу, убрать легко.
- `compat/.../NOTICE`, `app/src/main/assets/extensions/NOTICE.txt` — **не удалять** без юридической проверки (Apache 2.0).
- `data/.../backup/*Importer.kt` + тест — отдельным пакетом с миграцией; переименование ломает фичу и 16 тестов.
- Строки настроек, `SourceManager`, extension stores — проверить, что это не публичный контракт API расширений.

## Верификация на текущий момент

- Полный прогон (`assembleDebug testDebugUnitTest :domain:test :source-api:test :sources:opds:test`) зелёный; известный
  флейк — `DownloadedBookTest.downloadedBookStaysReadable`, изолированно проходит.
- Room 14 с миграциями 11→12→13→14 и тестами.
- Журнал по каждому пакету — [STATUS.md](./STATUS.md); передача — [HANDOFF.md](./HANDOFF.md).
