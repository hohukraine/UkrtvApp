# UkrtvApp

Android TV додаток для перегляду фільмів та серіалів з українських онлайн-ресурсів.

## Функції

- **Пошук** — миттєвий пошук по каталогу Uakino та UAFLIX (база 47к+ записів)
- **Топ 200** — добірка найкращих фільмів за версією ютуб каналу Чесний Огляд
- **Тренди** — актуальні фільми та серіали 2026 року
- **Продовжити перегляд** — автоматичне запам'ятовування прогресу
- **Обране** — персональний список улюбленого контенту
- **Детальна інформація** — рейтинг IMDB, актори, режисер, жанри, країна
- **Адаптивний інтерфейс** — оптимізовано для Android TV (DeviceClass: LOW/MID/HIGH)

## Обслуговування каталогу

Для підтримання актуальності бази даних використовується Python скрипт:

```bash
# Регулярне швидке оновлення (RSS + перші сторінки)
python3 scripts/sync_catalog.py --incremental

# Повний добір відсутніх за sitemap (разово або рідко)
python3 scripts/sync_catalog.py --baseline --backfill-limit 500

# Перевірка на "мертві" посилання (видалення 404)
python3 scripts/sync_catalog.py --prune-verify
```

## Вимоги

- **Android TV** (звичайний Android теж працює)
- **Android 6.0+** (API 23)
- **Апаратне прискорення** — рекомендовано

## Збірка

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Встановлення на TV через ADB
adb connect <TV_IP>
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Використані ресурси

- [Uakino](https://uakino.best/) — український кіно-портал
- [UAFLIX](https://uafix.net/) — український онлайн-кінотеатр

## Ліцензія

MIT
