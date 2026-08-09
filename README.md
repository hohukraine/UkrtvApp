<img width="640" height="360" alt="Screenshot_20260808_121536" src="https://github.com/user-attachments/assets/c284b082-9f79-428c-9ba0-e0588f82b4ca" />

# UkrtvApp

Android TV додаток для перегляду фільмів та серіалів з українських онлайн-ресурсів.

## Функції

- **Пошук** — миттєвий пошук по каталогу Uakino та UAFLIX (база 49к+ записів)
- **Топ 200** — добірка найкращих фільмів за версією ютуб каналу Чесний Огляд
- **Тренди** — актуальні фільми та серіали за даними TMDB
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
- **Android 8.0+** (API 26)
- **Зовнішній плеєр** — для відтворення потрібен [VLC](https://play.google.com/store/apps/details?id=org.videolan.vlc) або [Just Player](https://play.google.com/store/apps/details?id=com.brouken.player)
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
