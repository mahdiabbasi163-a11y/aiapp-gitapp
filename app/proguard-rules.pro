# ProGuard & R8 Optimization & Obfuscation Rules for Kodyar24

# Keep Android entry points and components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Retrofit data models to prevent JSON deserialization issues
-keepclassmembers class com.example.data.model.** { *; }
-keep class com.example.data.model.** { *; }

# Keep Room entities and DAOs
-keepclassmembers class com.example.data.db.** { *; }
-keep class com.example.data.db.** { *; }

# Preserve line numbers and source attributes for crash reports
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable,Signature,Annotation,*Annotation*
