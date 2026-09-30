# Adroit core consumer R8 rules.
#
# The library itself uses no reflection, and the Google Mobile Ads Next-Gen SDK and UMP SDK ship their own consumer
# rules inside their AARs. The Google adapter (io.github.thesobiaakbar.adroit.internal.google) calls both SDKs directly, with no
# reflection, class-name lookups or serialization, so it needs no keep rules either. Every rule added here must name
# the concrete R8 failure it fixes (see docs/architecture/R8_REPORT.md).

# Failure fixed: every minified (R8 full mode) app crashed at launch with
#   RuntimeException: Unable to get provider androidx.startup.InitializationProvider
#   Caused by: RuntimeException: Failed to create an instance of androidx.work.impl.WorkDatabase
#     at androidx.room.Room.getGeneratedImplementation
# The Next-Gen SDK (1.4.0) depends on androidx.work:work-runtime:2.7.0, which brings androidx.room:room-runtime:2.2.5.
# Room instantiates the generated WorkDatabase_Impl through its no-arg constructor by reflection, but Room 2.2.5's
# own rule (-keep class * extends androidx.room.RoomDatabase) keeps no constructor in R8 full mode, AGP's default,
# so R8 removed it. Later Room versions ship this member rule themselves; with them it's redundant.
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
