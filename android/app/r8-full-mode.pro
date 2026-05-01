# R8 full mode rules for Paperkeep
# Applied only when minSdk >= 24 (no legacy class backporting needed).
# These rules allow R8 to do more aggressive dead-code elimination beyond
# the default compatibility mode.

# Suppress R8 full-mode diagnostics that are safe to ignore
-ignorewarnings

# Hilt generated component classes — full mode needs explicit keep
-keep class **_HiltComponents* { *; }
-keep class **_HiltModules* { *; }
-keep class *_MembersInjector { *; }
-keep class *_Factory { *; }
-keepnames class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory

# kotlinx.serialization — keep serializers that R8 full mode prunes
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    private static final kotlinx.serialization.internal.SerializationConstructorMarker *;
    private static final long serialVersionUID;
}
-keep class kotlinx.serialization.internal.** { *; }

# Room — keep generated DAO impls (R8 full mode can strip these)
-keep class **_Impl extends androidx.room.RoomDatabase { *; }
-keep class **Dao_Impl { *; }

# Compose — R8 full mode breaks some internal Compose reflection
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# DataStore — proto/preferences serialization
-keep class androidx.datastore.** { *; }

# Play Billing — BillingClient and Purchase are loaded reflectively
-keep class com.android.billingclient.api.** { *; }

# Play Integrity — token request classes
-keep class com.google.android.play.core.integrity.** { *; }

# ML Kit — keep entry points for on-device models
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Glance widget receivers must survive (R8 strips unused exported components)
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends android.service.quicksettings.TileService { *; }

# CameraX — keep callback interfaces (R8 full mode removes unused interfaces)
-keep interface androidx.camera.** { *; }
-keep class androidx.camera.** { *; }
