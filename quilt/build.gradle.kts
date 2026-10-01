import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    `maven-publish`
    id("net.fabricmc.fabric-loom") apply false
    id("net.fabricmc.fabric-loom-remap") apply false
    id("me.modmuss50.mod-publish-plugin")
}

val isUnobfuscatedVersion = stonecutter.eval(stonecutter.current.version, ">=26.1")
val usesModrinthModDependencies = stonecutter.eval(stonecutter.current.version, ">=26.3") ||
        stonecutter.current.version == "1.21.11"
val usesJava8Runtime = stonecutter.eval(stonecutter.current.version, "<1.17")

apply(plugin = if (isUnobfuscatedVersion) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap")

version = "${property("mod.version")}+${stonecutter.current.version}-quilt"
base.archivesName = property("mod.id") as String

val syncSharedSources = rootProject.tasks.findByName("syncSharedSources") ?: rootProject.tasks.register("syncSharedSources") {
    doLast {
        val sharedPaths = listOf(
            "src/main/java/megabytesme/minelights/accessor",
            "src/main/java/megabytesme/minelights/effects",
            "src/main/java/megabytesme/minelights/mixin",
            "src/main/java/megabytesme/minelights/model",
            "src/main/java/megabytesme/minelights/network",
            "src/main/java/megabytesme/minelights/rgb",
            "src/main/java/megabytesme/minelights/runtime",
            "src/main/java/megabytesme/minelights/config/CompassPriority.java",
            "src/main/java/megabytesme/minelights/config/DimmingMode.java",
            "src/main/java/megabytesme/minelights/config/MineLightsConfig.java",
            "src/main/java/megabytesme/minelights/config/SimpleJsonConfig.java",
            "src/main/java/megabytesme/minelights/MineLightsClient.java",
            "src/main/resources/assets/minelights",
            "src/main/resources/minelights.mixins.json"
        )

        sharedPaths.forEach { relativePath ->
            rootProject.file(relativePath).deleteRecursively()
        }

        val sharedJavaPaths = listOf(
            "accessor", "effects", "mixin", "model", "network", "rgb", "runtime"
        )
        sharedJavaPaths.forEach { path ->
            copy {
                from(rootProject.file("../common/src/main/java/megabytesme/minelights/$path"))
                into(rootProject.file("src/main/java/megabytesme/minelights/$path"))
            }
        }

        listOf("CompassPriority.java", "DimmingMode.java", "MineLightsConfig.java", "SimpleJsonConfig.java").forEach { fileName ->
            copy {
                from(rootProject.file("../common/src/main/java/megabytesme/minelights/config/$fileName"))
                into(rootProject.file("src/main/java/megabytesme/minelights/config"))
            }
        }
        val fabricClientFile = rootProject.file("../fabric/src/main/java/megabytesme/minelights/MineLightsClient.java")
        val fabricClient = fabricClientFile.readText().replace("\r\n", "\n")
        val fabricServerExePath = "return getGameDir().resolve(\"mods\").resolve(\"MineLights\").resolve(\"MineLights.exe\");"
        check(fabricClient.contains(fabricServerExePath)) {
            "Could not find the Fabric server executable path while preparing Quilt sources."
        }
        val initializer = Regex("(?s)    @Override\n    public void onInitializeClient\\(\\) \\{.*?\n    \\}\n")
        check(initializer.containsMatchIn(fabricClient)) {
            "Could not find the Fabric client initializer while preparing Quilt sources."
        }
        val quiltClient = fabricClient
            .replace("import net.fabricmc.api.ClientModInitializer;\n", "")
            .replace("import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;\n", "")
            .replace("import net.fabricmc.loader.api.FabricLoader;\n", "")
            .replace("public class MineLightsClient implements ClientModInitializer {", "public class MineLightsClient {")
            .replace("private static String resolvedModLoader = \"fabric\";", "private static String resolvedModLoader = \"quilt\";")
            .replace(initializer, "")
            .replace(fabricServerExePath, "return getGameDir().resolve(\"MineLights\").resolve(\"MineLights.exe\");")
        check(!quiltClient.contains("net.fabricmc.loader.api.FabricLoader") &&
                !quiltClient.contains("net.fabricmc.api.ClientModInitializer")) {
            "Fabric loader entrypoint references remain in the generated Quilt client source."
        }
        rootProject.file("src/main/java/megabytesme/minelights/MineLightsClient.java").writeText(quiltClient)
        copy {
            from(rootProject.file("../common/src/main/resources/assets/minelights"))
            into(rootProject.file("src/main/resources/assets/minelights"))
        }
        copy {
            from(rootProject.file("../common/src/main/resources/minelights.mixins.json"))
            into(rootProject.file("src/main/resources"))
        }
        listOf("LiveStatusEntry.java", "ModMenuIntegration.java").forEach { fileName ->
            copy {
                from(rootProject.file("../fabric/src/main/java/megabytesme/minelights/config/$fileName"))
                into(rootProject.file("src/main/java/megabytesme/minelights/config"))
            }
        }
    }
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }

    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    strictMaven("https://maven.fabricmc.net/", "FabricMC", "net.fabricmc")
    strictMaven("https://maven.quiltmc.org/repository/release", "QuiltMC", "org.quiltmc")
    strictMaven("https://maven.shedaniel.me/", "Shedaniel", "me.shedaniel")
    strictMaven("https://maven.terraformersmc.com/releases", "TerraformersMC", "com.terraformersmc")
}

