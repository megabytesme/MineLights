plugins {
    `maven-publish`
    id("net.fabricmc.fabric-loom") apply false
    id("net.fabricmc.fabric-loom-remap") apply false
    id("me.modmuss50.mod-publish-plugin")
}

val isUnobfuscatedVersion = stonecutter.eval(stonecutter.current.version, ">=26.1")

apply(plugin = if (isUnobfuscatedVersion) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap")

version = "${property("mod.version")}+${stonecutter.current.version}"
base.archivesName = property("mod.id") as String

repositories {
    /**
     * Restricts dependency search of the given [groups] to the [maven URL][url],
     * improving the setup speed.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    strictMaven("https://maven.fabricmc.net/", "FabricMC", "net.fabricmc")
    strictMaven("https://maven.shedaniel.me/", "Shedaniel", "me.shedaniel")
    strictMaven("https://maven.terraformersmc.com/releases", "TerraformersMC", "com.terraformersmc")
}

dependencies {
    add("minecraft", "com.mojang:minecraft:${stonecutter.current.version}")
    if (!isUnobfuscatedVersion) {
        add("mappings", "net.fabricmc:yarn:${property("deps.yarn")}:v2")
        add("modImplementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    } else {
        implementation("net.fabricmc:fabric-loader:0.18.4")
        implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    }

    if (!isUnobfuscatedVersion) {
        add("modCompileOnly", "curse.maven:${property("deps.cloth_config")}")
        add("modCompileOnly", "curse.maven:${property("deps.modmenu")}")
    }
}

java {
    withSourcesJar()
    val requiresJava25: Boolean = stonecutter.eval(stonecutter.current.version, ">=26.1")
    val requiresJava21: Boolean = stonecutter.eval(stonecutter.current.version, ">=1.20.6")
    val requiresJava17: Boolean = stonecutter.eval(stonecutter.current.version, ">=1.18")
    val javaVersion: JavaVersion =
        if (requiresJava25) JavaVersion.VERSION_25
        else if (requiresJava21) JavaVersion.VERSION_21
        else if (requiresJava17) JavaVersion.VERSION_17
        else JavaVersion.VERSION_1_8
    targetCompatibility = javaVersion
    sourceCompatibility = javaVersion
}

if (isUnobfuscatedVersion) {
    sourceSets.named("main") {
        java.exclude("megabytesme/minelights/config/ModMenuIntegration.java")
        java.exclude("megabytesme/minelights/config/LiveLogEntry.java")
        java.exclude("megabytesme/minelights/config/LiveStatusEntry.java")
    }
}

tasks {
    processResources {
        inputs.property("id", project.property("mod.id"))
        inputs.property("name", project.property("mod.name"))
        inputs.property("version", project.property("mod.version"))
        inputs.property("minecraft", project.property("mod.mc_dep"))
        inputs.property("description", project.property("mod.description"))
        inputs.property("authors", project.property("mod.authors"))
        inputs.property("license", project.property("mod.license"))
        inputs.property("homepage", project.property("mod.homepage"))
        inputs.property("sources", project.property("mod.sources"))
        inputs.property("issues", project.property("mod.issues"))
        inputs.property("icon", project.property("mod.icon"))
        inputs.property("environment", project.property("mod.environment"))
        inputs.property("modmenu_entrypoint_class", if (isUnobfuscatedVersion) "megabytesme.minelights.config.ModMenuIntegrationStub" else "megabytesme.minelights.config.ModMenuIntegration")
        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "minecraft" to project.property("mod.mc_dep"),
            "description" to project.property("mod.description"),
            "authors" to project.property("mod.authors"),
            "license" to project.property("mod.license"),
            "homepage" to project.property("mod.homepage"),
            "sources" to project.property("mod.sources"),
            "issues" to project.property("mod.issues"),
            "icon" to project.property("mod.icon"),
            "environment" to project.property("mod.environment"),
            "modrinth" to project.property("mod.modrinth"),
            "kofi" to project.property("mod.kofi"),
            "discord" to project.property("mod.discord"),
            "modmenu_entrypoint_class" to if (isUnobfuscatedVersion) "megabytesme.minelights.config.ModMenuIntegrationStub" else "megabytesme.minelights.config.ModMenuIntegration"
        )

        filesMatching("fabric.mod.json") { expand(props) }
    }

    // Builds the version into a shared folder in `build/libs/${mod version}/`
    register<Copy>("buildAndCollect") {
        group = "build"
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}

val mcVersion = stonecutter.current.version
fun prop(name: String) = project.property(name).toString()

publishMods {
    file = rootProject.layout.buildDirectory.file("tmp/publish/${project.name}.jar")
    additionalFiles.from(rootProject.layout.buildDirectory.file("tmp/publish/${project.name}-sources.jar"))
    displayName = "${prop("mod.name")} ${prop("mod.version")} for $mcVersion"
    version = prop("mod.version")
    changelog = rootProject.file("CHANGELOG.md").readText()
    type = STABLE
    modLoaders.add("fabric")

    dryRun = true

    modrinth {
        projectId = property("publish.modrinth").toString()
        accessToken = ""
        val targets = property("mod.mc_targets").toString().split(" ")
        minecraftVersions.addAll(targets)
        requires {
            slug = "fabric-api"
            version = property("deps.fabric_api").toString()
        }
    }

/*
    curseforge {
        projectId = property("publish.curseforge").toString()
        accessToken = ""
        minecraftVersions.add(mcVersion)
        requires {
            slug = "fabric-api"
        }
    }
    */
}

publishing {
    repositories {
        maven("...") {
            name = "..."
            credentials(PasswordCredentials::class.java)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "${prop("mod.group")}.${prop("mod.id")}"
            artifactId = prop("mod.version")
            version = mcVersion

            from(components["java"])
        }
    }
}
