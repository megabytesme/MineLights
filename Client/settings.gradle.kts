pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7.10"
}

stonecutter {
    create(rootProject) {
        versions("1.14.3", "1.14.4", "1.15", "1.16", "1.16.2", "1.17", "1.19", "1.20", "1.20.5")
        vcsVersion = "1.20.5"
    }
}

rootProject.name = "MineLights"