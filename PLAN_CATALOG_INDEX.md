# План: Локальний пошуковий індекс провайдерів

## Проблема
Поточний DLE search:
- Повертає 0 результатів для валідних запитів (помилки кодування)
- Повертає хибні результати ("Кухар" → "Кухня", "Кухарі")
- Кожен пошук = HTTP запит (500ms-6s)
- catalog fallback повільний (3-5 сторінок, ~3-5s)

## Рішення: Local Catalog Index

Сканувати категорії обох провайдерів у фоновому режимі,
зберігати в Room, шукати локально (миттєво).

## Дані

### Uakino
| Категорія | Сторінок | Items/page | Всього |
|-----------|----------|-----------|--------|
| `/filmy/` | 446 | 40 | ~17,840 |
| `/seriesss/` | 167 | 40 | ~6,680 |
| **Всього** | **613** | | **~24,520** |

**Структура HTML:**
```html
<div class="movie-item short-item">
  <img src="..." alt="НАЗВА постер">
  <a href="URL" class="movie-title">НАЗВА</a>
  <div class="full-quality">HD 720p</div>
  <div class="deck-value">IMDB рейтинг</div>
</div>
```

**Екстракція:** позиційний split по `<div class="movie-item short-item">`

### Eneyida
| Сторінок | Items/page | Всього |
|----------|-----------|--------|
| 377 | 24 | ~9,048 |

**Структура HTML:**
```html
<article class="short related_item ignore-select clearfix">
  <a href="URL" class="short_title">НАЗВА</a>
  <div class="short_subtitle">РІК • ENGLISH_TITLE</div>
  <img data-src="POSTER_URL">
  <div class="label_quel-hd">FHD 1080p</div>
  <span class="ratingplus">+РЕЙТИНГ</span>
</article>
```

## Схема Room

```sql
CREATE TABLE catalog_index (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,          -- українська назва
    title_en TEXT,                -- англійська назва
    url TEXT NOT NULL UNIQUE,     -- посилання на детальну сторінку
    poster TEXT,                  -- URL постера
    provider TEXT NOT NULL,       -- "uakino" | "eneyida"
    year INTEGER,
    rating TEXT,                  -- IMDB або рейтинг переглядів
    quality TEXT,                 -- HD 720p, FHD 1080p тощо
    content_type TEXT,            -- "movie" | "series"
    updated_at INTEGER            -- timestamp
);

CREATE INDEX idx_title ON catalog_index(title COLLATE NOCASE);
CREATE INDEX idx_title_en ON catalog_index(title_en COLLATE NOCASE);
CREATE INDEX idx_provider ON catalog_index(provider);
```

## Процес побудови

```
1. App launch (або focus на Home)
   │
   ├── Check if index exists in Room
   │   └── If stale (>24h) or empty → rebuild
   │
   ├── CoroutineScope(Dispatchers.IO)
   │   ├── Launch: UakinoIndexBuilder
   │   │   ├── fetch /filmy/page/1..446  (parallel: 4)
   │   │   └── fetch /seriesss/page/1..167 (parallel: 4)
   │   │
   │   └── Launch: EneyidaIndexBuilder
   │       └── fetch /f/sort=rating/order=desc/page/1..377 (parallel: 4)
   │
   ├── For each page: parse HTML → List<CatalogItem>
   │
   └── Upsert into Room (batches of 100)
```

### Паралельність
- 4 concurrent requests per provider (OkHttp)
- Час на одну сторінку: ~500ms
- Uakino: 613 / 4 = ~154 batches × 500ms = ~77s
- Eneyida: 377 / 4 = ~95 batches × 500ms = ~48s
- **Загалом: ~2 хвилини**

### Пошук при index = ready

```
search(query):
  normalized = normalizeTitle(query)
  variants = buildQueryVariants(normalized)
  
  candidates = room.catalogIndexDao().search(variants)
  
  // SQL: WHERE title LIKE '%query%' OR title_en LIKE '%query%'
  
  best = SearchScorer.pickBestMatch(candidates, query)
  return best
```

Час: **~0ms** (SQLite + in-memory scoring)

## Переваги

| Аспект | DLE search (зараз) | Local Index |
|--------|-------------------|-------------|
| Час пошуку | 500ms-6s | ~0ms |
| "Кухар" знайде? | Ні (хибні результати) | Так ✅ |
| Потрібен інтернет? | Так | Тільки для побудови |
| Працює на повільному 3G? | Ні (таймаути) | Так ✅ |
| Одноразова побудова | — | ~2 хв |
| Пам'ять | — | ~3 MB |

## Імплементація (порядок)

1. **CatalogItem** — data class для індексу
2. **CatalogIndexDao** — Room DAO (insert, search, count)
3. **CatalogIndexDatabase** — Room DB з міграцією
4. **CatalogIndexBuilder** — фоновий білдер (Uakino + Eneyida)
5. **CatalogRepository** — API для пошуку + управління індексом
6. **Providers** — замінити `search()` на `CatalogRepository.search()`
7. **Top200Screen** — прибрати resolveTop200 (вже зроблено)

## Після імплементації

- Видалити `searchCatalogFallback()` з UakinoProvider
- Видалити DLE search (do=search) з обох провайдерів
- Залишити тільки `fetchDetail()` (завантаження сторінки фільму)