dependencies {
    add("minecraft", "com.mojang:minecraft:${stonecutter.current.version}")

    val quiltLoader = "org.quiltmc:quilt-loader:${property("deps.quilt_loader")}"
    if (!isUnobfuscatedVersion) {
        add("mappings", "net.fabricmc:yarn:${property("deps.yarn")}:v2")
        add("modCompileOnly", quiltLoader)
        add("modCompileOnly", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
        if (usesModrinthModDependencies) {
            add("modCompileOnly", "maven.modrinth:cloth-config:${property("deps.cloth_config")}")
            add("modCompileOnly", "maven.modrinth:modmenu:${property("deps.modmenu")}")
        } else {
            add("modCompileOnly", "curse.maven:${property("deps.cloth_config")}")
            add("modCompileOnly", "curse.maven:${property("deps.modmenu")}")
        }
    } else {
        compileOnly(quiltLoader)
        compileOnly("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
        if (usesModrinthModDependencies) {
            compileOnly("maven.modrinth:cloth-config:${property("deps.cloth_config")}")
            compileOnly("maven.modrinth:modmenu:${property("deps.modmenu")}")
        } else {
            compileOnly("curse.maven:${property("deps.cloth_config")}")
            compileOnly("curse.maven:${property("deps.modmenu")}")
        }
    }
}

java {
    withSourcesJar()

    val requiresJava25 = stonecutter.eval(stonecutter.current.version, ">=26.1")
    val requiresJava21 = stonecutter.eval(stonecutter.current.version, ">=1.20.6")
    val requiresJava17 = stonecutter.eval(stonecutter.current.version, ">=1.18")
    val javaVersion = when {
        requiresJava25 -> JavaVersion.VERSION_25
        requiresJava21 -> JavaVersion.VERSION_21
        requiresJava17 -> JavaVersion.VERSION_17
        else -> JavaVersion.VERSION_1_8
    }

    if (requiresJava25) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }
    targetCompatibility = javaVersion
    sourceCompatibility = javaVersion
}

tasks.withType<JavaCompile>().configureEach {
    if (usesJava8Runtime) {
        options.release.set(8)
    }
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/stonecutter/main/java")))
    }
}

val mcVersion = stonecutter.current.version
fun prop(name: String) = project.property(name).toString()

tasks {
    named("stonecutterPrepare") { dependsOn(syncSharedSources) }
    named("stonecutterGenerate") { dependsOn(syncSharedSources) }
    named("compileJava") { dependsOn(syncSharedSources, "stonecutterGenerate") }
    named("processResources") { dependsOn(syncSharedSources) }
    named("sourcesJar") {
        dependsOn(syncSharedSources, "stonecutterGenerate")
        outputs.upToDateWhen { false }
        (this as Jar).duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        (this as Jar).from(layout.buildDirectory.dir("generated/stonecutter/main/java"))
    }
    matching { it.name == "remapSourcesJar" }.configureEach { dependsOn("sourcesJar") }

    processResources {
        val minecraftTargets = prop("mod.mc_targets").split(Regex("\\s+"))
        val props = mapOf(
            "id" to prop("mod.id"),
            "name" to prop("mod.name"),
            "version" to prop("mod.version"),
            "description" to prop("mod.description"),
            "authors" to prop("mod.authors"),
            "license" to prop("mod.license"),
            "homepage" to prop("mod.homepage"),
            "sources" to prop("mod.sources"),
            "issues" to prop("mod.issues"),
            "icon" to prop("mod.icon"),
            "minecraft_versions_json" to minecraftTargets.joinToString(", ") { "\"=$it\"" },
            "quilt_loader_version_range" to if (isUnobfuscatedVersion) ">=0.31.0-beta.4" else ">=0.16.0"
        )

        inputs.properties(props)
        val metadataTemplate = if (isUnobfuscatedVersion) "quilt.mod.json.named" else "quilt.mod.json.intermediary"
        from(rootProject.file("src/main/metadata")) {
            include(metadataTemplate)
            rename { "quilt.mod.json" }
            expand(props)
        }
    }
}

val mainPublishJar = if (isUnobfuscatedVersion) tasks.named<Jar>("jar") else tasks.named<Jar>("remapJar")
val sourcesPublishJar = if (isUnobfuscatedVersion) tasks.named<Jar>("sourcesJar") else tasks.named<Jar>("remapSourcesJar")
val modrinthToken = providers.gradleProperty("modrinthToken")
    .orElse(providers.environmentVariable("MODRINTH_TOKEN"))
    .getOrElse("")
val modrinthDryRun = providers.gradleProperty("publish.dryRun")
    .map(String::toBoolean)
    .getOrElse(true)

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(mainPublishJar.flatMap { it.archiveFile })
    from(sourcesPublishJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/${prop("mod.version")}"))
    dependsOn(mainPublishJar, sourcesPublishJar)
}

publishMods {
    file = mainPublishJar.flatMap { it.archiveFile }
    additionalFiles.from(sourcesPublishJar.flatMap { it.archiveFile })
    displayName = "${prop("mod.name")} ${prop("mod.version")} for $mcVersion (Quilt)"
    version = "${prop("mod.version")}+${mcVersion}-quilt"
    changelog = rootProject.file("../CHANGELOG.md").readText()
    type = STABLE
    modLoaders.add("quilt")
    dryRun = modrinthDryRun

    modrinth {
        projectId = prop("publish.modrinth")
        accessToken = modrinthToken
        minecraftVersions.addAll(prop("mod.mc_targets").split(" "))
    }
}
