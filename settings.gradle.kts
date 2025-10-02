pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net")
		maven("https://maven.neoforged.net/releases")
		maven("https://maven.architectury.dev")
		maven("https://maven.kikugie.dev/snapshots")
		maven("https://maven.kikugie.dev/releases")
		maven("https://repo.polyfrost.cc/releases")
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.7.5"
}

stonecutter {
	kotlinController = true
	centralScript = "build.gradle.kts"

	create(rootProject) {
		fun mc(mcVersion: String, loaders: Iterable<String>) {
			for (loader in loaders) {
				vers("$mcVersion-$loader", mcVersion)
			}
		}

        mc("1.14.3", listOf("fabric"))
        mc("1.14.4", listOf("fabric"))
        mc("1.15", listOf("fabric"))
        mc("1.16", listOf("fabric"))
        mc("1.16.2", listOf("fabric"))
        mc("1.17", listOf("fabric"))
        mc("1.19", listOf("fabric"))
        mc("1.20", listOf("fabric"))
        mc("1.20.5", listOf("fabric"))
        mc("1.21.2", listOf("fabric"))
        mc("1.21.6", listOf("fabric"))
        mc("1.21.8", listOf("fabric"))

		vcsVersion = "1.21.8-fabric"
	}
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs")
    }
}

rootProject.name = "MineLights"
