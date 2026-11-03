# Keep youtubedl-android and JNI bindings
-keep class com.yausername.youtubedl_android.** { *; }
-keep class io.github.junkfood02.youtubedl_android.** { *; }
-dontwarn com.yausername.youtubedl_android.**
-dontwarn io.github.junkfood02.youtubedl_android.**

# Keep models for reflection/serialization
-keep class com.eurtlabs.stash.data.model.** { *; }

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**
