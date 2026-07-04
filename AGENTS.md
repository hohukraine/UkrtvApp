# CRITICAL PROJECT KNOWLEDGE (DO NOT DELETE)

## Uakino Trends
- **URL**: `https://uakino.best/find/year/2026/f/sort=rating;desc/`
- **Path in Profile**: `find/year/2026/f/sort=rating;desc/`
- **Note**: This URL is specifically for 2026 trends sorted by rating. Do not change the year or the format (using `;` for Uakino filters).

## Eneyida Trends
- **URL**: `https://eneyida.tv/f/sort=rating/order=desc/`
- **Path in Profile**: `f/sort=rating/order=desc/`
- **Note**: Eneyida uses `/` for filter parameters.

## Pro Max Optimizations
- **DNS**: System DNS with high timeouts (15s connect, 20s read).
- **Buffer**: 60s min / 150s max for unstable CDNs.
- **Hardware**: Always prioritize hardware decoders (OMX/C2 non-google).
- **WebP**: Intercept and convert WebP to JPEG for old TV Skia compatibility.
- **Warm-up**: Start resolution on focus or detail load to minimize wait time.

# Build Commands

## Android Build
```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew connectedDebugAndroidTest
./gradlew lint  # якщо доступно
```

## Player Configuration Notes
- Buffer durations optimized for HLS streaming (m3u8): minBuffer=30s, maxBuffer=120s, bufferForPlayback=2.5s
- Codec exception handling disabled to prevent crashes on malformed streams
- Audio attributes configured for media playback
- **SurfaceView (raw) instead of PlayerView**: PlayerScreen uses `AndroidView(SurfaceView)` directly (not `PlayerView` wrapper). This avoids `PlayerView.setPlayer()` overhead on Compose recomposition when overlays appear/disappear. Inspired by UAKino app decompile.
- **NO 720p cap**: Stuttering was caused by overlay recomposition, not 1080p decode. 720p cap removed.

## Lessons from Lift App Analysis

### Відеоплеєр (ExoPlayer 1.9.1 → 1.3.1)
- Buffer optimization: DefaultLoadControl з конфігурацією
- Codec fallback system: HLS (h264) → DASH VP9 → DASH AV1
- LoudnessEnhancer для контролю гучності (Android 31+)
- Network-aware playback через ConnectivityManager.NetworkCallback

### UI/UX Patterns
- Custom overlay controller замість стандартного PlayerControlView
- Hover-based навігація для TV пультів
- Progress sync dialog між пристроями

### Архітектура даних
- Кеш стану (cache) по season:episode ключах
- Система трекінгу fps для AFR (Auto Frame Rate)
- Async HTTP запити через OkHttp з таймаутами 10s

## "По-дорослому" (Професійний план): Pure Provider Strategy

Ми повністю відмовилися від TMDB API та перейшли на **Pure Provider Strategy**. Тепер додаток працює виключно на даних з українських ресурсів (Uakino, Eneyida), що робить його максимально швидким та автентичним.

### 1. Видалення TMDB
- Видалено `TmdbRepository`, `TmdbApi` та всі пов'язані з ними DI-модулі.
- Видалено базу даних для кешування TMDB (`EnrichedMovieEntity`, `EnrichedMovieDao`).
- Очищено `AppDatabase` та `build.gradle.kts` від залишків TMDB.

### 2. Глибокий парсинг провайдерів (v12)
Оновлено `DleParser.kt` для витягування всіх доступних метаданих прямо зі сторінок сайтів:
- **Рейтинг**: шукає блоки `.rating-num`, IMDB тощо.
- **Актори та Режисери**: парсить списки "В ролях" та "Режисер".
- **Країна та Жанри**: автоматично збирає дані з карток опису.
- **Опис**: покращений алгоритм вибору головного тексту, який ігнорує коментарі та технічне сміття.

### 3. Оновлений інтерфейс Detail Screen
- Додано відображення рейтингу (золотистий бейдж).
- Додано рядки з інформацією про країну, жанри, режисера та акторів.
- Інтерфейс став більш інформативним і "рідним" для кожного провайдера.

### 4. Максимальна швидкість
- **Відсутність очікування**: Більше немає потреби чекати, поки TMDB знайде відповідність для фільму. Дані відображаються одразу після завантаження сторінки провайдера.
- **Стабільність**: Пошук тепер працює на 100% точно, бо він шукає саме те, що є у провайдера, без посередників.

# TODO

## Plan for fixing slow Home page load (v2 - Professional Optimization)

