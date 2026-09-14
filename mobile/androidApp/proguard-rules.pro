# kotlinx.serialization: keep generated serializers for the GitHub release DTOs (private nested
# classes inside GitHubUpdater) and the shared config/stats models.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.mdportnov.monk.**$$serializer { *; }
-keep,includedescriptorclasses class com.mdportnov.monk.shared.**$$serializer { *; }
-keepclassmembers class com.mdportnov.monk.** { *** Companion; }
-keepclassmembers class com.mdportnov.monk.shared.** { *** Companion; }
# Accessibility service, tiles and receivers are referenced from the manifest only.
-keep class com.mdportnov.monk.MonkAccessibilityService { *; }
-keep class com.mdportnov.monk.tiles.** { *; }
