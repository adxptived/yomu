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

### S06 — источники и сеть (`IN_PROGRESS`)
- [ ] DNS-over-HTTPS (сейчас есть только настраиваемый User-Agent).
- [ ] UI области действия «очистить cookie / WebView».
- [ ] Shizuku как опциональный установщик: capability detection, fallback на приватный установщик.

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

### U01 — политика обновлений (`IN_PROGRESS`)
- [ ] per-title override/schedule.
- [ ] picker исключённых полок.
- [ ] persisted job result/counts (сейчас результат в памяти + уведомление).

### U02 — план проверок (`IN_PROGRESS`)
- [ ] фильтры по полкам и активный индикатор на экране плана.
- [ ] ожидание результата ручной проверки (сейчас воркер запускается, экран перечитывает план).

### U03 — Updates/History (`IN_PROGRESS`)
- [ ] сгруппированные заголовки (модель `HistoryView.group` готова, UI — нет).
- [ ] мультивыбор записей.
- [ ] виджет новых глав: DB-проекция, клики, ограничения фона, пустое состояние без сети.

### DL01 — очередь загрузок (`IN_PROGRESS`)
- [ ] Retry-After/backoff в ретраях очереди (сейчас общий retry и пауза между тайтлами).
- [ ] тесты fairness по источникам и команд из уведомлений.

### DL02 — retention (`IN_PROGRESS`)
- [ ] preview UI: IDs/байты/защищённые исключения до удаления.
- [ ] picker защищённых полок.
- [ ] правила авто-загрузки only-unread/category после новой главы и чтения.

### DL03 — физический офлайн и индекс (`IN_PROGRESS`)
- [ ] rescan/repair индекса по identity.
- [ ] отсутствующий SAF-корень → «временно недоступно», без массового удаления.
- [ ] ComicInfo round-trip, включая upload date; тесты same-name/Unicode/rename/corrupt archive.
- Примечание: стабильные имена (`[chapterId]`) не переименовывать без миграции; `.nomedia` для owned-каталогов уже есть.

### Q04 — приватность и диагностика (`IN_PROGRESS`)
- [ ] остальной аудит incognito по всем entry points (статистика, трекеры, уведомления).
- [ ] typed diagnostics с кодом стадии вместо свободного текста.
- [ ] приватность уведомлений/lockscreen.
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

### N02 — настройки внутри читалки
- [ ] реестр options по engine/mode и текущий scope (book/global); preview/search/presets/undo сохранить.
- [ ] явный scope control, reset override = наследовать; quick first, advanced collapsed.
- [ ] реальные preview text/PDF crop/spread, Cancel/Undo не сохраняют draft.
- [ ] TTS sample voice, ограничения text-layer/OCR PDF; тесты book/global, process restore, fontScale2.

### N03 — E-ink/доступный пресет
- [ ] пресет без анимации/контраст/размер с Undo, не отдельный engine.
- [ ] optional software flash каждые N физических страниц (не на каждый slider tick), off по умолчанию.
- [ ] vendor refresh только по подтверждённому API, иначе fallback; тесты transition counts, night mode, TalkBack.

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
