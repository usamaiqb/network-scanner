# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ============================================================================
# DATA CLASSES & MODELS
# ============================================================================

# Keep all data classes (Parcelable objects)
-keep class com.networkscanner.app.data.** { *; }

# Keep all enums and their values
-keepclassmembers enum com.networkscanner.app.data.** { *; }

# ============================================================================
# KOTLIN
# ============================================================================

# Keep Kotlin Metadata for reflection
-keep class kotlin.Metadata { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================================================
# ANDROID COMPONENTS
# ============================================================================

# Keep ViewBinding classes
-keep class com.networkscanner.app.databinding.** { *; }

# Keep AndroidX Preference
-keep class androidx.preference.** { *; }
-keepclassmembers class * extends androidx.preference.Preference {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ============================================================================
# SERIALIZATION (For Parcelable)
# ============================================================================

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ============================================================================
# DEBUG INFO
# ============================================================================

# Keep source file names and line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable

# Hide original source file name in stack traces
-renamesourcefileattribute SourceFile

# ============================================================================
# OPTIMIZATION
# ============================================================================

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Remove Kotlin null checks in release builds
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
}
