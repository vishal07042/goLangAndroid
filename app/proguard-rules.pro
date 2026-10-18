# ─── Go Enforcer / Affirmation Enforcer release rules ────────────────────
# Compose, coroutines and the lifecycle stack ship their own consumer
# rules — nothing extra needed for them.

# Manifest-declared components are kept automatically, but be explicit so
# a future refactor of these never strips them: the system instantiates
# them by name.
-keep class com.example.goenforcer.EnforcerAccessibilityService { *; }
-keep class com.example.goenforcer.EnforcerBootReceiver { *; }
-keep class com.example.goenforcer.AffirmationAlarmReceiver { *; }
-keep class com.example.goenforcer.AffirmationActivity { *; }
-keep class com.example.goenforcer.MainActivity { *; }

# Generated bank is pure data — keep field layout intact.
-keepclassmembers class com.example.goenforcer.GeneratedQuestionBank { *; }

# Line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
