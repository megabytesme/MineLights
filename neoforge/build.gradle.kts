import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    `java-library`
    id("dev.kikugie.stonecutter")
    id("net.neoforged.gradle.userdev")
    id("me.modmuss50.mod-publish-plugin") version "0.8.+"
}

val modId = property("mod.id").toString()
val modName = property("mod.name").toString()
val modVersion = property("mod.version").toString()
val modGroup = property("mod.group").toString()
val modDescription = property("mod.description").toString()
val modAuthors = property("mod.authors").toString()
val modHomepage = property("mod.homepage").toString()
val modSources = property("mod.sources").toString()
val modIssues = property("mod.issues").toString()
val modLicense = property("mod.license").toString()
val modIcon = property("mod.icon").toString()
val modEnvironment = property("mod.environment").toString()
val mcVersion = stonecutter.current.version
val buildVersion = "$modVersion+$mcVersion-neoforge"
val mcDep = property("mod.mc_dep").toString()
val neoforgeVersion = property("deps.neoforge").toString()
val clothConfigVersion = when {
    stonecutter.eval(mcVersion, ">=26.1") -> "26.1.154"
    stonecutter.eval(mcVersion, ">=1.21.11") -> "21.11.153"
    stonecutter.eval(mcVersion, ">=1.21.6") -> "20.0.149"
    stonecutter.eval(mcVersion, ">=1.21.4") -> "19.0.147"
    stonecutter.eval(mcVersion, ">=1.21.2") -> "17.0.144"
    stonecutter.eval(mcVersion, ">=1.21.1") -> "16.0.143"
    stonecutter.eval(mcVersion, ">=1.20.6") -> "15.0.140"
    stonecutter.eval(mcVersion, ">=1.20.5") -> "14.0.139"
    stonecutter.eval(mcVersion, ">=1.20.4") -> "13.0.138"
    else -> "12.0.137"
}
val clothConfigDependency = if (stonecutter.eval(mcVersion, ">=26.1")) {
    "curse.maven:${property("deps.cloth_config")}"
} else {
    "me.shedaniel.cloth:cloth-config-neoforge:$clothConfigVersion"
}

version = buildVersion
group = modGroup
base.archivesName.set(modId)

