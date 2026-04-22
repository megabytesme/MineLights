pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7.10"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        fun parseVersion(version: String): List<Int> =
            version.split('.').map { part -> part.toIntOrNull() ?: Int.MAX_VALUE }

        val requestedVersions = providers.gradleProperty("neoforge.versions")
            .orNull
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
        val versionDirs = file("versions")
            .listFiles()
            ?.filter { it.isDirectory && it.resolve("gradle.properties").exists() }
            ?.filter { versionDir ->
                requestedVersions?.contains(versionDir.name) ?: true
            }
            ?.map { it.name }
            ?.sortedWith { left, right ->
                val leftParts = parseVersion(left)
                val rightParts = parseVersion(right)
                val limit = maxOf(leftParts.size, rightParts.size)
                for (index in 0 until limit) {
                    val comparison = (leftParts.getOrElse(index) { 0 })
                        .compareTo(rightParts.getOrElse(index) { 0 })
                    if (comparison != 0) {
                        return@sortedWith comparison
                    }
                }
                left.compareTo(right)
            }
            ?: emptyList()

        require(versionDirs.isNotEmpty()) {
            "No NeoForge version definitions found in ${file("versions").absolutePath}"
        }

        versions(*versionDirs.toTypedArray())
        vcsVersion = versionDirs.last()
    }
}

rootProject.name = "MineLights-NeoForge"
