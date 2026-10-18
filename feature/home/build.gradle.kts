plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // PLAIN Store, single @DefaultDispatcher.
    alias(libs.plugins.metro)
    // Goldens for the Home surface; the harness comes from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.home.resources"
}

// Metro reads javax.inject qualifiers so the inherited @DefaultDispatcher keeps its qualifier.
metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))

            // `api`: HomeStore.State carries the kit's PagingUiState; HomeStore, HomeHandlerStore,
            // HomeHandlerStoreImpl and HomeStoreImpl extend the MVI contracts; homeGraph extends
            // NavGraphScope.
            api(project(":core:ui:kit"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            // The start card's mode catalog + picker sheet, shared with feature:settings (HS5).
            // `api`: State.startCardMode and the mode-selected action carry its StartCardModeUi.
            api(project(":core:ui:start-mode"))
            implementation(project(":core:data:exercise"))
            // HS6: the start card's mode is a CommonDataStore preference beside themePreference.
            implementation(project(":core:data:dataStore"))

            // `api`: homeGraph takes a Modifier and Event.HapticClick carries a HapticFeedbackType;
            // HomeInteractor returns PagingData and Flow, and State carries the PagingData;
            // StartCardBodyUi and the picker sheet carry ImmutableList.
            api(libs.cmp.ui)
            api(libs.androidx.paging.common)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            // kotlinx-datetime is `implementation` in core:core, so it does not arrive transitively;
            // `api` because the use-case interfaces take a TimeZone (D2).
            api(libs.kotlinx.datetime)
            implementation(libs.androidx.compose.paging)
            implementation(libs.cmp.animation)
            implementation(libs.cmp.material.icons.extended)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutine.test)
        }

        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.cmp.ui.test)
        }
    }
}

dependencies {
    "androidHostTestImplementation"(project(":core:ui:golden-harness"))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
