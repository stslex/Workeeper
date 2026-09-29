-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }
#-dontwarn com.google.firebase.**

# Store action and event type names reach Analytics and the Crashlytics log (storeTypeName in
# StoreAnalytics.kt, wear-paired-transport.md §9.2), so the names must survive R8; the members stay
# shrinkable. GUARD: a nested type is written with `$`. The dotted `Store.Action` form these rules
# replace matched nothing, so every action and event name was obfuscated.
-keepnames class * implements io.github.stslex.workeeper.core.ui.mvi.Store$Action
-keepnames class * implements io.github.stslex.workeeper.core.ui.mvi.Store$Event
-keep class ** extends io.github.stslex.workeeper.core.ui.navigation.Screen { *; }
