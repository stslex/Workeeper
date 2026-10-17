plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Non-collider. Route-arg feature (shape B — Screen.PastSession is a @Provides bound instance on the
    // extension factory, not an @Assisted param), single @IODispatcher (no collision).
    alias(libs.plugins.metro)
    // Goldens for the past-session surface. They live here rather than in core:ui:kit because
    // PastSetEditRow and PastExerciseCard are feature components and stay that way — extraction
    // §6.4 rank 8 settles the shared-row question as "deliberately separate" from LiveSetRow.
    // The harness is NOT copied: it comes from core:ui:golden-harness, so device config,
    // tolerance and canvas width cannot drift between modules.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.past_session.resources"
}

// Metro reads javax.inject qualifiers so the inherited @IODispatcher keeps its qualifier.
metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))

            // The kit stays implementation: no public declaration of this module names a kit type,
            // and the StringResolver is injected only into the internal CommonHandler.
            implementation(project(":core:ui:kit"))
            // `api`: PastSessionStore, PastSessionHandlerStore and PastSessionStoreImpl extend the
            // MVI contracts; pastSessionGraph extends NavGraphScope and PastSessionGraph.Factory
            // takes a Screen.PastSession; PastSetUiModel and OnSetTypeChange carry SetTypeUiModel.
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            api(project(":core:ui:plan-editor"))
            implementation(project(":core:data:exercise"))

            // `api`: pastSessionGraph takes a Modifier and Event.HapticClick carries a
            // HapticFeedbackType; the interactor returns a Flow and the graph exposes a
            // CoroutineDispatcher; State and the UI models carry ImmutableSet / ImmutableList.
            api(libs.cmp.ui)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
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
