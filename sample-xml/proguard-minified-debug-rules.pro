# R8 rules for the minifiedDebug APP only (the test build type), never for release.
#
# Failure fixed (connectedMinifiedDebugAndroidTest, instrumentation crashed before any test):
#   NoClassDefFoundError: Failed resolution of: Lkotlin/LazyKt;
#     at androidx.test.platform.io.TestDirCalculator.<init>
# The test APK doesn't bundle the libraries the app already has, such as the Kotlin standard library; it uses the
# app's copy, from which R8 removed what the app itself doesn't call. Keeping the standard library whole doesn't
# change how the Adroit libraries or the app code under test are shrunk and obfuscated.
-keep class kotlin.** { *; }

# Failure fixed (FragmentLeakTest):
#   NoSuchFieldError: No field nav_home of type I in class Lio/github/thesobiaakbar/adroit/sample/xml/R$id;
# The tests read the app's resource IDs as fields of its R classes, which R8 inlines into the app and removes.
-keepclassmembers class io.github.thesobiaakbar.adroit.sample.xml.R$* {
    public static <fields>;
}

# Failure fixed (AdProfilesDeviceTest):
#   NoSuchMethodError: No interface method snapshot()Lio/github/thesobiaakbar/adroit/DiagnosticsReport; in class
#   Lio/github/thesobiaakbar/adroit/DiagnosticsApi; (and then the same for getRecentEvents())
# The test reads the diagnostics report and recent events, which the app itself never calls, so R8 removed them.
# Kept, but still obfuscated: the test APK is rewritten with the app's mapping.
-keepclassmembers,allowobfuscation interface io.github.thesobiaakbar.adroit.DiagnosticsApi {
    *;
}
-keepclassmembers,allowobfuscation class io.github.thesobiaakbar.adroit.DiagnosticsReport, io.github.thesobiaakbar.adroit.PlacementDiagnostics {
    public *** get*();
    public *** is*();
}

# Failure fixed (every test, after sample-xml added Firebase in PROMPT 28A):
#   ClassNotFoundException: Didn't find class "javax.inject.Provider"
# Firebase brings javax.inject into the app, so the test APK leaves it out and uses the app's copy, which R8 had
# removed. Four annotation and interface types: keeping them changes nothing else.
-keep class javax.inject.** { *; }

# Failure fixed (StartupSmokeTest, with OnboardingShownRule, B22):
#   NoSuchMethodError: No virtual method recordShown(Lio/github/thesobiaakbar/adroit/billing/PaywallDefinition;Ljava/lang/String;)V
#   in class Lio/github/thesobiaakbar/adroit/billing/Paywalls;
# The test counts the onboarding paywall as shown so it doesn't cover MainActivity; the app itself never calls
# recordShown (its controller records the showing internally), so R8 removed it.
-keepclassmembers,allowobfuscation class io.github.thesobiaakbar.adroit.billing.Paywalls {
    public void recordShown(...);
}
