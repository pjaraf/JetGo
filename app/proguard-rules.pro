# Reglas para Retrofit/Gson, LibVLC y modelos
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

-keepattributes Signature
-keepattributes *Annotation*
-keep class com.jetgo.tv.data.model.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn org.videolan.libvlc.**
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.libvlc.util.** { *; }
