plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    // `api`, not `implementation`, and forced by the same two public exposures as the Nav2
    // library before it: NavGraphScope names EntryProviderScope/NavKey and NavigatorHolder names
    // NavBackStack in public signatures, so the types must reach `core:ui:mvi` and `:app:app`.
    // The KMP runtime artifact only — navigation3-ui (NavDisplay) is `:app:app`'s dependency.
    api(libs.androidx.navigation3.runtime)

    // ScreenSerializationTest: JSON is the round-trip vehicle (format-independent registration
    // check, no Bundle) and kotlin-reflect enumerates the sealed hierarchy.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("reflect"))
}
