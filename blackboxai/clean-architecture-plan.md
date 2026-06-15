# План “Чистий Слой” (Clean Architecture)

## Ціль
Розірвати витоки залежностей:
- Domain / Presentation не мають знати про `ua.ukrtv.app.data.providers.*` (окрім випадків, де це строго виключено межами).
- Data лишається єдиним місцем, яке знає: сайти, парсинг, `MediaSource`, `ContentProvider`, `StreamResolver`.

## Узгоджені рішення
1) Domain має повертати доменні типи (напр. `StreamResolutionResult`) замість `data.providers.MediaSource`.
2) Presentation має оперувати доменними моделями (провайдер як доменний `ProviderUiModel` або хоча б brand/id), а не `data.providers.ContentProvider`.
3) ViewModel викликає UseCase/Repository з Domain, а не Data-сервіси напряму.

## Послідовність робіт
1) Перевірити поточні доменні моделі (особливо `domain/model/StreamResolutionResult.kt`).
2) Оновити `domain/repository/MediaRepository.kt`.
3) Оновити `data/repository/ContentRepository.kt` (конвертація).
4) Оновити UseCase-и.
5) Переробити Home/Player ViewModel + UI state.
6) Оновити DI-модулі.
7) Збірка та ручні перевірки.

