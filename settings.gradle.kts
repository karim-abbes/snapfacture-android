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

rootProject.name = "Snapfacture"
include(":app")
// Harnais d'évaluation phase 0 de l'assistant IA (docs/DESIGN-ASSISTANT-IA.md) — jetable, jamais distribué aux utilisateurs.
include(":spike-assistant")
