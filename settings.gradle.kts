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

        mc("1.16.5", listOf("forge"))
        mc("1.17", listOf("forge"))
        mc("1.19", listOf("forge"))
        mc("1.20", listOf("forge"))
        mc("1.20.5", listOf("forge"))
        mc("1.21.2", listOf("forge"))
        mc("1.21.6", listOf("forge"))
        mc("1.21.8", listOf("forge"))

		vcsVersion = "1.21.8-forge"
	}
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs")
    }
}

rootProject.name = "MineLights"
