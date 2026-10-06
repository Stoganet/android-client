# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.stoganet.core.**$$serializer { *; }
-keepclassmembers class com.stoganet.core.** { *** Companion; }
-keepclasseswithmembers class com.stoganet.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Tink
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.errorprone.annotations.**

# proto messages reflect on their fields at runtime so R8 renaming crashes release builds
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
