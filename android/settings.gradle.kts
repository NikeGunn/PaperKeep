pluginManagement {
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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Paperkeep"

include(":app")
include(":core:ui")
include(":core:common")
include(":core:data")
include(":core:domain")
include(":core:ml")
include(":core:imaging")
include(":core:pdf")
include(":core:network")
include(":core:security")
include(":feature:scanner")
include(":feature:library")
include(":feature:reader")
include(":feature:settings")
include(":core:ads")
include(":feature:onboarding")
include(":core:crypto")
include(":feature:account")
include(":feature:sync")
include(":benchmark")
