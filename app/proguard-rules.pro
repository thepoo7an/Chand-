# ProGuard / R8 Keep Rules for Chand?! Release Builds

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep class kotlin.Metadata { *; }

# ViewModel Reflection Safety
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Kotlinx Serialization (Models & Type-Safe Navigation Routes)
-dontnote kotlinx.serialization.AnnotationsKt
-keep class ir.thepoo7an.chand.model.** { *; }
-keep class ir.thepoo7an.chand.ui.navigation.** { *; }
-keepclassmembers class ir.thepoo7an.chand.model.** {
    *** Companion;
}
-keepclasseswithmembers class ir.thepoo7an.chand.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class ir.thepoo7an.chand.ui.navigation.** {
    *** Companion;
}
-keepclasseswithmembers class ir.thepoo7an.chand.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Moshi Codegen Adapters & Retrofit Interfaces
-keep class * extends com.squareup.moshi.JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-keep @com.squareup.moshi.JsonClass class ir.thepoo7an.chand.network.** { *; }
-keep class ir.thepoo7an.chand.network.*JsonAdapter { *; }
-keep interface ir.thepoo7an.chand.network.FinancialRetrofitApi { *; }
-keep interface ir.thepoo7an.chand.network.GitHubReleaseApi { *; }

# Retrofit & OkHttp
-dontwarn retrofit2.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Glance AppWidgets, Receivers, Actions & JobService
-keep class ir.thepoo7an.chand.ChandApplication { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
-keep class ir.thepoo7an.chand.widget.WidgetConnectivityJobService { *; }
