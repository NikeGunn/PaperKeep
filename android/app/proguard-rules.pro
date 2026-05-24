# Paperkeep ProGuard / R8 rules
# These rules are added on top of the Android default rules.

# Keep application class
-keep class app.paperkeep.PaperkeepApplication { *; }

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

# Type-safe Navigation Compose routes are @Serializable; Navigation derives the
# route string from the serial name (the class's qualified name). R8 obfuscation
# renamed these classes, desyncing the back-stack route from KClass.qualifiedName
# and breaking the bottom-bar visibility check in release builds. Keep the route
# class names so the serial name stays stable. (See navigation/AppRoutes.kt.)
-keep,allowobfuscation,allowshrinking class app.paperkeep.navigation.*Route
-keepnames @kotlinx.serialization.Serializable class app.paperkeep.navigation.**
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.paperkeep.**$$serializer { *; }
-keepclassmembers class app.paperkeep.** {
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

# Glance AppWidget
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Quick Settings TileService
-keep class * extends android.service.quicksettings.TileService { *; }

# Share / Intent handling activities
-keep class app.paperkeep.share.** { *; }

# Crash handler — must survive even when most of the app is stripped
-keep class app.paperkeep.crash.PaperkeepCrashHandler { *; }

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
