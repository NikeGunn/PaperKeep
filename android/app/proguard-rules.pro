# ScanVault ProGuard / R8 rules
# These rules are added on top of the Android default rules.

# Keep application class
-keep class com.scanvault.app.ScanVaultApplication { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.scanvault.**$$serializer { *; }
-keepclassmembers class com.scanvault.** {
    @kotlinx.serialization.Transient <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.**

# Prevent stripping of enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Ktor client
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Glance AppWidget
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Quick Settings TileService
-keep class * extends android.service.quicksettings.TileService { *; }

# Share / Intent handling activities
-keep class com.scanvault.app.share.** { *; }

# Crash handler — must survive even when most of the app is stripped
-keep class com.scanvault.app.crash.ScanVaultCrashHandler { *; }

# OTA config data classes (kotlinx.serialization)
-keep class com.scanvault.core.network.ota.** { *; }

# OpenCV — only keep the native bridge; let R8 strip unused pure-Java wrappers
-dontwarn org.opencv.**
-keep class org.opencv.android.OpenCVLoader { *; }
-keep class org.opencv.core.Mat { *; }
-keep class org.opencv.core.Size { *; }
-keep class org.opencv.core.Scalar { *; }
-keep class org.opencv.imgproc.Imgproc { *; }
-keep class org.opencv.imgcodecs.Imgcodecs { *; }

# PaddleOCR / TFLite — keep the JNI entry points
-dontwarn com.baidu.paddle.**
-keep class com.baidu.paddle.** { *; }
-dontwarn org.tensorflow.**
-keep class org.tensorflow.lite.** { *; }

# AdMob / Google Mobile Ads
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.ads.** { *; }

# Keep entry points for reflection
-keepattributes Signature
-keepattributes Exceptions
-keepattributes EnclosingMethod
-keepattributes InnerClasses
