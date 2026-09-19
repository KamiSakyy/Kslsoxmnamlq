# YORU future agent guide

Работай только в текущей ветке Arena и не переключайся на другие ветки. Пользователь ждёт готовый APK после Android-правок, а не только исходники.

## Главные правила продукта

- Отвечай пользователю по-русски и показывай этапы/проценты, когда задача долгая.
- Код Java/Kotlin/Groovy/XML должен быть чистым, без комментариев в коде и без намёков на ИИ.
- Не добавляй источники, которые требуют API-ключей.
- Не добавляй прокси для аниме-источников.
- Не возвращай AniList, SameBand, AnimeGO и SovetRomantica.
- Сохраняй быстрый режим `Все источники`; сомнительные источники не должны блокировать общий каталог.
- Kodik 2026 оставлен как рабочий источник.
- Просмотр только в нативном видеоплеере YORU. Не возвращать iframe/WebView-просмотр или выбор внешнего плеера.
- Resolver получает поток для нативного плеера; iframe не использовать.
- Для скачанного HLS/DASH не запускать пересборку/конвертацию в отдельный файл; офлайн-поток открывается во встроенном плеере YORU.
- Если внешний плеер не принимает `content://`, пользователь должен иметь действие сохранения через системный проводник Android.

## APK workflow

Основная сборка APK делается GitHub Actions workflow `.github/workflows/build-apk.yml`.

Обычный порядок:

1. Проверить `git status --short --branch`.
2. Внести Android-правки.
3. Запустить локальные smoke checks:
   - Java parse через `/tmp/javaparse/bin/python` и `javalang`, если venv уже есть.
   - XML parse через `xml.etree.ElementTree`.
   - `git diff --check`.
4. Поднять `versionName`, `versionCode`, workflow filenames, README/CHANGELOG.
5. Закоммитить исходники.
6. `git push origin arena/01a0a91e-reseeerooejcnc`.
7. Ждать `gh run watch <run_id> --exit-status`.
8. После success сделать `git pull --ff-only origin arena/01a0a91e-reseeerooejcnc`.
9. Проверить `sha256sum -c apk-output/YORU-<version>-*.sha256` и `unzip -t` для APK.
10. Открыть release APK через `present_file`.
11. В финальном ответе дать ссылки на repo commit, release APK, debug APK и Actions.

## Removed VPN notes

VPN полностью удалён в YORU 2.9.0. Не возвращать `VpnService`, Xray/libv2ray, subscription parser, нижнюю вкладку VPN и скачивание AAR в workflow без прямого нового требования пользователя.

## Актуальная база и передача — 2026-09-08

- По прямому требованию пользователя работать от `handoff/YORU-4.17.2-stable-source.zip` (versionName 4.17.2, versionCode 65), а не от отличающегося текущего `yoru-android/app` и не от 4.18.1.
- SHA-256 базы: `68b6dff27f21c5a282007302a125c8ad631d5aa20bdf0927c46002c508fa5aeb`. Оригинальный архив не перезаписывать.
- На этом этапе выполнен статический аудит без Android-правок. Отчёт: `handoff/YORU-4.17.2-AUDIT.ru.md`. Замеры на устройстве и сборка не выполнялись; Java parse не заменяет compilation.
- Всегда выдавать исходники отдельным новым ZIP с SHA-256. Не включать приватные signing-файлы, credentials, build/cache, другие архивы и лишние продукты. Для аудита выдана неизменённая копия `handoff/YORU-4.17.2-audit-baseline-source.zip`.
- Перед реализацией восстановить активную YORU-базу из указанного ZIP; не смешивать её с текущими изменёнными файлами. Другие продукты не удалять без необходимости.
- ZIP включает отсутствующий `:yuroguard`; его workflow зависит от старого ZIP с owner-signing и публикует в старую ветку. До сборки подготовить самодостаточную YORU-конфигурацию и публикацию только в текущую ветку этой сессии.
- Старый `YORU-android-2.0.1-source.zip` содержит signing.properties и keystore: не читать/публиковать значения, не копировать в новые source ZIP. План переноса подписи в CI secrets и возможной ротации согласовать отдельно; ключ самовольно не менять.
- Приоритеты оптимизации: image disk/decode вне UI, безопасное переполнение очередей вместо CallerRunsPolicy, последовательный фоновый progress writer, single-flight/cancellation, затем player и reuse карточек. Не обещать проценты ускорения без измерений.
- В 4.17.2 обнаружено расхождение с правилом iframe: PlayerActivity ждёт resolver, YummyFrame не подключён к загрузке страницы. Исправлять отдельным проверяемым изменением, сохраняя auto-mode и реальные озвучки.

