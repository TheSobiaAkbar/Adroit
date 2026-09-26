# R8 rules for the minifiedDebug APP only (the test build type), never for release.
#
# Failure fixed (connectedMinifiedDebugAndroidTest, instrumentation crashed before any test):
#   NoClassDefFoundError: Failed resolution of: Lkotlin/LazyKt;
#     at androidx.test.platform.io.TestDirCalculator.<init>
# The test APK doesn't bundle the libraries the app already has, such as the Kotlin standard library; it uses the
# app's copy, from which R8 removed what the app itself doesn't call. Keeping the standard library whole doesn't
# change how the CompanyAdMob libraries or the app code under test are shrunk and obfuscated.
-keep class kotlin.** { *; }

# Failure fixed (FragmentLeakTest):
#   NoSuchFieldError: No field nav_home of type I in class Lcom/company/admob/sample/xml/R$id;
# The tests read the app's resource IDs as fields of its R classes, which R8 inlines into the app and removes.
-keepclassmembers class com.company.admob.sample.xml.R$* {
    public static <fields>;
}
