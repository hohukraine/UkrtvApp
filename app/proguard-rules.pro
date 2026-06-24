# Jsoup HTML parsing
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class org.jsoup.parser.** { *; }
-keep class org.jsoup.select.** { *; }
-keep class org.jsoup.nodes.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okhttp3.internal.** { *; }
-dontwarn okhttp3.internal.**

# ExoPlayer (Media3)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-dontwarn androidx.media3.exoplayer.**
-keep class androidx.media3.common.** { *; }
-dontwarn androidx.media3.common.**
-keep class androidx.media3.datasource.** { *; }
-dontwarn androidx.media3.datasource.**
-keep class androidx.media3.ui.** { *; }
-dontwarn androidx.media3.ui.**


-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject *;
    @dagger.hilt.android.* <fields>;
}

# Jetpack Compose
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.ui.**
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.material3.**
-keep class androidx.tv.** { *; }
-dontwarn androidx.tv.**



# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class ua.ukrtv.app.domain.model.** { *; }
-keep class * implements android.os.Parcelable { *; }

# DataStore / Preferences
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**
-keep class androidx.datastore.preferences.** { *; }
-dontwarn androidx.datastore.preferences.**

# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keepclassmembers,allowobfuscation class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *



# Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}


