-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep,allowobfuscation,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

-repackageclasses
-allowaccessmodification

-dontwarn io.github.libxposed.api.**

-keep class org.luckypray.dexkit.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
