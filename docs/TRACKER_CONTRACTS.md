# Контракты трекеров для T01 (подготовка по Mihon)

Подготовка к пакету T01 по правилу плана: **сначала service contract по официальной реализации, потом код**.
Источник — официальный репозиторий Mihon (Apache-2.0), файлы `app/src/main/java/eu/kanade/tachiyomi/data/track/*/`:
[MangaBakaApi.kt](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/track/mangabaka/MangaBakaApi.kt),
[BangumiApi.kt](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/track/bangumi/BangumiApi.kt),
[HikkaApi.kt](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/track/hikka/HikkaApi.kt),
[ComickApi.kt](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/track/comick/ComickApi.kt).
Это не «выдуманные JSON»: адреса и scope взяты из работающих адаптеров. Но **client_id у Mihon свои** и redirect-URI
`mihon://…` — Yomu не может их переиспользовать как есть. Перед кодом нужна регистрация собственного OAuth-клиента
или явный UX «введите свой client id», плюс live-аккаунт для проверки.

| Сервис | Base / API | OAuth | Маппинг и особенности |
|---|---|---|---|
| MangaBaka | `https://mangabaka.org`, API `https://api.mangabaka.org/v1` | `/auth/oauth2`, scope `library.read library.write offline_access openid`, redirect `mihon://mangabaka-auth`; bearer-token в `Authorization` | поиск `GET /v1/series/search`, карточка `GET /v1/series/{id}`, профиль `GET /v1/my/profile`, библиотека `LIBRARY_API_URL`; `tracking_url = BASE_URL/{id}`; score service-native |
| Bangumi | `https://api.bgm.tv`, web `https://bgm.tv` | `https://bgm.tv/oauth/access_token`, authorize `https://bgm.tv/oauth/authorize`, redirect `mihon://bangumi-auth` | grant_type `authorization_code`, client_id и redirect в теле токен-запроса; есть DTO коллекции/поиска/пользователя |
| Hikka | API `https://api.hikka.io`, web `https://hikka.io` | `https://hikka.io/oauth`, scope `readlist,read:user-details` | DTO: read list, manga, pagination, user, OAuth token; bearer-авторизация |
| Comick | API `https://api.comick.dev`, ресурсы `https://api.comick.dev/integrations/v1`, OAuth `https://comick.dev/api/auth/oauth2` | scope `library:read library:write offline_access`, redirect `app.mihon://comick-auth`; обязательна проверка выданных scope | service-native score, DTO library entry / user / search; ошибка `ComickMissingScopesException` при отсутствии прав |

## Что нужно до написания адаптеров

1. Зарегистрировать свой OAuth-клиент для каждого сервиса (или согласовать схему с пользовательским client id) и
   зафиксировать redirect-URI Yomu (`yomu://track-<service>-auth`) в манифесте и в `TrackLoginActivity`.
2. Для каждого адаптера описать capabilities (search/bind/status/score/dates/progress/reread/privacy/delete/pending),
   redacted samples ответов и Limits — по образцу существующих 5 адаптеров в `data/tracking/`.
3. Mock-контрактные тесты на MockWebServer (authorization, refresh, search, bind, update, logout, expired token ≠ 5xx).
4. Live-проверка с реальным аккаунтом. Без неё статус остаётся `LIVE_PENDING` — как и сейчас в
   [PLAN_2026-11-18.md](PLAN_2026-11-18.md) строка T01.

## Почему это не сделано сразу

В Yomu сейчас 5 трекеров (Shikimori, AniList, MyAnimeList, Kitsu, MangaUpdates). Добавление четвёрки выше — отдельные
пакеты T01, каждый со своим контрактом, адаптером, capabilities и mock/live-проверкой. Писать адаптер без собственного
OAuth-клиента значит создать кнопку «Войти», которая заведомо не работает; правила плана это запрещают.
