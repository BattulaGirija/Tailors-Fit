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

rootProject.name = "TailorsFit"
include(":app")
include(":pattern-core")

// The Windows desktop app (Compose for Desktop) builds only when asked: ./gradlew -Pdesktop=true :desktop:packageMsi
if (providers.gradleProperty("desktop").orNull == "true") include(":desktop")
