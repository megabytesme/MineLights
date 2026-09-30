plugins {
    id("dev.kikugie.stonecutter")
    id("net.minecraftforge.gradle") version "[7.0.29,8.0)" apply false
    id("me.modmuss50.mod-publish-plugin") version "0.8.+" apply false
}

val requestedForgeVersions = providers.gradleProperty("forge.versions")
    .orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?: listOf("1.17.1", "1.18", "1.18.1", "1.18.2", "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.6", "1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3")

stonecutter active requestedForgeVersions.first()

stonecutter parameters {
    constants["loader_fabric"] = false
    constants["loader_neoforge"] = true
    constants["loader_forge"] = true
}
