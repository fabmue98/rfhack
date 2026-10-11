# Google Play Billing: the AAR ships consumer rules, these are belt-and-braces.
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.**

# Room ships its own rules; keep nothing extra is required, but harmless:
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# org.json ships with the Android platform.
-dontwarn org.json.**

# Keep Compose tooling previews out of the way; no reflection used by the app.
