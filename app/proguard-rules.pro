# Kept for when minification is enabled.

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.mateof.tfm.**$$serializer { *; }
-keepclassmembers class com.mateof.tfm.** { *** Companion; }
-keepclasseswithmembers class com.mateof.tfm.** { kotlinx.serialization.KSerializer serializer(...); }

# --- Data models (used by Gson inside SignalR client too) ---
-keep class com.mateof.tfm.data.model.** { *; }

# --- SignalR / RxJava ---
-keep class com.microsoft.signalr.** { *; }
-dontwarn com.microsoft.signalr.**
-dontwarn io.reactivex.rxjava3.**
-dontwarn org.slf4j.**

# --- Retrofit / OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