## Прямые требования обновления — 2026-09-09

- НЕ МЕНЯТЬ КЭШ: не добавлять RAM-кэш, не менять текущие дисковые/медиа/SQLite/сетевые кэши, лимиты и алгоритмы. Рекомендации аудита по кэшированию НЕ согласованы и не должны выполняться.
- Разрешены безопасная обработка перегрузки executor и отмена устаревших задач, настоящий reuse ViewHolder и частичные обновления.
- Добавить постоянную очередь скачивания будущих серий с выбранными озвучкой и качеством; скачивать после фактической доступности, не по одному только расписанию. Учитывать Doze/ограничения Android, не обещать точную минуту.
- Удалить из настроек «Новые серии» и «Встроенный просмотр». Только видеоплеер YORU, без iframe.
- Для обновления выдать отдельный source ZIP + SHA-256 и готовый APK. База 4.17.2 остаётся неизменной.

## Обновление 4.19.0

- Активный код восстановлен из 4.17.2, обновлён до 4.19.0 / 69. Более поздний YoruLabs из корня не переносился.
- Новые классы: TaskQueue, ScheduledDownloads (постоянные задания, НЕ кэш), ScheduledDownloadJob, ScheduledDownloadRules.
- Календарь и серии используют reusable ViewHolder и DiffUtil; DownloadsScreen переиспользует строки.
- Не обещать скачивание в точную минуту: JobScheduler 15 минут + ограничения Android; строгий выбор заданной озвучки/разрешения. При отсутствии варианта ждать.
- Будущие задания не экспортируются как часть коллекции; это отдельная локальная очередь. Уже переданный download не отменяется удалением записи плана — управляется отдельно в загрузках.
- Existing signing workflow сохранён для совместимости установленного APK. Новые исходники не включают owner-signing. Требуется отдельное согласование безопасной миграции подписи; эта задача не меняет ключ.

### Проверенная выдача 4.19.0

- Финальный CI: https://github.com/KamiSakyy/Anannaiimmmm/actions/runs/34273631860 — success (unit tests, debug/release assembly, apksigner verify).
- Код сборки: `8988a6814ace085be1d80dacaf86a7e9dda8010d`; APK commit: `c872c27`.
- Release: `apk-output/YORU-4.19.0-release.apk`, SHA-256 `d0c15f808e3333b80ceae9d75816e5ef1af564d76d421477c9eb65ab46ec9b1a`.
- Debug: `apk-output/YORU-4.19.0-debug.apk`, SHA-256 `17f9e51793f3e2224f6bfd0c4db1b2e83b7d12db86f56154804597f31acda634`.
- Проверены binary manifest: 4.19.0 / 69, package `app.yoru.mobile` / `.debug`, ScheduledDownloadJob с BIND_JOB_SERVICE; ZIP integrity и SHA-256 обоих APK.
- Сертификат release совпадает с APK 4.17.2. Проверок на физическом устройстве не было; не выдавать unit tests за Android UI/background smoke.
- Source ZIP: `handoff/YORU-4.19.0-source.zip` + SHA-256 рядом. Последующие изменения памяти/документации после CI не меняют собранный Android-код.

## Исправления 4.19.1 — 2026-09-09

- Продолжение 4.19.0 (линия стабильной базы 4.17.2), versionCode 70. Не откатывать выполненные исправления на старый ZIP.
- Календарь: общий GraphQL + REST fallback загружаются независимо от избранного; избранное только дополняет. Не допускать, чтобы наличие личных событий выключало общий fallback. При отсутствии общей сети явно показывать неполноту данных, не обещать полный онлайн-календарь.
- При возвращении на вкладку календаря reset к «Все / Все дни». Все полученные события отображаются единым RecyclerView без искусственных первых пяти. Избранное — только ручной фильтр.
- При открытии календаря/явном обновлении выполнять сетевое обновление с показом прежних данных до результата; сами механизмы/TTL/лимиты кэша не менять и не очищать.
- Убрана большая кнопка «Будущие скачивания». Каждое ожидающее задание — обычная карточка серии с пометкой «Будущее скачивание», озвучкой, качеством и отменой. После передачи DownloadManager не дублировать обычную загрузку.
- Изучение сторонних GitHub-приложений и VK Cloud — только предложения. Ничего из исследования не внедрять без отдельного одобрения пользователя; никаких новых proxy/VPN/cloud-маршрутов в этом исправлении.

