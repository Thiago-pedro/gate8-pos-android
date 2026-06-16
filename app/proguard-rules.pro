-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Stone SDK (flavor stone + token PackageCloud)
-dontwarn stone.**
-keep class stone.** { *; }
-keep class br.com.stone.** { *; }