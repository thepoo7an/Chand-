# ProGuard / R8 Keep Rules for Chand?! Release Builds

# Kotlinx Serialization (Models & Navigation Routes)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.example.model.**$$serializer { *; }
-keepclassmembers class com.example.model.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.example.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.example.ui.navigation.** {
    *** Companion;
}

# Moshi Codegen Adapters & Retrofit Interfaces
-keep class * extends com.squareup.moshi.JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-keep @com.squareup.moshi.JsonClass class com.example.network.** { *; }
-keepclassmembers class com.example.network.*JsonAdapter { *; }
-keep interface com.example.network.FinancialRetrofitApi { *; }
-keep interface com.example.network.GitHubReleaseApi { *; }

# Retrofit & OkHttp
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Glance AppWidgets, Receivers, Actions & JobService
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
-keep class com.example.widget.WidgetConnectivityJobService { *; }
