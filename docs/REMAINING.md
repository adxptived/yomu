# Что осталось доделать

Срез: `main`, релиз `v2.9.4` опубликован (`arm64-v8a`, `x86_64`, `SHA256SUMS.txt`). Аудит 17 ноября закрыт по
коду. Здесь только открытые пункты; шаги, файлы, приёмка и стоп-условия — в [PLAN_2026-11-18.md](PLAN_2026-11-18.md).

## 1. Аппаратные и live-гейты

Не закрываются кодом и тестами; нужен телефон/планшет, реальные аккаунты или сервер.

- [ ] **Установка 2.9.4 поверх 2.9.1** с библиотекой, скачанными главами и настройками; проверить миграцию и чтение.
- [ ] **WebView-сессии:** очистка одного сайта с cookie на `/` и вложенном пути; второй сайт остаётся в системе.
- [ ] **Приватность:** шторка и lock screen для аудио в incognito и при «скрывать на экране блокировки».
- [ ] **Манга:** свайп, аппаратные клавиши, слайдер и номер страницы после single/split/спред, RTL, переходы глав.
- [ ] **E-ink:** вспышка при быстром листании, TalkBack, реальный ghosting.
- [ ] **PDFium/TalkBack:** PDF-жесты, меню страницы, выделение и доступность.
- [ ] **S03:** живая RSS-лента (код есть, проверена только mock/fixture).
- [ ] **S04/T02:** реальный сервер Audiobookshelf/Komga/Kavita/Suwayomi.
- [ ] **Производительность Q03:** замеры на устройстве, fast/release, наборы 1000 книг/10000 online/500+ PDF.

## 2. Код

### Волна 1 — чтение

- [ ] **M03a · манга** (`TODO`) — `ReaderWindow` (chapter±1), `SessionPageSource`, chapter-aware pager, anchor,
  inline Retry при ошибке соседа; локальный цельный EPUB не трогать.
- [ ] **M03b · онлайн-новеллы** (`TODO`) — bounded-документ текущей ±1 главы, identity bridge, anchor по
  chapter+offset; после приёмки M03a.

### Волна 2 — аудио

- [ ] **A02 · офлайн-объём** (`TODO`) — all/nextN/selected, оценка размера/unknown, свободное место и Wi-Fi,
  частичная доступность, удаление выбранного с сохранением stream reference и позиции, Range/ETag.
- [ ] **A01 · управление** (`TODO`) — rewind `OFF/ADAPTIVE/CUSTOM` с миграцией, один command owner для экрана,
  уведомления, гарнитуры и Bluetooth; fade/end-of-chapter только по возможностям Media3.
- [ ] **A03 · rich audio** (`TODO`, подпакеты) — metadata overrides; gain без clipping; аудиовиджет; Android Auto
  (сначала проверить `MediaSessionService` vs `MediaLibraryService`); linked editions text↔audio.

### Волна 3 — данные

- [ ] **MG01 · миграция источника** (`TODO`) — `planMigration`/`applyMigration`, preview с confidence и
  неоднозначностями, выбор категорий, транзакция + reversible journal, batch, идемпотентность.
- [ ] **B01 · backup/restore** (`TODO`) — manifest категорий, merge vs replace, remap ID, streaming/лимиты,
  фикстуры старого и текущего форматов, rollback.
- [ ] **B02 · field-complete sync** (`TODO`) — field matrix; сначала локальные поля (audio position, bookmarks,
  overrides, subscriptions); tombstones; unknown fields не становятся удалением; затем серверные поля после T01/S04/T02.
- [ ] **B03 · мастер sync** (`TODO`) — state machine backend → input → probe → categories → schedule → preview,
  один status summary, recovery по auth/network/storage/battery.

### Волна 4 — внешнее (live-гейты)

- [ ] **T01 · новый трекер** (`LIVE_PENDING`) — service contract по официальной документации, один сервис,
  capabilities, pending queue, mock-тесты, затем live auth/search/bind/update/logout.
