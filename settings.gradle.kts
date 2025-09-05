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
include(":core:database")
include(":core:exercise")

include(":feature:home")
include(":feature:exercise")
