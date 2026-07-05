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
include(":app:dev")
include(":app:store")

include(":core:core")

include(":core:ui:kit")
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
include(":core:ui:plan-editor")

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

include(":lint-rules")

// Phase B — Metro DI spike. Disposable, NOT wired into :app. Revert in one shot on NO-GO.
include(":spike-metro")
