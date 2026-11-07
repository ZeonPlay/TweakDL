# VideoInfo is populated by Jackson from yt-dlp JSON output.
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-dontwarn org.apache.commons.**
-dontwarn com.fasterxml.jackson.**