val syncSharedSources = rootProject.tasks.findByName("syncSharedSources") ?: rootProject.tasks.register("syncSharedSources") {
    doLast {
        val generatedPaths = listOf(
            "src/main/java/megabytesme/minelights/model",
            "src/main/java/megabytesme/minelights/network",
            "src/main/java/megabytesme/minelights/rgb",
            "src/main/java/megabytesme/minelights/config/CompassPriority.java",
            "src/main/java/megabytesme/minelights/config/DimmingMode.java",
            "src/main/java/megabytesme/minelights/config/MineLightsConfig.java",
            "src/main/resources/assets/minelights",
            "src/main/resources/minelights.mixins.json"
        )

        generatedPaths.forEach { relativePath ->
            rootProject.file(relativePath).deleteRecursively()
        }

        copy {
            from(rootProject.file("../common/src/main/java/megabytesme/minelights/model"))
            into(rootProject.file("src/main/java/megabytesme/minelights/model"))
        }
        copy {
            from(rootProject.file("../common/src/main/java/megabytesme/minelights/network"))
            into(rootProject.file("src/main/java/megabytesme/minelights/network"))
        }
        copy {
            from(rootProject.file("../common/src/main/java/megabytesme/minelights/rgb"))
            into(rootProject.file("src/main/java/megabytesme/minelights/rgb"))
        }
        copy {
            from(rootProject.file("../common/src/main/java/megabytesme/minelights/config/CompassPriority.java"))
            into(rootProject.file("src/main/java/megabytesme/minelights/config"))
        }
        copy {
            from(rootProject.file("../common/src/main/java/megabytesme/minelights/config/DimmingMode.java"))
            into(rootProject.file("src/main/java/megabytesme/minelights/config"))
        }
        copy {
            from(rootProject.file("../common/src/main/java/megabytesme/minelights/config/MineLightsConfig.java"))
            into(rootProject.file("src/main/java/megabytesme/minelights/config"))
        }
        copy {
            from(rootProject.file("../common/src/main/resources/assets/minelights"))
            into(rootProject.file("src/main/resources/assets/minelights"))
        }
        copy {
            from(rootProject.file("../common/src/main/resources/minelights.mixins.json"))
            into(rootProject.file("src/main/resources"))
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.parchmentmc.org")
    maven("https://maven.shedaniel.me/")
    maven("https://www.cursemaven.com")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    implementation("net.neoforged:neoforge:$neoforgeVersion")
    compileOnly(clothConfigDependency)
    runtimeOnly(clothConfigDependency)
}

runs {
    configureEach {
        modSource(sourceSets.main.get())
        workingDirectory = project.file("run")
    }
}

java {
    withSourcesJar()

    val javaVersion = when {
        stonecutter.eval(mcVersion, ">=26.1") -> 25
        stonecutter.eval(mcVersion, ">=1.20.5") -> 21
        else -> 17
    }

    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
}

sourceSets.named("main") {
    java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/stonecutter/main/java")))
    if (stonecutter.eval(mcVersion, ">=26.1")) {
        java.exclude("megabytesme/minelights/config/LiveLogEntry.java")
        java.exclude("megabytesme/minelights/config/LiveStatusEntry.java")
    }
}

tasks.named("stonecutterPrepare") {
    dependsOn(syncSharedSources)
}

tasks.named("stonecutterGenerate") {
    dependsOn(syncSharedSources)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(
        when {
            stonecutter.eval(mcVersion, ">=26.1") -> 25
            stonecutter.eval(mcVersion, ">=1.20.5") -> 21
            else -> 17
        }
    )
    dependsOn(syncSharedSources)
    dependsOn("stonecutterPrepare")
    dependsOn("stonecutterGenerate")
}

tasks.processResources {
    val props = mapOf(
        "id" to modId,
        "name" to modName,
        "version" to modVersion,
        "authors" to modAuthors,
        "description" to modDescription,
        "homepage" to modHomepage,
        "sources" to modSources,
        "issues" to modIssues,
        "license" to modLicense,
        "icon" to modIcon,
        "environment" to modEnvironment,
        "minecraft" to mcDep,
        "forge_version" to neoforgeVersion
    )

    props.forEach(inputs::property)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
        expand(props)
    }

    dependsOn(syncSharedSources)
    dependsOn("stonecutterPrepare")
    dependsOn("stonecutterGenerate")
}

tasks.named<Jar>("jar") {
    dependsOn(syncSharedSources)
    dependsOn("stonecutterPrepare")
    dependsOn("stonecutterGenerate")
    from("src/main/resources") {
        include("META-INF/mods.toml")
    }
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(syncSharedSources)
    dependsOn("stonecutterPrepare")
    dependsOn("stonecutterGenerate")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(layout.buildDirectory.dir("generated/stonecutter/main/java"))
    from("src/main/resources") {
        include("META-INF/mods.toml")
    }
}

if (stonecutter.current.isActive) {
    rootProject.tasks.register("buildActive") {
        group = "project"
        dependsOn(tasks.named("build"))
    }
}

publishMods {
    file = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    additionalFiles.from(tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile })

    displayName = "$modName $modVersion for $mcVersion (NeoForge)"
    version = modVersion
    changelog = rootProject.file("../CHANGELOG.md").takeIf { it.exists() }?.readText() ?: "No changelog provided."
    type = STABLE
    modLoaders.add("neoforge")
    dryRun = true

    modrinth {
        projectId = property("publish.modrinth").toString()
        accessToken = ""
        val targets = property("mod.mc_targets").toString().split(" ")
        minecraftVersions.addAll(targets)
    }
}
