# YORU 4.41.0 — сборка и ограничения

- Версия 4.41.0 / 92. Ветка `arena/01a0a91e-reseeerooejcnc` (репо `KamiSakyy/Reseeerooejcnc`).
- Требование пользователя: вес APK до 2.7 МБ, сборка через GitHub Actions workflow с Android SDK.

## Что менялось в коде

- Превью серий: общая картина на все серии убрана (guard size>=2 в DetailsActivity.prepareEpisodeDisplay/episodePoster, DownloadHub.episodePoster); новый ApiRepository.episodeShots — скриншоты Shikimori по номеру серии (REST /api/animes/{mal}/episodes), кэш YoruCache.shots (таблица новая, VERSION 1→2, 21 день, keep 240); fillEpisodePosters добирает пустые превью фоном при открытии карточки; офлайн-библиотека пересобирает пустые/дублирующиеся постеры загрузок.
- Кнопки: «Скачать · выбор озвучки» → «Скачать» (DetailsActivity.actions, строка серии, PlayerActivity.downloadButtonTitle); downloadVoiceName удалён; «Доступные озвучки:» из voiceInfoCard убрано.
- Связанные: ApiRepository.refreshFranchise (enrichRelated в фоне) из DetailsActivity.ensureRelated при <15 записей, merge с дедупом по key, re-render.
- Карточки: YoruBrain.chip fallback «YORU» → «★ 8.4» (только при score>0, иначе пусто); Ui.Card.voice badge скрыт при отсутствии оценки; пустые tag/voice — GONE.
- Уведомления о выходе: SeriesWatcher (series-watcher.db, toggle/notify/schedule, Job 2711, период 15 минут) + SeriesWatchJob (JobService: airedEpisodes через 1 GraphQL-запрос, затем downloadOptions, уведомление с постером и кнопкой «Смотреть», запись удаляется после уведомления); иконка-колокольчик в календаре (рядом со скачиванием) и в строке будущей серии карточки; активное состояние — заполненный колокольчик PURPLE; Android 13+ запрашивает POST_NOTIFICATIONS.
- Шапка MainActivity: иконка telegram (новый Ui.Icon "telegram") между search и settings, ACTION_VIEW https://t.me/AsuMeo.
- О проекте: заголовок «YORU», текст про цель и @AsuMeo, «Создатель: Линэ»; версия и правообладатели убраны.
- YoruShield/yoru_profile.json: remote-профиль указывает на текущее репо и ветку.

## Сборка

- Стек как раньше: AGP 8.13.2, Gradle 8.13, compileSdk 36, OkHttp 5.4.0, JDK 17.
- Workflow `.github/workflows/build-apk.yml`: ветка `arena/01a0a91e-reseeerooejcnc`, APK в `apk-output/`, артефакты yoru-4.41.0-*, шаг «Report APK sizes» роняет сборку при превышении 2.7 МБ (2831155 байт) с разбором крупных вложений.
- Офлайн-постеры: 400x571 → 240px webp q50, 1.2 МБ → 384 КБ.

## Ограничения

- Проверка выхода серии в SeriesWatchJob идёт через Shikimori + источники раз в 15 минут с ограничениями JobScheduler/Doze; точную минуту выхода не обещать.
- Скриншоты Shikimori по сериям есть не у всех тайтлов — при их отсутствии превью остаётся placeholder «Серия N» (без общей картинки на все серии).
- Java parse (javalang) — статика, не замена компиляции; финальную проверку даёт CI (unit tests + assemble + apksigner).
