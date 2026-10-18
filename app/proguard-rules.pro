# OkHttp: platform logging probes loaded reflectively when present
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# zxing-android-embedded: capture activities launched via intents
-keep class com.journeyapps.barcodescanner.** { *; }
