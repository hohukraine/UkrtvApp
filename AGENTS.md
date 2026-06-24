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
