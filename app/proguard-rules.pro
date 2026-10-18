# Call Mirror keeps reflection-free hot paths, so only framework and library
# defaults plus the ViewBinding consumer rules are required here.

# Coroutines: keep metadata used by stack-trace recovery and support libs
-keepnames @kotlinx.coroutines.InternalCoroutinesApi class * : kotlinx.coroutines.internal.DiagnosticCoroutineContextException

# OkHttp: platform logging probes loaded reflectively when present
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# zxing-android-embedded: capture activities launched via intents
-keep class com.journeyapps.barcodescanner.** { *; }
