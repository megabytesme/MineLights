plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.gradle.userdev") version "7.1.21" apply false
}

val requestedNeoForgeVersions = providers.gradleProperty("neoforge.versions")
    .orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?: listOf("1.20.2")

stonecutter active requestedNeoForgeVersions.first()
