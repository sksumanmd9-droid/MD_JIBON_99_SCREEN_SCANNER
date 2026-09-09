# App-specific ProGuard rules
-keep class com.mdjibon.scanner.Analyzer { *; }
-keep class com.mdjibon.scanner.MainActivity { *; }
-keep class com.mdjibon.scanner.ScreenCaptureService { *; }
-keep class com.mdjibon.scanner.FloatingScannerService { *; }

# Keep all inner classes
-keep class com.mdjibon.scanner.** { *; }

# Preserve annotations
-keepattributes *Annotation*

# Keep line numbers for crash logs
-keepattributes SourceFile,LineNumberTable