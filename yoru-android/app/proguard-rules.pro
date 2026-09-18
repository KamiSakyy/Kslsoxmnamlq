-allowaccessmodification
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable,!MethodParameters,!Signature,!Deprecated
-repackageclasses ''
-flattenpackagehierarchy ''
-overloadaggressively
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
}
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}
-keep class com.tsuyu.line.r.** { *; }
-keep class com.tsuyu.line.Sec {
    public static java.lang.String s(java.lang.String);
    public static byte[] decryptData(byte[]);
    public static boolean checkSecurity();
    private static native java.lang.String x(java.lang.String);
    private static native boolean c();
}
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
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