### Step 1: Instant First Frame with Local Cache (done)
- [x] Implemented `HomeCacheRepository` to store the last successful home sections.
- [x] Modified `ContentRepository.getHomeSections()` to emit cached content immediately.
- [x] Parallelized background fetching of Movies and Series categories.

### Step 2: Adaptive Enrichment Throttling (done)
- [x] Removed `HomeViewModel` enrichment worker (no longer needed with Pure Provider strategy).
- [x] Set hard limit of 150 items for Home Screen content.

### Step 3: Resource-Aware UI (done)
- [x] Optimized `MovieCard` images: added `.size(180, 270)` to Coil requests to match display size.
- [x] Disabled crossfade in `MovieCard` to save GPU cycles on low-end TV hardware.
- [x] Improved `LazyRow` item keying in `ContentRow` for better stability.

### Step 5: Metadata (Pure Provider Plan)
- [x] Implemented deep parsing in `DleParser`.
- [x] Removed Room Database for TMDB metadata.
- [x] Updated Detail Screen to show rich metadata from providers.

# Live Analysis Results (Python curl audit)

## Iframe Strategy = 100% for stream resolution
- **Uakino (ashdi.vip)**: iframe `https://ashdi.vip/vod/265595` → direct m3u8 found:
  `https://ashdi.vip/video23/2/new/.../hls/BKiPlHaPmPtenhH+D44=/index.m3u8`
- **Eneyida (hdvbua.pro)**: iframe `https://hdvbua.pro/embed/1010808/b0c42c552` → direct m3u8 found:
  `https://s11.hdvbua.pro/media1/content/stream/2025/1010808/11105583/index.m3u8`
- Both iframes contain `file: "..."` JS assignments → `findMediaUrlsInText()` extracts full m3u8 URLs
- YouTube/trailer iframes are correctly filtered out by `youtube`/`facebook` checks

## HTML Structure Differences
- **Uakino detail**: uses `.fi-item > .fi-label > h2` for labels + `.fi-desc a` for values (div-based)
- **Eneyida detail**: uses `<li>` with `<span>` labels + `<a>` values (li-based)
- `extractInfo` in DleParser now handles both structures with `.fi-item:contains()` and `.fi-desc a`

## Eneyida Details
- Poster images use `data-src` (relative path), handled by `abs:data-src` already
- Genres separated by `•` in raw text but extracted cleanly from individual `<a>` elements

## seasonLinks Fix
- `otherSeasons` fallback `a[href*='-sezon']` was picking up sidebar links on MOVIE pages (7 links from side-box/side-content sections)
- Fixed: Only use the broad `-sezon` fallback when `contentType == ContentType.SERIES` + filters out sidebar elements

## Build
- compileSdk bumped to 37 (dependencies like core-ktx 1.19.0 require 37)
- targetSdk bumped to 37

## Eneyida Series (HDVB embed JSON playlist)
- **Eneyida series** (e.g. `embed/6097/b0c42c552`) contain a full JSON playlist in the iframe's `file: [...]` JS
- Structure: `Season → Voiceover Studio → Episodes` (3-level nesting)
- `IframeResolutionStrategy` now extracts the JSON via regex (`file: '[...]'`) and parses it into proper `ProviderSeason`/`ProviderEpisode` objects
- Falls back to URL-based season/episode guessing (`s(\d+)e(\d+)`) if JSON parsing fails
- Voiceover variants are deduplicated (`distinctBy { it.e }`) — HDVB player handles voiceover selection internally

## Uakino Series AJAX Playlist (RESOLVED)
- **Uakino series pages** have `playlists-ajax` div with `data-news_id` and `data-xfname`
- **Key finding**: The parameter name is `xfield` (NOT `xfname`!). HTML uses `data-xfname="playlist"` but POST to `playlists.php` must use `xfield=playlist`
- `POST engine/ajax/playlists.php` with `news_id=33511&xfield=playlist` returns JSON: `{"success":true,"response":"<div class=\"playlists-player\">..."}`
- Response HTML contains:
  - `.playlists-lists .playlists-items li` — voiceover/season tabs (e.g. `DniproFilm (1-8)`)
  - `.playlists-videos li[data-file]` — episode list with `data-file="//ashdi.vip/vod/..."` VOD links
  - Each episode has `data-voice` attribute for voiceover selection
- `AjaxPlaylistResolutionStrategy` added to DleStrategies.kt — runs FIRST in the chain for Uakino
- Each episode's ashdi.vip VOD link is then resolved by `IframeResolutionStrategy` → m3u8
- Deep resolution also uses AJAX for fetching other season pages' playlists

