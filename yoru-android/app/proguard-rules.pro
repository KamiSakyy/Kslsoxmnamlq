-allowaccessmodification
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable
-repackageclasses ''
-overloadaggressively
-useuniqueclassmembernames
-dontoptimize
-assumenosideeffects class android.util.Log {
 public static *** d(...);
 public static *** v(...);
 public static *** i(...);
 public static *** w(...);
 public static *** e(...);
}
-keep class go.** { *; }
-keep class com.tsuyu.line.r.** { *; }
-keepclassmembers class * {
 @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * extends java.lang.Throwable { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.json.**
