pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Workeeper"

include(":app:app")
// The CMP-shaped composition root: App(), the NavDisplay and its entry providers, and the
// navigator. `:app:app` depends on it, so it sits BELOW the app graph and reads what it needs
// through its own AppRootDeps contract. See documentation/feature-specs/kmp-phase-4-app-common.md.
include(":app:common")
include(":app:dev")
include(":app:store")
include(":app:wear")

include(":core:core")
include(":core:ui:kit")
include(":core:ui:golden-harness")
include(":core:ui:navigation")
include(":core:ui:mvi")
include(":core:ui:test-utils")
include(":core:data:database")
include(":core:data:database-test")
include(":core:data:exercise")
include(":core:data:dataStore")
include(":core:data:backup:api")
include(":core:data:backup:google-drive")
include(":core:data:backup:scheduling")
include(":core:data:backup:worker")
include(":core:wear-protocol")
include(":core:ui:plan-editor")
include(":core:ui:start-mode")

include(":feature:exercise")
include(":feature:exercise-chart")
include(":feature:all-exercises")
include(":feature:all-trainings")
include(":feature:single-training")
include(":feature:settings")
include(":feature:archive")
include(":feature:home")
include(":feature:live-workout")
include(":feature:past-session")
include(":feature:image-viewer")
include(":feature:plan-editor")
include(":feature:app-dialogs:api")
include(":feature:app-dialogs:impl")
include(":feature:recovery")
include(":feature:wear-bridge")

include(":lint-rules")