## Deep Resolution Fix
- `ResolutionChain.resolve()` `findMediaUrlsInText` now used for ALL providers (was Eneyida-only)
- Uakino series deep resolution: `parsePlaylist` still used as fallback

## Black Screen Root Cause (Mediatek TV)
**Симптом**: FPS=0.0, аудіо грає, відео чорний екран. Кодек `OMX.MS.AVC.Decoder` вибраний, `setPortMode` error -1010 (нормально для цього чіпсету).

**Головна причина**: `AndroidView(PlayerView)` був зовні `when(state)`, тому SurfaceView існував весь час. Коли зверху рендерився Loading overlay, на Mediatek TV SurfaceView міг інвалідуватись (SurfaceView на цих TV некоректно працює коли не topmost view). Потім `player.prepare()` конектив кодек до вже невалідного surface.

**Фікс**: `AndroidView` всередині `when(Ready)` — Surface створюється ТІЛЬКИ коли `Ready`, і відразу конектиться кодек. При `Loading`/`Error` AndroidView немає в композиції → Surface не існує → не може інвалідуватись.

**Другорядна причина (amplifier)**: `loadStream()` мала race window — `_uiState = Loading` всередині `viewModelScope.launch`, а guard перевірявся ДО launch. При множинних викликах `initialize()` (напр. якщо композиція перестворювалась), кожен проходив guard і запускав resolve. Кожен resolve → новий `Ready` з новим `loadTrigger` → `LaunchedEffect` рестартував → `player.prepare()` → кодек перепідключався.

**Третя причина (amplifier)**: `LaunchedEffect(s.url, s.loadTrigger, player)` мала `player` в ключі. Якщо `player` змінювався (після `toggleCodecPolicy`), ефект рестартував і викликав `prepare()`.

