# Proguard rules for Temi Shoe Store application.
# Add project-specific Proguard rules here.

# Keep Temi SDK classes from being obfuscated/removed
-keep class com.robotemi.sdk.** { *; }
-keep interface com.robotemi.sdk.** { *; }

# Keep Firebase models from being removed
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}
