# Jsoup HTML parsing
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# OkHttp
-dontwarn okhttp3.internal.**

# ExoPlayer (Media3)
-dontwarn androidx.media3.**

# Dagger Hilt
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject *;
    @dagger.hilt.android.* <fields>;
}

# kotlinx.serialization
-keep,includedescriptorclasses class ua.ukrtv.app.domain.model.**$$serializer { *; }
-keepclassmembers class ua.ukrtv.app.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class ua.ukrtv.app.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class ua.ukrtv.app.domain.model.** { *; }
-keep class * implements android.os.Parcelable { *; }

# Room
-keepclassmembers,allowobfuscation class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Coroutines
-dontwarn kotlinx.coroutines.**

# Coil
-dontwarn coil.**
