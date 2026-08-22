# ЗАВДАННЯ: YouTV-стиль для UkrtvApp (лише TV-формфактор)

> **СТАТУС: ВИКОНАНО (2026-08-22).** A/B/C реалізовано + друга ітерація точної відповідності
> метрикам APK: `MainSectionRow.kt` (картки 120×164, ряди 242/224dp, header 16sp), `YouTvHero.kt`
> (title 32sp на 16% висоти, watch 216×56 на 66%, dots), `TvDetailContent.kt` (bottom-anchored,
> кнопки #66000000→brand radius 18, опис у рамці 70/30, рекомендовані 48dp alpha 0→expand).
> Константи — `ui/theme/YouTvDimens.kt`. assembleDebug SUCCESSFUL.

Ти працюєш над Android-застосунком **UkrtvApp** (`ua.ukrtv.app`) у `/Users/alex/Documents/UkrtvApp`.
Потрібно реалізувати 3 фічі за зразком додатку YouTV AndroidTV. **Phone/Tablet-гілки UI не змінювати взагалі.**

---

## 0. Контекст проєкту (обов'язково врахувати)

- **UI:** Jetpack Compose, Material3 + `androidx.tv.material3` (TV-компоненти: `Surface`, `ClickableSurfaceDefaults`, `Border`)
- **DI:** Hilt (`hiltViewModel()`)
- **Навігація:** type-safe Navigation Compose, routes у `app/src/main/java/ua/ukrtv/app/navigation/AppNavigation.kt`
- **Дані:** скрапери (Jsoup), репозиторій `ContentRepository`, `ProviderManager.activeProvider: StateFlow<MediaProvider>`
- **Ключові theme-константи** (`ui/theme/Dimensions.kt`): `GridDefaults.horizontalPadding = 56.dp`, `columnSpacing = 24.dp`, `CardDefaults.posterWidth/Height = 160/240.dp`, `wideWidth/Height = 320/180.dp`, `CardDefaults.focusPanelHeight = 68.dp`
- **Розміри карток:** `ProviderSizes.card(PosterStyle)` — VERTICAL (Uakino) 160×240, WIDE (UAFLIX) 320×180
- **Пристрій-гварди:** `LocalDeviceClass.current` (`DeviceClass.LOW/MID/HIGH`), `LocalIsMediatek.current` — на LOW/Mediatek анімації вимикати (snap або `deviceClass == DeviceClass.LOW` перевірки), це усталений патерн коду
- **Важливий нюанс фокусу** (коментар у ContentRow.kt:403): `onFocusChanged` над clickable НІКОЛИ не спрацьовує — фокус-сайд-ефекти робити через card's `interactionSource.collectIsFocusedAsState()` / параметр `onFocused`

---

## A. Головний екран (TV): перемикання секцій ↑/↓ як у YouTV

### Що залишається БЕЗ ЗМІН
`TopBar`, hero-банер (`Top200SignatureHero` / `HeroCarousel`), `HomeBackground`, офлайн-банер, діалог оновлень, вся `PhoneHomeScreen` (рядок ~581 у HomeScreen.kt), `HomeViewModel` (стани вже є).

### Що міняємо
У `app/src/main/java/ua/ukrtv/app/ui/home/HomeScreen.kt`:
- Функція `TvHomeScreen` (ряд. 143) та `HomeScreenContent` (ряд. 298).
- Замість вертикального `LazyColumn` зі стеком рядів (`ContentRow` × 8) — фіксована структура:

```
Box {
  HomeBackground(...)                       // без змін
  Column(fillMaxSize) {
    TopBar(...)                             // без змін
    if (!isOnline) offlineBanner            // без змін
    Box(weight(1f), clipToBounds) {         // hero займає решту місця
      if (top200Banners.isNotEmpty()) Top200SignatureHero(...) else HeroCarousel(...)
    }
    MainSectionRow(...)                     // НОВИЙ компонент, знизу
  }
  newUpdate AlertDialog                     // без змін
}
```

### Новий файл: `ui/home/components/MainSectionRow.kt`

```kotlin
data class HomeSectionUi(
    val id: String,               // "continue_watching", "watchlist", "trending", "movies", ...
    val title: String,
    val items: List<Movie>,
    val isLoading: Boolean,
    val useLargeCards: Boolean = false,   // тільки trending
    val dismissable: Boolean = false,     // тільки continue_watching
    val categoryKey: String? = null       // "movies"/"series"/... для кнопки «Див. всі»
)
```

Композабл `MainSectionRow(sections, activeIndex, onSectionChange: (Int)->Unit, brandColor, providerHint, onMovieClick, onItemDismiss, onItemFocused, onSeeAllClick: (HomeSectionUi)->Unit, restoreMovie, restoreWindowOpen, onRestoreHandled, onRowFocusChange: (Boolean)->Unit, sharedTransitionScope, animatedContentScope, modifier)`.

Структура:
```
Column {
  Column(graphicsLayer { alpha = sectionAlpha }) {   // fade стосується і заголовка, і карток
    SectionHeader(section.title, brandColor)          // reuse з components/SectionHeader.kt
    Box(Modifier.height(animatedRowHeight)) {
      LazyRow(fillMaxSize, snapFlingBehavior, contentPadding = GridDefaults.horizontalPadding)
    }
  }
}
```

**Секційний fade (точні значення з YouTV `MainVerticalGrid.R1()`):**
```kotlin
val sectionAlpha = remember { Animatable(1f) }
var lastIndex by remember { mutableIntStateOf(activeIndex) }
LaunchedEffect(activeIndex) {
    if (activeIndex == lastIndex) return@LaunchedEffect
    if (activeIndex > lastIndex) {
        sectionAlpha.snapTo(1f)                                  // вниз = миттєва заміна
    } else {
        sectionAlpha.snapTo(0f)
        sectionAlpha.animateTo(1f, tween(100, delayMillis = 50)) // вгору = fade-in
    }
    lastIndex = activeIndex
}
```
На `DeviceClass.LOW` / Mediatek — завжди `snapTo(1f)`.

**Анімація висоти (YouTV `MainVerticalGrid.V1()`, 250ms):**
- Розміри карток як у `TvContentRow` (ContentRow.kt:229–303): `tvDims = ProviderSizes.card(posterStyle)`, `cardScale` за deviceClass (LOW .75 / MID 1.0 / HIGH 1.15), `largeCardScale` для trending (HIGH 1.25 інакше 1.15)
- `rowHeight = base * scales + (if showFocusPanel(HIGH only) CardDefaults.focusPanelHeight + 8.dp else 32.dp)`
- Обгорнути в `animateDpAsState(targetValue = rowHeight, animationSpec = tween(250))`
- `posterStyle = PosterStyle.forProvider(items.firstOrNull()?.provider ?: providerHint)`
- Картки: reuse `MovieCard(...)` та `ContinueWatchingCard(...)` з `ui/home/` — ті самі параметри, що в TvContentRow (width, height, showFocusPanel, onClick, onDismiss, sharedTransitionScope/animatedContentScope, focusModifier, onFocused)

**Перехоплення ↑/↓ (onPreviewKeyEvent на контейнері LazyRow, preview-фаза):**
```kotlin
Modifier.onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionUp   -> if (activeIndex > 0)                { onSectionChange(activeIndex - 1); true } else false // false = фокус іде на hero
        Key.DirectionDown -> if (activeIndex < sections.lastIndex) { onSectionChange(activeIndex + 1); true } else false
        else -> false
    }
}
```
Лівий/правий край: блокувати як у TvContentRow (isFirst && DirectionLeft → true; isLast && DirectionRight → true).

**Повернення фокусу після зміни секції:** тримати `var hadFocus by remember`; коли секція змінилась І `hadFocus == true` — retry-цикл (3 спроби з `withFrameNanos {}`, як ContentRow.kt:256–268) `firstItemFocus.requestFocus()` через `focusRequester` на першому елементі. При першому вході в ряд (з hero) — фокус природний, нічого не красти.

**Решта обов'язкова поведінка (копіюється з TvContentRow):**
- Shimmer-плейсхолдери `ShimmerBox` поки `items.isEmpty() && isLoading` (`deviceClass.maxShimmerItems()` шт.)
- Restore-логіка: `restoreMovie` → знайти index по ключу `"${pageUrl}_${season}_${episode}"`, `lazyListState.scrollToItem(index)` + requestFocus (ContentRow.kt:254–268)
- Trailing-кнопка «Див. всі» — reuse `TrendsTrailingButton(brandColor, onClick = { onSeeAllClick(section) }, useLargeCards, provider)`; для секції trending батько викликає `onSeeAllTrendsClick`, для категорій — `onSeeAllCategoryClick(categoryKey)`
- Звук фокусу: `AudioManager.playSoundEffect(FX_FOCUS_NAVIGATION_LEFT)` з тротлінгом 150ms (ContentRow.kt:406–413)
- `onItemFocused(item)` пробросити (він драйвить фон через ViewModel.focusedMovie)
- `BringIntoViewSpec` як у ContentRow.kt:322–326

### Зміни в TvHomeScreen
1. Побудова секцій (`remember` від mainState/categoriesState/homeLayout/maxItems), порожні секції (items empty && !isLoading) пропускати:
   - Продовжити перегляд (dismissable=true) → Мій список → Тренди (`trendingLabel`, useLargeCards=true) → Фільми → Серіали → Аніме → Мультфільми → Мультсеріали (кожен із `categoryKey`)
   - `take(maxItems)` де maxItems = `deviceClass.maxPostersPerRow()` — як зараз
2. `var activeIndex by rememberSaveable { mutableIntStateOf(initial) }`; initial = індекс секції, що містить restoreTarget (якщо є). Clamp: `LaunchedEffect(sections) { if (activeIndex >= sections.size) activeIndex = sections.lastIndex.coerceAtLeast(0) }`
3. `rowHasFocus` стан ← `onRowFocusChange` з MainSectionRow; використати в HomeBackground: `backdropUrl = if (rowHasFocus) focusedMovie?.poster else activeBannerMovie?.backdropUrl`, blur 28dp лише HIGH&&rowHasFocus (замість старої логіки pastHero/scrollFraction — скролу більше немає)
4. Прибрати Rail Fade (`focusedRowId`) — він більше не потрібен
5. FOCUS_RESTORE_WINDOW_MS / restoreWindowOpen механіку зберегти
6. Grid error: компактний банер з кнопкою «Спробувати знову» між hero і рядком, якщо `gridError != null && !mainState.isLoading`

---

## B. Екран категорії (перехід «Див. всі»): інфо про сфокусований елемент + пагінація

### B1. `ui/components/MediaGridScreen.kt`
Додати параметри (дефолти щоб не зламати Trends/Top200, які теж цей компонент юзають):
```kotlin
showFocusedInfo: Boolean = false,
onLoadMore: (() -> Unit)? = null,
isLoadingMore: Boolean = false,
```
Тільки TV-гілка (`TvMediaGridScreen`). Якщо `showFocusedInfo`:
1. Вгорі (~35% висоти) — панель даних **сфокусованого елемента сітки** (трекнути `focusedIndex: MutableState<Int?>`; `CompactMediaCard` додати параметр `onFocused: (() -> Unit)? = null`, викликати коли interactionSource сфокусований):
   - Назва (24–28sp bold), рядок: пігулка рейтингу (bg brandColor, білий текст, як IMDb-бейдж) • рік • жанри • тривалість, опис `maxLines = 3` ellipsis
   - Змена даних — плавно (`AnimatedContent`/Crossfade fade 150ms); padding start 56dp
2. Сітка нижче займає решту, `contentPadding` horizontal 78dp (замість 16dp)
3. Кнопка Back лишається (floating зверху зліва)

Пагінація (TV+phone, якщо `onLoadMore != null`):
```kotlin
LaunchedEffect(gridState, items.size, isLoadingMore) {
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
        .collect { last ->
            if (last != null && last >= items.size - 10 && !isLoadingMore && !isLoading) onLoadMore?.invoke()
        }
}
```
Індикатор `isLoadingMore`: маленький CircularProgressIndicator у футері сітки.

Phone-гілка нові параметри ігнорує.

### B2. `ui/category/FullCategoryGridViewModel.kt` — лінива пагінація
Зараз (ряд. 72–91) одразу тяне сторінки 1..4. Замінити на:
```kotlin
private var currentPage = 0
private var endReached = false
private val _isLoadingMore = MutableStateFlow(false)
val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

fun loadNextPage() {
    val provider = providerManager.activeProvider.value
    if (currentPage == 0 || endReached || _isLoadingMore.value || _items.value.isEmpty()) {
        if (_items.value.isEmpty()) refresh() // перша сторінка йде через init→refresh
        return
    }
    viewModelScope.launch {
        _isLoadingMore.value = true
        try {
            val page = withContext(Dispatchers.IO) { provider.getMoviesByCategory(category, currentPage + 1) }
            if (page.isEmpty()) endReached = true
            else {
                currentPage += 1
                _items.value = (_items.value + page).distinctBy { it.pageUrl }
            }
        } catch (e: Exception) { /* не ламаємо вже показаний контент; можна писати в _error */ }
        finally { _isLoadingMore.value = false }
    }
}
fun retry() = refresh()
private fun refresh() { currentPage = 0; endReached = false; _items.value = emptyList(); ... завантажити сторінку 1, при успіху currentPage = 1 ... }
```
У `FullCategoryGridScreen.kt` передати `onLoadMore = viewModel::loadNextPage`, `isLoadingMore`, `showFocusedInfo = true`.

---

## C. Сторінка деталей (TV): YouTV-стиль

### C1. Новий файл `ui/detail/TvDetailContent.kt`
Замінює поточний Netflix-скрол `DetailContent` (DetailScreen.kt:164–755) **тільки для TV**. Phone (`PhoneDetailContent`, ряд. 782) не чіпати.

Композиція (усі відступи як у YouTV activity_video_detail.xml):
```
Box(fillMaxSize.background(Background)) {
  AsyncImage(detail.poster, ContentScale.Crop, fillMaxSize, alpha ~0.85)   // бекдроп
  Box(Brush.verticalGradient(Transparent → Background.copy(.5f) → Background))  // низ
  Box(Brush.horizontalGradient(Background.copy(.7f) → Transparent))             // лівий затемнювач
  // floating Back-кнопка (кругла Surface 44dp, як зараз у DetailContent)
  Loading → DetailSkeleton(); Error → повідомлення + retry
  Success → Column(Modifier.align(BottomCenter).padding(start = 42.dp, end = 96.dp, bottom = 48.dp)):
     1. Title uppercase bold 40sp, maxLines 2
     2. Row: RatingPill("IMDb 7.4" або rating з detail.rating; bg brandColor, radius 4, padding h12 v2)
              + Text(year • country.take(2) • genres.take(3), one line ellipsis, alpha .7)
     3. Spacer 20 → ActionsRow (Row spacedBy 16, vertical center):
        - PlayButton: Surface 210×54dp, bg brandColor (focused: white bg/black text), icon PlayArrow + 
          текст "ДИВИТИСЯ" / "ПРОДОВЖИТИ"; під ним (якщо watchPercent > 0):
          Box(width 210){ LinearProgressIndicator(progress = watchPercent/100f, height 3.dp, brandColor)
                          + Text("Переглянуто ${watchPercent}%", 11sp, alpha .6) }
          Стан Resolving → CircularProgressIndicator(20dp) + "ЗАВАНТАЖЕННЯ..." (як зараз)
        - Bookmark: Surface 54×54dp icon Favorite/FavoriteBorder (toggle isInWatchlist)
        - Пігулка типу: Text("ФІЛЬМ"/"СЕРІАЛ", border White.copy(.3), chip shape, 12sp bold)
     4. Серіали (detail.seasons not null): reuse готовий `SeasonEpisodePicker(seasons, onEpisodeClick, accentColor = brandColor)` 
        — він уже має чипси сезонів + озвучок + картки серій (SeasonEpisodePicker.kt). Заголовок «ЕПІЗОДИ» всередині нього.
     5. InfoBlocks Row(top 16): 
        - Опис (weight 0.7f): Box(bg White.copy(.06f), RoundedCornerShape(8), padding 12): description, 15sp, alpha .85, maxLines 5
        - Озвучка (weight 0.3f): label "ОЗВУЧКА" bold 13sp brandColor + перелік voiceoverOptions через " · "
     6. «Рекомендуемо» (related, см. C2): заголовок 13sp Black letterSpacing 2sp + контейнер:
        var expanded by remember; height animateDpAsState(tween 250): 52.dp ↔ ~230.dp
        LazyRow усередені з MovieCard (compact розміри ProviderSizes.compactCard), 
        Modifier.onFocusChanged { expanded = it.hasFocus } на контейнері; alpha анімація так само.
     7. Коментарі: якщо detail.comments.isNotEmpty() — існуючий CommentsSection нижче.
}
```
Фокус-порядок природний (Compose focus search): Play → Bookmark → сезони → опис → рекомендації. Перевірити, що з Play DOWN веде в seasons/description.

### C2. `ui/detail/DetailViewModel.kt`
1. `DetailUiState` += `related: List<Movie> = emptyList()`
2. `DetailState.Success` += `watchPercent: Int = 0` (у `loadDetail` вже є повний `progress: WatchProgress` — взяти `progress?.progressPercentage ?: 0`, модель WatchProgress.kt:23 має `progressPercentage`)
3. Завантаження related після Success (viewModelScope.launch):
   ```kotlin
   private fun loadRelated(detail: MovieDetail) {
       viewModelScope.launch {
           val query = detail.genres.firstOrNull()
           var list = if (!query.isNullOrBlank()) {
               runCatching { mediaRepository.search(query).first().getOrDefault(emptyList()) }.getOrDefault(emptyList())
           } else emptyList()
           if (list.size < 4) list = runCatching { mediaRepository.getTmdbTrendsCached(providerManager.activeProvider.value) }.getOrDefault(emptyList())
           _related.value = list.filter { it.id != detail.id && it.pageUrl != detail.pageUrl }.distinctBy { it.pageUrl }.take(12)
       }
   }
   ```
   Додати `_related = MutableStateFlow(emptyList())` і включити в combine uiState (combine тепер з 5 потоків).
4. Виклик `loadRelated(detail)` після `_state.value = Success(...)` (обидва місця: основний і enriched).

### C3. `ui/detail/DetailScreen.kt` — роутинг
У гілці Success (ряд. ~116–127): `PHONE → PhoneDetailContent(...)` (без змін), `else → TvDetailContent(uiState, onMovieClick, onWatchClick = { viewModel.watchContent() }, onEpisodeClick = { s, e, vo -> viewModel.watchContent(s, e, vo) }, onBackClick, onToggleWatchlist)`.
Стару функцію `DetailContent` (164–755) та приватну `MetaRow` (758) видалити (мертвий код), імпорти почистити. `SeasonEpisodePicker`, `CommentsSection`, `DetailSkeleton`, `RatingCircle` лишаються.

---

## Порядок робіт
1. A: `MainSectionRow.kt` → переписати `TvHomeScreen` → збірка
2. B: ViewModel пагінація → MediaGridScreen → збірка
3. C: DetailViewModel → TvDetailContent → роутинг → збірка

## Верифікація
```bash
./gradlew :app:assembleDebug
```
Ручний чек-лист (TV, D-pad):
- [ ] Головна: hero фокусується; ↓ входить у ряд; ↑/↓ перемикають секції (вниз = миттєво, вгору = fade-in); з першої секції ↑ повертає на hero
- [ ] Банер/фон реагує на сфокусовану картку; «Продовжити перегляд» довгий тап видаляє
- [ ] «Див. всі» → екран категорії: інфо зверху змінюється при русі сіткою; скрол до кінця підтягує наступну сторінку; Back працює
- [ ] Деталі (фільм): бекдроп, Play/закладка/пігулки; прогрес «Переглянуто X%» після перегляду; «Рекомендуемо» розгортається при фокусі
- [ ] Деталі (серіал): чипси сезонів/озвучок/серій працюють, запуск серії
- [ ] Назад із деталей → головна на тій самій секції/картці
- [ ] Регрес: Phone-версія, Trends, Top200 — без візуальних змін
