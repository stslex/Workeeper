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
include(":core:database")
include(":core:exercise")
include(":core:dataStore")

include(":feature:exercise")
include(":feature:all-exercises")
include(":feature:all-trainings")
include(":feature:charts")
include(":feature:single-training")

include(":lint-rules")