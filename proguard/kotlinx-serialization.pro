# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Compose Navigation type-safe routes resolve enum nav-args via
 # Class.forName(serialName) where serialName defaults to compile-time FQN.
 # The kotlinx-serialization rules above keep `serializer()` members but allow
 # class-name obfuscation, which breaks the Class.forName lookup on minified
 # builds → IllegalArgumentException("Cannot find class with name ...").
-keep @kotlinx.serialization.Serializable class ** extends java.lang.Enum {
    *;
}

