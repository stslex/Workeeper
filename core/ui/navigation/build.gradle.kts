import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // GUARD: the classic composeLibrary convention applied this implicitly; the KMP convention
    // does not — without it every generated Screen serializer silently disappears.
    alias(libs.plugins.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`, and forced by the same public exposures as before:
            // NavGraphScope names EntryProviderScope/NavKey and NavigatorHolder names
            // NavBackStack in public signatures, so the types must reach `core:ui:mvi` and
            // `:app:app`. The KMP runtime artifact only — navigation3-ui (NavDisplay) is
            // `:app:app`'s dependency.
            api(libs.androidx.navigation3.runtime)
            // SavedStateConfiguration is public API (screenSavedStateConfiguration); the pin
            // makes the version Navigation 3 already resolves explicit, it changes nothing.
            api(libs.androidx.savedstate)
            // StateFlow/SharedFlow are public API (NavResultsSource, NavigatorReceiver).
            api(libs.coroutines.core)
            // SerializersModule is public API and the @Serializable Screen serializers are
            // generated against it.
            api(libs.kotlinx.serialization.core)
        }

        iosTest.dependencies {
            implementation(kotlin("test"))
            // JSON is the round-trip vehicle for the fixed-catalog registry oracle.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

dependencies {
    // ScreenSerializationTest: JSON is the round-trip vehicle (format-independent registration
    // check, no Bundle) and kotlin-reflect enumerates the sealed hierarchy.
    "androidHostTestImplementation"(libs.kotlinx.serialization.json)
    "androidHostTestImplementation"(kotlin("reflect"))
}

// GUARD: the classic convention compiled this module with -Xjvm-default=all; Kotlin 2.4's
// default ENABLE would additionally emit Screen$DefaultImpls / Screen$BottomBar$DefaultImpls
// compatibility bridges, changing the module's JVM interface ABI. Module-local on purpose —
// the shared KMP convention keeps the toolchain default.
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
}
