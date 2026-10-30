rootProject.name = "SosyalOyunPlatformu"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":shared")
include(":server")
// Backend imajı (Dockerfile) Android SDK'sı olmadan derlenir; mobil modüller orada yoktur.
if (System.getenv("SOSYALOYUN_SADECE_SERVER") != "true") {
    include(":composeApp")
    include(":androidApp")
}
