pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "hearth"

include(":app")
include(":core:model")
include(":core:data")
include(":core:tools")
include(":ai")
include(":mcp")
include(":ui")
include(":web")