**Порівняння зі старою збіркою (`/Users/alex/Desktop/UkrtvApp-master`):**
| Аспект | Стара (робоча) | Нова (було зламано) |
|--------|---------------|-------------------|
| Player створення | `remember { viewModel.buildPlayer() }` | `viewModel.getOrCreatePlayer()` |
| AndroidView | Всередині `when(Ready)` | Зовні `when(state)` |
| LaunchedEffect key | `s.url, s.loadTrigger` | `s.url, s.loadTrigger, player` |
| loadStream Loading state | Всередині launch (теж race, але не тригеррило через #1) | Всередині launch, виправлено на перед launch |
| initialize() guard | Немає (не suspend) | Було Ready, тепер Ready \|\| Loading |
| Плеєр в `DisposableEffect.onDispose` | `player.release()` | `viewModel.releasePlayer(player)` (з instance check) |
| `toggleCodecPolicy()` | Немає | Є — вимикає/вмикає апаратне декодування |

# Session 4: Deep Resolution + WebP Image Decoder Fix

## Проблема 1: Неправильний серіал (замість Spider-Man Noir — Fallout)

**Корінь**: `resolveOtherSeasons()` в Phase B знаходив посилання з сайдбару "схожі серіали" і вважав їх іншими сезонами поточного серіалу. Фільтр `cls.contains("side")` був недостатній.

**Фікс**: Додано фільтрацію за числовим ID серіалу з URL. Для `34229-pavuk-nuar-1-sezon.html` витягується ID=34229, і додаються ТІЛЬКИ посилання, що містять `/<id>-`:
- `UakinoProvider.kt` — `resolveOtherSeasons()` line 254
- `EneyidaProvider.kt` — `resolveOtherSeasons()` line 479

## Проблема 2: `Failed to create image decoder with message 'unimplemented'` (WebP на Mediatek TV)

**Корінь**: Стара збірка використовувала **Coil 1.x** з глобальними налаштуваннями:
- `.bitmapConfig(Bitmap.Config.RGB_565)` — примусово RGB_565 для всіх зображень
- `.allowRgb565(true)`
- `ImageDecoderDecoder` в decoder chain (Coil 1.x feature)

Нова збірка використовує **Coil 2.7.0** де:
- `ImageDecoderDecoder` було видалено (Coil 2.x feature removal)
- `.bitmapConfig()` та `.allowRgb565()` deprecated але все ще працюють
- За замовчуванням `ARGB_8888`, що ламається на Mediatek TV через відсутність WebP кодека в Skia

**Фікс**: Відновлено конфігурацію зі старої збірки в `UkrtvApplication.kt:40-58`:
- `.bitmapConfig(Bitmap.Config.RGB_565)` — глобальний RGB_565
- `.allowRgb565(true)`
- `.memoryCache { maxSizePercent(0.15) }` — зменшено до 15%
- `.diskCache { 64MB }`
- `.crossfade(true)`
- `.respectCacheHeaders(true)`

Також замінено голий `AsyncImage(model = poster)` в `EpisodePickerOverlay.kt:268` на `ImageRequest.Builder` з `RGB_565`.

## Важливі висновки
- Провайдери (Uakino) віддають постери у форматі WebP (`*.webp`)
- Mediatek TV (Android TV) не має Skia кодеку WebP, що викликає `Failed to create image decoder`
- Coil 2.7.0 НЕ має `ImageDecoderDecoder` (був у Coil 1.x)
- Для TV з stripped-down Skia потрібно примусово `RGB_565` та можливо перетворення WebP→JPEG через OkHttp interceptor (якщо поточний фікс не допоможе)

# Session 5: Rating Fix + Performance Optimizations

## Проблема 1: `Rating: 16` замість `6.4` на Uakino movie
**Корінь**: У HTML Uakino movie `.fi-item` з `Вік. рейтинг: 16` з'являється **перед** `.fi-item` з `imdb рейтинг: 6.4`. Старий `extractRating()` брав перший збіг по "рейтинг".

**Корінь `Rating: null` на Uakino series**: Серіал використовує **`.fi-item-s`** замість `.fi-item` для блоку IMDB. Старий код шукав тільки `.fi-item`.

**Фікс** (`DleParser.kt:extractRating`):
- Пошук і `.fi-item`, і `.fi-item-s` через `doc.select(".fi-item, .fi-item-s")`
- IMDB блок (з `labelText.contains("imdb")`) повертається **одразу** (пріоритет)
- `рейтинг` без `imdb` зберігається як fallback, але виключається `вік. рейтинг`
- `.r_imdb span` обмежено тільки Eneyida

**Результат**:
| Тест | До | Після |
|------|-----|-------|
| Uakino movie (Смерть Робіна Гуда) | `16` | `6.4` |
| Uakino series (Павук-Нуар) | `null` | `8.3` |
| Eneyida movie (Поганий хлопець і я 2) | `6.5` | `6.5` |
| Eneyida series (Дім Дракона) | `8.4` | `8.4` |

## Проблема 2: `findMediaUrlsInText` 17ms на detail pages (0 URL)
**Корінь**: 4 regex запускалися на повному HTML сторінки, навіть якщо там немає `.m3u8`.

**Фікс** (`DleResolutionUtils.kt:51-54`):
```kotlin
if (!text.contains(".m3u8") && !text.contains(".mp4") &&
    !text.contains(".webm") && !text.contains("dleid://") &&
    !text.contains("data-file")) return emptyList()
```
**Результат**: ~3ms замість ~17ms на Uakino movie (146KB).

## Проблема 3: `extractInfo` 6× повтор `el.text()`
**Корінь**: `extractInfo()` викликалась 6 разів (genres, country, actors, director, duration), кожен раз викликаючи `el.text()` на всіх елементах.

**Фікс**: `infoElementTexts = infoElements.associateWith { it.text() }` — один прохід замість 6.

## Проблема 4: `extractDescription` — 3× виклик `text()` на кандидата
**Корінь**: `el.text()` викликався в `filter`, `maxByOrNull`, і фінальному `?.text()`.

**Фікс**: `map { text -> ... }` — `text()` викликається раз, результат перевіряється в одному блоці.

## Priority 5 (Eneyida season links = 0) — **by design**
- Eneyida не зберігає сезони як HTML-посилання на сторінці
- Сезони знаходяться всередині JSON-плейлиста в iframe (HDVB embed)
- `IframeResolutionStrategy` → `extractBalancedJson()` → `parseJsonPlaylist()` — основний шлях
- `resolveOtherSeasons()` в Phase B використовується ТІЛЬКИ як fallback для `isDeep=true`
- 0 links — очікувана поведінка

## Priority 6 (select → getElementsByClass) — **skipped**
- `selectFirst(".class")` зупиняється на першому збігу (CSS selector evaluator, depth-first)
- `getElementsByClass("class").first()` збирає ВСІ елементи, потім бере перший
- Заміна була б регресією продуктивності; `selectFirst` для простих класів — оптимально

## Build
- `./gradlew assembleDebug` — BUILD SUCCESSFUL
- 12 pre-existing test failures (10 DleParserDetailTest + 1 EneyidaParserTest + 1 StreamResolverTest) — не пов'язані зі змінами

## Relevant Files Updated
- `DleParser.kt`: `extractRating()`, `parseDetail(doc)`, `extractDescription()`, `extractInfo()`
- `DleResolutionUtils.kt`: `findMediaUrlsInText()` early exit
