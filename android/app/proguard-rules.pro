# kotlinx.serialization: keep generated serializers for the saved game
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.therefore.logic.** {
    *** Companion;
}
-keepclasseswithmembers class com.therefore.logic.** {
    kotlinx.serialization.KSerializer serializer(...);
}