- [ ] **S04 · Audiobookshelf** (`LIVE_PENDING`) — контракт, stable server/library/item/track IDs, secure credentials,
  browse→manifest→playback, двусторонний progress, offline через A02; нужен тестовый сервер.
- [ ] **T02 · серверный tracking** (`LIVE_PENDING`) — отдельные контракты Komga/Kavita/Suwayomi, mapping
  item/chapter/page, revisions/loop prevention, live на реальном сервере.

### Волна 5 — язык и редактор

- [ ] **N02 остаток** (`IN_PROGRESS`) — реестр видимых опций по engine/mode, TTS sample, PDF crop preview,
  fontScale 1/1.3/2, process restore; scope и общие настройки уже сделаны.
- [ ] **N04 · слова/контекст** (`TODO`) — точный book+locator контекст и переход к месту, фильтр слов по книге,
  глоссарий/заметки о персонажах, retry перевода; opt-in fulltext/OCR индекс с estimate/cancel/rebuild.
- [ ] **N05 · длинные операции** (`TODO`) — общий task-контракт (progress/cancel/partial errors/output), recovery
  journal, merge/split глав/томов, cleaning brush, staged validation и атомарная публикация.

### Волна 6 — качество

- [ ] **M05 · экспериментальный рендерер** (`TODO`) — только после Q03 baseline и M03: конкретный bottleneck,
  opt-in prototype за интерфейсом, обычный fallback, сравнение качества/latency; нет выигрыша — не включать.
- [ ] **Q01 · границы компонентов** (`TODO`) — owners input/session/selection/query/operations/source
  connections/update policy; локальные выделения по волнам, финальный проход; тесты cancellation/rotation/disposal.
- [ ] **Q02 · a11y/visual** (`TODO`) — 48 dp, semantics, focus order, TalkBack-путь источник→selection→Resume→PDF,
  ширины 320/360/411/tablet, fontScale 1/1.3/2, light/dark/AMOLED; просмотр screenshot, не только запись.
- [ ] **Q03 · perf/memory** (`TODO`) — baseline до M05, методика/устройство/fixtures, cold/warm start, первая
  страница, frame p50/p95/p99, память, IO/network; отчёт с limitations.
- [ ] **Q05 · документация/матрица** (`TODO`) — USER_GUIDE/EXTENSIONS/KNOWN_LIMITATIONS/ARCHITECTURE/README по
  фактическому HEAD, остаточный реестр паритета, установка поверх прежней APK без data clear, финальный отчёт.

### Открытые хвосты

- [ ] **L05** (`TODO`) — экран библиотеки не использует `LibraryQuerySpec`/`SavedQueryShelf` для восстановления
  фильтров и умных полок.
- [ ] **S03** (`LIVE_PENDING`) — только живая RSS-лента; код и mock-тесты есть.
- [ ] **S06 Shizuku** (`BLOCKED`) — в публичном Shizuku API нет install-вызова; декоративную настройку не добавлять.

## 3. Порядок

1. **M03a** — первый срез; после него M03b.
2. **A02 → A01 → A03**.
3. **MG01 → B01 → B02 (локальные поля) → B03**.
4. **T01 → S04 → T02** — по мере появления аккаунтов/серверов; B02 (серверные поля) после них.
5. **N02 остаток → N04 → N05**.
6. **M05** после Q03 baseline; затем Q01/Q02/Q03/Q05.
7. **L05** — в любой волне; **S03/S04/T02** — только при живой среде.

## 4. Уже закрыто — не переделывать

Аудит 17 ноября (2.9.4): S06-очистка, DL03-repair, M02/M04-раскладка, U01/U02-окно, N02-scope, Q04-incognito,
CSS глав. Пакеты: R01–R03, D01/D02, L01–L04/L06, S01/S02/S05, M01/M02/M04, U01–U03, DL01–DL03, N01, N03, Q04.
Robolectric-стабильность закрыта: 4.18.1, CI Java 17/21, 2.9.4 опубликован.

## 5. Вне плана

- Убрать упоминания стороннего продукта из кода/строк/документации — отдельная задача с проверкой совместимости
  импорта; юридические NOTICE не трогать без проверки лицензии.
