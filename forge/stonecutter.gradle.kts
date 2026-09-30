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
    ?: listOf("1.7.2", "1.7.10", "1.8", "1.8.8", "1.8.9", "1.9", "1.9.4", "1.10", "1.10.2", "1.11", "1.11.2", "1.12", "1.12.1", "1.12.2", "1.13.2", "1.14.2", "1.14.3", "1.14.4", "1.15", "1.15.1", "1.15.2", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5", "1.17.1", "1.18", "1.18.1", "1.18.2", "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.6", "1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3")

stonecutter active requestedForgeVersions.first()

stonecutter parameters {
    constants["loader_fabric"] = false
    constants["loader_neoforge"] = true
    constants["loader_forge"] = true
}
