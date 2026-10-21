# kotlinx.serialization keeps generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class red.crishoper.agent.** {
    *** Companion;
}
-keepclasseswithmembers class red.crishoper.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class red.crishoper.agent.**$$serializer { *; }
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
