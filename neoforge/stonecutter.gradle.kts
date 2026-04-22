plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.gradle.userdev") version "7.1.21" apply false
}

val requestedNeoForgeVersions = providers.gradleProperty("neoforge.versions")
    .orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?: listOf("1.20.2", "1.20.5", "1.21.2", "1.21.6", "1.21.8", "1.21.9", "26.1")

stonecutter active requestedNeoForgeVersions.first()

stonecutter parameters {
    constants["loader_fabric"] = false
    constants["loader_neoforge"] = true
}
