# SpeechWithAbhi ProGuard Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room entities and DAOs
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Kotlinx Serialization models
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Media3 MediaSessionService
-keep class androidx.media3.session.** { *; }
-keep class com.speechwithabhi.app.service.playback.** { *; }
