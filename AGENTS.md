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