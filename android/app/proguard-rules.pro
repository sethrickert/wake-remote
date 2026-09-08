# Defensive keeps for zxing-android-embedded, which supplies the QR capture activity.
#
# Measured, not assumed: with these rules removed entirely, a release build still retains
# all 41 zxing resources and CaptureActivity in the manifest, because AGP generates its
# own aapt keep rules for manifest-declared library activities and resource shrinking
# defaults to "safe" mode. So these are insurance against a future consumer-rules or
# shrinkMode change, not a fix for a reproduced failure.
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.barcodescanner.**

# Kept by name from AndroidManifest.
-keep class com.apextechlabs.wakeremote.MainActivity { *; }
-keep class com.apextechlabs.wakeremote.EnrollmentActivity { *; }
-keep class com.apextechlabs.wakeremote.ManualKeyActivity { *; }
