-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }
#-dontwarn com.google.firebase.**

-keep class ** extends io.github.stslex.workeeper.core.ui.mvi.Store.Action { *; }
-keep class ** extends io.github.stslex.workeeper.core.ui.mvi.Store.Event { *; }
-keep class ** extends io.github.stslex.workeeper.core.ui.navigation.Screen { *; }
