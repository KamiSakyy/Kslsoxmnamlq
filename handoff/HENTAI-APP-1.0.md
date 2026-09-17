# Tsuyu H 1.0 — отдельное хентай-приложение

Отдельное приложение (applicationId `com.tsuyu.hentai`, имя «Tsuyu H»), ставится рядом с Tsuyu.
Каталог — только хентай, карточки обогащаются Shikimori (rating RX / R_PLUS), видео только с 4 источников.
Показывается ТОЛЬКО тот хентай, чей первый файл реально отозвался на проб (8KB-проба: ftyp для MP4, #EXTM3U для HLS).

## Архитектура
- Flavor `henta` в `yoru-android/app/build.gradle` (main flavor не тронут, Tsuyu 1.12 собирается как раньше).
- Весь движок — `app/src/main/java/com/tsuyu/line/HentaiEngine.java` (+ мосты `hentaText`/`hentaShiki` в ApiRepository).
- Точка входа в хентай-режим прозрачна: в flavor `henta` любой source в `catalog()`/`allCatalog()` перенаправляется в `HentaiEngine`; UI менять не пришлось.
- id аниме = цифра сайта + id на сайте: 1=hentasis, 2=allhentaii, 3=hentakli, 4=porncado. URL страницы хранится в `Anime.hentaUrl`.
- Кэш страниц — таблица `henta` в YoruCache (24 ч).

## Источники (разобраны через CI-recon, данные в `handoff/recon/`)
1. **hentasis1.top** — страница `/id-slug.html`, в JS массив `file:"https://svt01.hentasis1.top/hentasis2025/<vid>/<Title>-NN_rus_(www.Hentasis.top).mp4"`. Прямые MP4, `Accept-Ranges: bytes`, 200 OK (проверено HEAD). Озвучка/Суб = `_rus_` / `_sub_`.
2. **allhentaii.fun** — каталог `/2d/page/N` (1094 позиции), страница → `file:"/pl/<cat>/<vid>/playlist.txt"` → JSON `[{title,file}]` → прямые MP4 на `cdn.allhentai.fun` (200 OK проверено).
3. **v6.hentakli.org** — страница → `RalodePlayer.init({...})` JSON (серии id) → `/video.php?id=N` → `hencdn.top/video/M` → player-страница с `<source src="https://deu4h1t.storage-hencdn.com/hls/.../index-v1-a1.m3u8?token=st=..~exp=.." res="720" label="720p">`. **HLS с токеном (24 ч)**; m3u8 тянется заново при каждом открытии (токен всегда свежий), качество берётся из атрибута `res`.
4. **porncado.com** — WordPress, `/8-hentai/page/N`, страница → `<source src="https://xcdn1.nosofiles.com/.../output_<uuid>_high.mp4?verify=...">` (signed, expires). Качества high/low. Без `verify` = 403 (проверено) → URL берётся из свежего HTML.

## Проверенные факты (HEAD-пробы из CI)
- svt01.hentasis1.top MP4: 200, video/mp4, 117MB, accept-ranges ✓
- cdn.allhentai.fun MP4: 200, 129MB, accept-ranges ✓
- xcdn1.nosofiles.com без verify: 403 ✓ (нужен fresh signed URL)
- hencdn.top/video/157: 200 text/html (player), внутри signed m3u8 720p ✓
- Шикимори REST: `rating=erotic` = 422 (невалидно); валидные: none,g,pg,pg_13,r,r_plus,rx. Для карточек используется GraphQL `animes(search:...)` + фильтр rating RX/R_PLUS.
- m3u8/HLS нет нигде, кроме hencdn (hentakli).

## Сборка
- CI `build-apk.yml` собирает 4 варианта: main debug/release + henta release; APK: `apk-output/Tsuyu-1.12-*.apk` и `apk-output/TsuyuHentai-1.0-*.apk`; лимит 2.7MB на каждый release.