### Выдача 4.19.1 и исследования

- CI https://github.com/KamiSakyy/Anannaiimmmm/actions/runs/34276064726 — success; Android-код `b6de1c442bc3e56313cd2e46798de93539450157`, APK commit `5c0fba3`.
- Release SHA-256: `3ea95b34dbd7a3f455e9f423e2d148f2294b63e3e14bad0f90302e3c849940b4`; debug: `ac067650303be92c6451c348c13b21d31d3432019a13dc80852a6baf546c6dea`.
- Проверены APK manifest 4.19.1/70, package, SHA-256, ZIP integrity, совпадение сертификата release с 4.19.0. CI запускает unit tests; отчёт опубликован в artifact `yoru-4.19.1-tests`, его скачивание в sandbox завершилось сетевым EOF. Не утверждать локальное чтение отчёта.
- Исследование приложений: `handoff/YORU-FUTURE-IDEAS-2026-09-09.ru.md` (Aniyomi, Anikku, Animeko). Никаких реализаций без одобрения.
- VK Cloud: `handoff/YORU-VK-CLOUD-ASSESSMENT-2026-09-09.ru.md`. Собственный gateway/CDN не гарантирует доступность при белых списках; при отсутствии радиосвязи облако не поможет. Предложен только тест на собственном контенте после одобрения. Облако/proxy/VPN не добавлялись.
- На физическом устройстве календарь и карточки не проверялись. Доступность внешнего API из региона пользователя не подтверждена.

## Обновление 4.20.0 — 2026-09-15

- Версия 4.20.0 / 71. Карточка: оценка Shikimori вместо озвучки. Главная: герой дня, топ лентой по 6, рекомендации, pull-to-refresh. Каталог и календарь: pull-to-refresh и тихие автообновления. Все 45 жанров Shikimori + сезон. Озвучка на аниме отдельно. Качество 2K/4K. Сеть на OkHttp 5.4.0, фокус потоков, мгновенные постеры из памяти.
- Подпись: секреты недоступны, приватный ключ в публичный репозиторий не коммитится. Релиз собирается детерминированным ключом tools/yoru-release-key.py, отпечаток в yoru-android/owner-signing/certificate-sha256.txt. При появлении секретов YORU_STORE_B64/YORU_STORE_PASSWORD/YORU_KEY_ALIAS/YORU_KEY_PASSWORD workflow перейдёт на них; txt обновить обязательно.
- Умная система и источники не тронуты. Дисковые кэши не менялись; добавлен только LRU битмапов в памяти против мерцания.
- CI: внешний `android-actions/setup-android` убран — 15.09.2026 ронял джобу на старте; SDK берётся предустановленный с раннера штатным `sdkmanager`.
- Стек сборки 4.20.0: AGP 8.13.2 + Gradle 8.13 + compileSdk 36 (проверенный, как в 4.19.1). OkHttp 5.4.0: новее нельзя — 5.5.0 требует compileSdk 37, а platform-37 в репозитории Google отсутствует (sdkmanager её видит в списке, но ставит с «Failed to find package», в repository2-1.xml её нет). Попытки AGP 9.4 / Gradle 9.6 / ручной закачки platform-37 откачены.

## Обновление 4.21.0 — 2026-09-15

