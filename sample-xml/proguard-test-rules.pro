# R8 rules for the instrumentation test APK of the minifiedDebug build (testBuildType). Never applied to the app.
#
# R8 failure fixed (minifyMinifiedDebugAndroidTestWithR8, "Missing classes detected"): leakcanary-android-instrumentation
# has optional integrations with the pre-AndroidX support library (android.support.v4.app.Fragment*) and with
# WorkManager's multiprocess artifact (androidx.work.multiprocess.RemoteListenableWorker). This app uses neither;
# LeakCanary only activates those integrations when the classes are present at runtime.
-dontwarn android.support.v4.app.**
-dontwarn androidx.work.multiprocess.**
