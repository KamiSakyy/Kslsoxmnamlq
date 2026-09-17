# Tsuyu H 1.1 — видео работает

## Проблема 1.0
Карточки в каталоге показывались, но видео не запускалось.

## Корневая причина
Все точки запуска воспроизведения были жёстко привязаны к источнику `yoru`:
- `DetailsActivity.loadFull()` — предварительная загрузка `api.playback(seed, "yoru")`;
- все кнопки «Смотреть»/строки серий — `Ui.openPlayer(this, anime, "yoru", number)`.

Для карточки с `source="henta"` плеер шёл в `findSourceCandidate(input, "yoru")` →
`yoruShell(input)` → поиск хентай-названия на Yoru → пусто → «Просмотр сейчас не вернул серии».
Если ранний поиск у Yoru уходил дольше 8 секунд, детали вообще не догружали серии
(ветка `waited>=8000` → «Серии временно недоступны»).

## Исправления (1.1)
1. `ApiRepository.playback()`: если `input.source == "henta"`, режим принудительно `"henta"`.
2. `DetailsActivity`: ранний плеер и все `openPlayer` передают `"henta"` для henta-карточек
   (остальные источники — как раньше, `"yoru"`).
3. `SourceEngine.playbackOrder()`: для henta — только `"henta"` (без параллельных попыток других источников).
4. `ApiRepository.quickDetails()`: dispatch на `HentaiEngine.quickDetails` (Shikimori RX/R+ карточки).
5. `HentaiEngine.card()/details()`: проб файла больше не отбрасывает карточку (6-секундное окно;
   плеер перепроверяет файл при запуске). Карточка исключается только если файл не найден на странице.
6. `HentaiEngine.catalogBrowsed()`: дедуп по названию в фиксированном приоритете
   `hs → ah → hk → pc` (porncado отстаёт из-за Cloudflare-челленджа на CDN).
7. `MainActivity` (flavor henta): старт на вкладке «Каталог», вкладка «Главная» показывает каталог,
   бренд «Tsuyu H», статус «Собираем рабочие источники…» при первой загрузке,
   пустой каталог показывает причину (note).

## E2E-проверка (CI, реальные запросы приложения)
`handoff/recon/e2e-appflow.txt` — шаг «E2E app flow» в `recon.yml`:
каталог → карточка → страница тайтла → первый файл → Range-проб (как в `VideoResolver.probeUrl`).

Последний результат:
- hentasis1.top: MP4, 8192 B, ftyp ✓
- allhentaii.fun: MP4, 8192 B, ftyp ✓
- v6.hentakli.org → hencdn.top: HLS, m3u8 27 KB ✓
- porncado.com: CDN (xcdn*.nosofiles.com) отдаёт Cloudflare «Just a moment»
  для прямых запросов с datacenter-IP; с телефона (residential IP) обычно проходит.
  В дедупе porncado — последний приоритет.

## Сборка
- run: 35147941040 (коммит 54a943e)
- `apk-output/TsuyuHentai-1.1-release.apk` — 2 517 410 B (2.40 MB)
- sha256: ce78a5d4dc98720c360f77da28a24316e0ce8be8bf0c63d43758823d7c5858e4
- package com.tsuyu.hentai, versionName 1.1, versionCode 2
