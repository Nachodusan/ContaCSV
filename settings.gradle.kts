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
    // 🚫 Bloquea repos locales por seguridad
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        // ⚙️ Si algún día vuelves a necesitar librerías de GitHub, descomenta:
        // maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ContaCSV10"
include(":app")