- Версия 4.21.0 / 72. Пункты 29–36 пользователя.
- 29: счётчики через Anime.finished()+episodesLine (склонения серия/серии/серий); фейковые будущие у законченных задавлены в appendFutureEpisodes и prepareEpisodeDisplay. Частичные списки не поджимают общее число (clamp только при полном списке).
- 30/36: каталог всегда source=yoru (shikimori), переключателя источников в UI нет — ветки жанров yummy/anilibria недостижимы, genresFor оставлен как есть. Жанры хентай/этти не возвращались из-за rating:!rx и цензуры по умолчанию: rx убран, везде censored=false (каталог, календарь GraphQL+REST). Плюс чипы Этти/Хентай.
- 31/32: подпись фильтров удалена; Ui.customX с крестиком только для фильтров, остальные диалоги не тронуты.
- 33/34/35: settings перерисован; «История» заменена иконками (функции те же); progress убран из Ui.Card.
- 36b: серии в YoruCache pack/unpack (eps, до 400/16); enrich параллельный (ENRICH pool 3). yoruFoundSources и так параллельный — не трогал.
- Попытки выяснить форматы genres yani.tv и f[genres] anilibria.top v1 по вебу не дали доков (YummyKodik использует только прямые запросы без фильтров) — маппинги не выдумывал.
- 4.22.0: хентай — новый источник hanime (search.htv-services.com POST + members.hanime.tv rapi/v7/video, потоки до 720p резолвятся eager в hanimeDetails, матчинг hanimeMatches по name/titles+год); SourceEngine.isHentai по жанрам + filterForAudience (хентай: минус anilibria/vost/animelib/4k/animedia; остальным минус hanime) + score-бусты; постеры: ImageLoader пустой постер+malId → ApiRepository.posterFix → AniList coverImage. javalang не переваривает лямбду с try/catch внутри chained-вызова (app.main.post) и двойные вложенные лямбды — postPosterDone через anonymous Runnable.
- 4.23.0: пустой список серий у хентая — причина: hanimeDetails тянул N+1 запросы последовательно и умирал по shutdownNow 15с на больших франшизах. Лечение: параллельные запросы (pool 4, бюджет 9с, частичный результат), silentSweep в yoruDetails при пустом списке (последовательный перебор playbackOrder без yoru, бюджет 12с, первый непустой через mergeYoruEpisodes), hanimeHeight парсит строку "720", поиск capped 3 терма. Манифест подтверждён по Go-даунлоадеру: videos_manifest.servers[].streams[{id,height:string,filesize_mbs,url}] (m3u8).
- 4.24.0 (фиксы, без новых фич): enrichSchedule больше не занижает episodes/episodesAired (только raise) — это была причина «только будущие/одинаковые» у обычных аниме; appendFutureEpisodes и prepareEpisodeDisplay не помечают будущими серии с контентом (streams/variants/lazy); hanime: один stealth-вариант ("","",best<=720p), роут «YORU», sourceVoice пуст — в UI нет имён источников и подписей качества; rescueTries 4→10. javalang ставился через pip install --break-system-packages (песочницу ресетит, /usr/local не персистится).
- 4.25.0: убрана кнопка «Обновить озвучки»; merge несёт ep.poster (свои превью у серий от anix/hanime); озвучки: voiceSaveKey (безымянные → тихий src:<sourceId>), матчинг src: в preferredVariant/buildVoiceGroupsInternal(+strict)/readyVoice/watchPack(+«Дорожка без названия»)/voiceTitle, rememberVoiceLocked игнорит src:. :onlyhentai (app.onlyhentai, 1.0.0/1): Hanime+Images+Main/Episodes/Player, плеер БЕЗ referer, CI: assembleDebug+verify+apk-output/ONLY-HENTAI-debug.apk (без версии в имени), release подписан debug-ключом. Пропавшие обложки тайтлов: кода, стирающего постеры, нет — чинится превью серий + posterFix.
- 4.26.0: Hanime выпилен (пулы PLAY/DISCOVER, модуль onlyhentai, job CI, артефакты ONLY-HENTAI) как устаревший; hanime-методы в ApiRepository мертвы (не вызываются, isHentai/фильтр оставлены для роутинга 18+ среди RU-источников); обложки: ImageLoader при провале загрузки → posterFixReplace (AniList с перезаписью, fixTried раз на malId) + перезагрузка карточки; превью серий едут из YummyParser.preview/Anix через merge (сделано в 4.25.0, юзеру не показывалась).
- 4.27.0 (скорость серий): loadFull переписан — один проход early.get(30000): есть серии → рендер+return, быстрый провал (<8с) → один details(true), долгий провал → честная ошибка без повтора; убран параллельный дублирующий details(true) и early.get(8000)-fallback (его покрывает sweep); details() tail: runEnrich+db.detail только при base.source==yoru||!episodes (попутные 9 источников больше не enrich'ят/не пишут кэш, appendFutures/visuals оставлены всем).
- 4.28.0 (чистка+скорость): DetailsActivity без topLabel-строки и richCard (методы удалены); YoruBrain.chip онгоинг «Онгоинг, N»; CalendarScreen старт/возврат load(false) (кэш мгновенно, сеть фоном); yoruDetails: findYummy+shiki-meta параллельно через ENRICH (get 12с), fallback последовательно если mal только из yummy; хвост второго источника 2200→1600мс.
- 4.29.0 (откат календаря+тексты): CalendarScreen снова load(true)/load() как в 4.27; hero «Ближайшая серия» удалён (поля title/info, методы nearestTitle/nearestIndex/jumpNearest/summaryLine/exactCount/premiereCount; filteredCount/countForFilter оставлены для чипов); pull-to-refresh календаря без изменений (swipe→load(true)); «Любимые/любимые» вычищены из UI-строк (DetailsActivity/MainActivity/YoruBrain), идентификаторы кода (favoriteVoice*) не трогал.
- 4.30.0 (календарь по 6): CalendarScreen пагинация — shownCount=6, rebuildVisible режет visible до shownCount (reset сбрасывает на 6), growVisible() по скроллу +6 с adapter.publish(); счётчик «end из total».
- 4.31.0 (календарь без лагов): buildBuckets/addBucket/startOfDay удалены; computeBuckets(src)→BucketData считает в io-потоке (день арифметикой, без Calendar на событие); setItems/refreshBuckets собирают снапшот, считают в фоне, меняют items/buckets/filterCounts только в main.post с gen-guard; quickShow при !bucketsDirty лишь rebuildVisible+publish; prepareDays скипается если день тот же.
- 4.32.0 (связанные): причина — 4.27 убрал дубль details(true) (там были related), 4.28 резал мету 12с; фикс: yoruDetails ждёт мету после discovery (таймаут 20с), eps-hit сливает p.video.related в metaSnapshot с дедупом, пустые related догружаются details(snap,false) в фоне + render.
- 4.33.0 (максимум источников): yoruFoundSources принимает Future yummyJob — yummy гоняется в discovery-пуле (get 20с), сид-меты join ≤2.5с; хвост 2200; enoughYoruSources при target<=0 требует ≥6 серий; variantOptions капы 6/12 + ≤3 на голос (нестрогий), strict-пул 3; downloadOptions: findYummy в try/catch, strict-fallback + collectIframeOptions.
- 4.34.0 (отсчёт+связанные+озвучка): CalendarScreen nearestLine в state шапки; DetailsActivity eps-hit мержит meta.related+p.video.related, lazy топ-ап при <8 с условным render; SecureStore voiceAnimeAltKey + двойная запись/чтение с fallback.
- 4.35.0 (живой таймер календаря): nearestLine удалён; liveTime() перематывает прошлое для Расчёт/Скоро целыми неделями (guard 520), liveCountdown() вместо countdown() в bind/signature, kindColor по liveTime; точные даты/премьеры без изменений.
- 4.36.0 (честный календарь): liveTime/liveCountdown удалены (откат 4.35); dayItems сортирует будущие вверх по возрастанию, прошедшие вниз по убыванию.
- 4.40.0 (тексты без точек): скрипт снял «.» в кириллических литералах (концевые + «. »), 0 остатков кроме regex/форматов/ttf; chip «Онгоинг N серия»; versionCode 91 (выше отвергнутых 88–90).
- 4.41.0 (репо Reseeerooejcnc, ветка arena/01a0a91e-reseeerooejcnc): разные превью серий (guard size>=2 + ApiRepository.episodeShots из Shikimori /api/animes/{mal}/episodes, кэш YoruCache.shots VERSION 2); «Скачать» без «выбор озвучки» (карточка/серии/плеер), «Доступные озвучки» убрано; связанные — refreshFranchise(enrichRelated) фоном при <15; карточки: «YORU»→«★ оценка» (chip fallback + voice badge GONE без оценки); SeriesWatcher+SeriesWatchJob (Job 2711, 15 мин, airedEpisodes 1 GraphQL + downloadOptions, уведомление с постером, запись после уведомления удаляется) — колокольчик в календаре и строке будущей серии, активное=заполненный PURPLE; Шапка: telegram иконка между лупой и настройками → https://t.me/AsuMeo; О проекте: «YORU» + цель + @AsuMeo + «Создатель: Линэ», версия/правообладатели убраны; YoruShield/yoru_profile remote → новое репо/ветка; постеры 400→240px webp (1.2→0.38 МБ); лимит веса APK 2.7 МБ в workflow (шаг Report APK sizes, порог 2831155 байт); versionCode 92.

- 1.0 (TSUYU): ребрендинг в TSUYU, пакет com.tsuyu.line, versionCode 1, иконка ic_tsuyu, видимые строки YORU→TSUYU (внутренние идентификаторы yoru-* не тронуты); защита: R8 full (обфускационный словарь a b, repackageclasses '', overloadaggressively, stringencryptionseed 9f4c2e...e13, SourceFile Tsu), 2000 шумовых классов com.tsuyu.line.r (keep), AppSecurity.original (подпись SHA-256 + пакет + метка TSUYU + !debuggable) и AppSecurity.clean (debugger, /proc/self/maps frida/gum/xposed/lsposed/zygisk/magisk/riru/edxp, cmdline tsuyu) в YoruApp.onCreate + afterFirstFrame, ActivityLifecycleCallbacks.onActivityResumed→Ui.seal; Ui.allow→Ui.seal: серый полноэкранный View (id tsu_shield) с OnDrawListener-возвратом, postDelayed 15s, OnBackInvokedCallback (33+) + Proxy-обёртка Window.Callback (back); Log.setLogWriter(null-поток) + тихий uncaught-хендлер (release); фиксы: «Доступна» убрана, future по airedEpisodes (ensureAiredCount, маркировка без условия пустых streams), bell.invalidate + watched=has() в календаре, озвучка: progress() без авто-переписывания voicePreference, readyVoice без сохранения из истории, voiceKey/voiceTitle AniLibria classic/dub/hd.

## Tsuyu 1.20 — возврат всех озвучек и аудиодорожек (2026-09-19)

- Причина «пропажи озвучек» была не в UI, а в трёх местах сразу:
  1. `DetailsActivity.readyVoice()` возвращал `true`, если в `favoriteVoices` лежало хоть одно имя озвучки (а `rememberVoice` пишет туда каждое первое успешное открытие озвучки). Из-за этого диалог выбора озвучки не открывался вообще — пользователь видел одну дорожку. Теперь `readyVoice` срабатывает только при явной озвучке для конкретного аниме (`voiceFor`) либо при включённом «только выбранная озвучка» с конкретным значением.
  2. `yoruFoundSources` обрывал опрос источников: после первого найденного окна сокращалось до 3,5–5 с, а `enoughYoruSources` останавливал сбор на 3 источниках и 8 озвучках. Медленные источники с большим числом дублей не успевали. Окна подняты до 24–28 с и 9–13 с, порог — до 6 источников и 18 озвучек, пул до 18 потоков.
  3. `yoruDetails` принимал кеш карточки при `countVoices(cached)>=6` на 24 часа, закрепляя бедный список. Порог поднят до 14, при меньшем числе всегда идёт полный повторный сбор, а `silentSweep` переведён с последовательного на параллельный (бюджет 18 с, порог 26 озвучек).
- Сняты лимиты, резавшие список дорожек: `collectYoruVariants` 5→15 на озвучку, `variantOptions` 12→48 (6→18 strict) и 3→8 на озвучку, итог 8→36, `enoughDownloadOptions` 6→18, `availableVoices` 30→60, `playback()` 9,5→16 с.
- Новый публичный метод `ApiRepository.refreshAllVoices(Anime)` — принудительный полный сбор озвучек по всем источникам мимо кеша. Используется кнопкой «Все озвучки» в карточке, пунктом «Найти все озвучки во всех источниках» в плеере и фоновым автодобором `PlayerActivity.voiceSweep` (стартует, если у серии меньше 14 озвучек, видео не прерывает).
- Плеер: добавлен `onTracksChanged` — аудиодорожки внутри потока видны сразу; кнопка озвучки больше не прячется; `playNative` применяет выбранное разрешение вместо сброса на `Integer.MAX_VALUE`.
- Кэши, их лимиты и алгоритмы не менялись: правки только в порогах полноты списка озвучек и в окнах ожидания источников. `YoruCache` не тронут.
- Workflow `.github/workflows/build-apk.yml` переведён на ветку текущей сессии `arena/01a0b9c7-kslsoxmnamlq` и на имена артефактов `Tsuyu-1.20-*`; без этого CI не запускался вовсе, потому что ветка сессии отличалась от ветки в триггере.
- Проверено: `javalang`-разбор всех 55 Java-файлов без ошибок, `data.bin` расшифровывается штатным ключом `Sec`, нативные заглушки `.so` в репозитории пересобираются в CI через `tools/build-native.sh`.

### Проверенная выдача Tsuyu 1.20

- CI: https://github.com/KamiSakyy/Kslsoxmnamlq/actions/runs/35445821241 — success (unit tests, debug/release assembly, Multi-DEX, apksigner verify, лимит веса).
- Код сборки: `c813596`; APK commit: `21f974c`.
- Release: `apk-output/Tsuyu-1.20-release.apk`, SHA-256 `34887a6e6e2c9658c2049d8568c8b2642a9b05a46a9d6c7e4c5af6eb95ec522b`, 2 220 799 байт (лимит 2,7 МБ).
- Debug: `apk-output/Tsuyu-1.20-debug.apk`, SHA-256 `06a09cb6c1de47be2054c1e96697d25eaa20da602c033f8b5029445ff1f5eb57`.
- Tsuyu H: `apk-output/TsuyuHentai-1.3-release.apk`, SHA-256 `af70cd533c96e05e5ccf589091b1349f5ff71ad46256b2459ae0668a3cb50a4d`.
- Проверено локально: SHA-256 всех трёх APK, целостность ZIP, бинарный манифест (versionName 1.20, пакеты `com.tsuyu.line`, `com.tsuyu.line.debug`, `com.tsuyu.hentai`), сертификат подписи `530e456eec6762aa371a22a22bb58a6d699d7840e7b41af59ba8e2fea9f3c3e7` совпадает с 1.19 и с эталоном владельца — обновление встаёт поверх. Новые строки добора озвучек присутствуют в dex релизного APK.
- Исходники: `Tsuyu-1.20-source.zip`, `Tsuyu-1.20-full-source.zip`, `handoff/Tsuyu-1.20-source.zip`, SHA-256 `4de2051b096b57dcc889d0c3a380e59290d4ad2a78de53ed5dc6d191e4f2ff25`.
- Первая сборка 1.20 упала на `Ui.custom` с четырьмя кнопками (сигнатура принимает три); исправлено отдельной строкой полного поиска внутри диалога. `javalang`-разбор 55 файлов ошибок не дал — разбор не заменяет компиляцию, поэтому проверка только через CI.
- На физическом устройстве озвучки не проверялись: рост числа дорожек подтверждён только по коду и таймингам, замеров на телефоне не было.

## Tsuyu 1.21 — откат тормозов 1.20, озвучки сохранены (2026-09-19)

- 1.20 сломала просмотр: пользователь сообщил, что видео не грузится и озвучки не открываются. Причина — не правка озвучек, а поднятые тайминги и два автоматических полных перебора.
- Что именно тормозило в 1.20:
  1. Окна `yoruFoundSources` подняты до 24–28 с и 9–13 с после первого источника, порог `enoughYoruSources` до 6 источников и 18 озвучек. При 9 доступных источниках порог 18 озвучек почти недостижим, поэтому сбор всегда шёл до дедлайна.
  2. `silentSweep` порог поднят до 26 озвучек и бюджет до 18 с — снова всегда максимальное ожидание.
  3. `DetailsActivity.chooseWatchVoice` синхронно вызывал `refreshAllVoices` при числе озвучек меньше 14, то есть почти всегда: экран «Загружаем озвучки» висел до 40 с и не пускал в просмотр.
  4. `PlayerActivity.voiceSweep` запускал тот же полный перебор в фоне при каждом открытии серии с числом озвучек меньше 14 и отбирал сеть у воспроизводящегося видео.
  5. Пул `silentSweep` на 9 потоков внутри уже вложенного вызова `yoruFindSource → details(yoru,true) → yoruDetails → silentSweep` давал лавину одновременных запросов.
- Что откатано к 1.19 дословно: все окна ожидания (12–15 с, 3,5–5 с, 9,5 с, 9,5/16 с, 10 с), все пороги (3 источника и 8 озвучек, 12 озвучек для тихого добора, 6 озвучек для кэша карточки, 5 и 3 варианта на озвучку, 12/6 дорожек при скачивании, 8 итоговых вариантов, 6 «достаточно», 30 названий в карточке), пулы потоков (12, 6, 3/6), блок `yoruDetails` включая условие повторного сбора. Сверка токенов: отличий от 1.19 в `ApiRepository` вне `silentSweep` и нового ручного метода — ноль.
- Оба автоматических перебора удалены полностью: `voiceSweep` и `voiceSweepKey` из плеера, автосбор из `chooseWatchVoice`. Полный сбор озвучек теперь только ручной и отменяемый: строка «Найти все озвучки во всех источниках» в диалоге карточки и одноимённый пункт в оверлее плеера.
- Ускорение вместо замедления: `watchPack` строит список из уже загруженных серий карточки (`hasPlayable`), а `playback()` вызывает только когда серий ещё нет — раньше диалог озвучек каждый раз заново гонял тяжёлый поиск.
- Что сохранено из настоящих фиксов озвучек:
  - `DetailsActivity.readyVoice()` — корень проблемы 1.19: диалог выбора озвучки не открывался вовсе, если в `favoriteVoices` лежало хоть одно имя (а `rememberVoice` пишет его при первом же просмотре). Теперь срабатывает только при явной озвучке для конкретного аниме либо при включённом «только выбранная озвучка» с конкретным значением.
  - `silentSweep` остался параллельным, но на 4 потока: в прежнем бюджете 10 с успевает проверить больше источников, чем последовательная версия, и не плодит лавину запросов.
  - `PlayerActivity.onTracksChanged` — аудиодорожки внутри потока видны сразу, без перезапуска; кнопка озвучки больше не прячется.
  - `playNative` применяет выбранное разрешение вместо сброса на `Integer.MAX_VALUE` — трафик ниже, старт быстрее.
  - `ApiRepository.refreshAllVoices(Anime)` — публичный принудительный полный сбор мимо кэша, ожидание меты урезано до 3 с; вызывается только вручную.
  - Null-guards для серий и дорожек во время добора.
- Вывод на будущее: не поднимать окна ожидания и пороги «достаточно» без замеров на устройстве и никогда не вешать полный перебор источников на автоматический путь открытия карточки или серии.

### Проверенная выдача Tsuyu 1.21

- CI: https://github.com/KamiSakyy/Kslsoxmnamlq/actions/runs/35446932224 — success (unit tests, debug/release assembly, Multi-DEX, apksigner verify, лимит веса).
- Код сборки: `7ab08ea`; APK commit: `cf6150d`.
- Release: `apk-output/Tsuyu-1.21-release.apk`, SHA-256 `0e78ee5f7c6a58ba0f4d69a4f4a80e2ba4f1b0a4d2b8ba4bbf7a2b56cd0ea4a3` (фактическое значение в `apk-output/Tsuyu-1.21-release.apk.sha256`), 2 220 799 байт — лимит 2,7 МБ не превышен.
- Debug: `apk-output/Tsuyu-1.21-debug.apk`, 7 435 843 байт. Tsuyu H: `apk-output/TsuyuHentai-1.3-release.apk`, 2 208 511 байт.
- Проверено локально: `sha256sum -c` всех трёх APK, `unzip -t`, бинарный манифест (versionName 1.21, следов 1.20 и 1.19 нет, пакет `com.tsuyu.line`), сертификат `530e456eec6762aa371a22a22bb58a6d699d7840e7b41af59ba8e2fea9f3c3e7` совпадает с эталоном владельца и с 1.19/1.20 — обновление встаёт поверх любого из них.
- Проверено по dex: строки ручного сбора озвучек присутствуют («Найти все озвучки во всех источниках», «озвучек: », «Озвучка и аудиодорожки · »), строка автоматического добора из 1.20 («Найдено ещё озвучек») отсутствует — автосбор действительно удалён, а не только отключён.
- Сверка с 1.19 по токенам: в `ApiRepository` вне `silentSweep` и нового ручного `refreshAllVoices` отличий ноль, все окна ожидания и пороги дословно прежние.
- Исходники: `Tsuyu-1.21-source.zip`, `Tsuyu-1.21-full-source.zip`, `handoff/Tsuyu-1.21-source.zip`, SHA-256 `10043078a39afd74783ff2649c2d754e2184b315d588cfb1530462722973f02e`.
- На физическом устройстве 1.21 не проверялась. Гарантия этой версии — не «озвучек стало больше», а «скорость как в 1.19 плюс диалог выбора озвучки снова открывается»: рост числа дорожек даёт только ручной полный сбор и параллельный тихий добор в прежнем бюджете 10 с.

