# ProGuard rules for Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-dontwarn androidx.media3.exoplayer.**

# MediaCodec optimizations
-keep class android.media.MediaCodec { *; }
-dontwarn android.media.MediaCodec

# Hilt
-keep class dagger.** { *; }
-keep class dagger.hilt.** { *; }
-dontwarn dagger.**

# Jetpack Compose - critical for runtime optimization
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.ui.**
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.material3.**
-keep class androidx.tv.** { *; }
-dontwarn androidx.tv.**

# Compose snapshots (for lock verification)
-keep class androidx.compose.runtime.snapshots.** { *; }
-dontwarn androidx.compose.runtime.snapshots.**

# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Codec2
-keep class android.media.codec2.** { *; }
-dontwarn android.media.codec2.**