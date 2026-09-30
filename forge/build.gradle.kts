import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import net.minecraftforge.renamer.gradle.RenameJar
import java.io.RandomAccessFile
import java.util.zip.GZIPInputStream

plugins {
    `java-library`
    id("dev.kikugie.stonecutter")
    id("net.minecraftforge.gradle") version "[7.0.29,8.0)"
    id("net.minecraftforge.renamer") version "1.1.5"
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
val modIcon = property("mod.icon").toString().substringAfterLast('/')
val mcVersion = stonecutter.current.version
val forgeVersion = property("deps.forge").toString()
val forgeVersionRange = property("forge.version_range").toString()
val forgeLoaderRange = property("forge.loader_range").toString()
val buildVersion = "$modVersion+$mcVersion-forge"
val usesLegacyForgeMetadata = mcVersion in setOf("1.7.2", "1.7.10", "1.8", "1.8.8", "1.8.9", "1.9", "1.9.4", "1.10", "1.10.2", "1.11", "1.11.2", "1.12", "1.12.1", "1.12.2")
val requiresSrgRuntimeMappings = stonecutter.eval(mcVersion, "<=1.20.4")
val mappingChannel = when (mcVersion) {
    "1.7.2", "1.7.10", "1.8", "1.8.8", "1.8.9", "1.9", "1.9.4", "1.10", "1.10.2", "1.11", "1.11.2", "1.12", "1.12.1", "1.12.2" -> "stable"
    "1.13.2", "1.14.2", "1.14.3", "1.16.1" -> "snapshot"
    else -> "official"
}
val mappingVersion = when (mcVersion) {
    "1.7.10" -> "12-1.7.10"
    // MCP only published the stable-12 archive against 1.7.10; reuse it for the shared 1.7.2 SRG namespace.
    "1.7.2" -> "12-1.7.10"
    "1.8" -> "18-1.8"
    "1.8.8" -> "20-1.8.8"
    "1.8.9" -> "22-1.8.9"
    "1.9" -> "24-1.9"
    "1.9.4" -> "26-1.9.4"
    "1.10", "1.10.2" -> "29-1.10.2"
    "1.11", "1.11.2" -> "32-1.11"
    "1.12", "1.12.1", "1.12.2" -> "39-1.12"
    "1.13.2" -> "20190320-1.13.2"
    "1.14.2" -> "20190624-1.14.2"
    "1.14.3" -> "20190719-1.14.3"
    "1.16.1" -> "20200723-1.16.1"
    else -> mcVersion
}
val mcDependency = property("mod.mc_dep").toString()
val clothConfigVersion = property("deps.cloth_config").toString()
val usesClothConfig = stonecutter.eval(mcVersion, ">=1.16.3") && mcVersion !in setOf("1.21.4", "26.3")
val clothConfigModId = if (stonecutter.eval(mcVersion, ">=1.17.1")) "cloth_config" else "cloth-config"
val clothConfigProject = if (stonecutter.eval(mcVersion, ">=1.21.5")) "cloth-config-forge" else "cloth-config"
val clothConfigDependency = if (mcVersion == "1.16.2") {
    "maven.modrinth:9s6osm5g:tR748cRj"
} else if (mcVersion == "1.18" || mcVersion == "1.18.1" || mcVersion == "1.18.2") {
    "maven.modrinth:9s6osm5g:ZbWG3eJW"
} else if (mcVersion == "1.16.4" || mcVersion == "1.16.5") {
    "maven.modrinth:9s6osm5g:i0ExoqTD"
} else if (mcVersion == "1.17.1") {
    "maven.modrinth:9s6osm5g:GH6kNTCk"
} else {
    "maven.modrinth:$clothConfigProject:$clothConfigVersion"
}
val clothConfigRuntime = property("forge.cloth_config_runtime").toString().toBoolean()
val javaVersion = when {
    stonecutter.eval(mcVersion, ">=26.1") -> 25
    stonecutter.eval(mcVersion, ">=1.20.5") -> 21
    stonecutter.eval(mcVersion, "<=1.16.5") -> 8
    mcVersion == "1.17.1" -> 16
    else -> 17
}

version = buildVersion
group = modGroup
base.archivesName.set(modId)

val syncSharedSources = rootProject.tasks.findByName("syncSharedSources") ?: rootProject.tasks.register("syncSharedSources") {
    doLast {
        val generatedPaths = listOf(
            "src/main/java/megabytesme/minelights/MineLightsClient.java",
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
            "src/main/resources/assets/minelights",
            "src/main/resources/minelights.mixins.json"
        )

        generatedPaths.forEach { rootProject.file(it).deleteRecursively() }

        fun copyShared(fromPath: String, toPath: String) {
            copy {
                from(rootProject.file(fromPath))
                into(rootProject.file(toPath))
            }
        }

        copyShared("../common/src/main/java/megabytesme/minelights/MineLightsClient.java", "src/main/java/megabytesme/minelights")
        copyShared("../common/src/main/java/megabytesme/minelights/accessor", "src/main/java/megabytesme/minelights/accessor")
        copyShared("../common/src/main/java/megabytesme/minelights/effects", "src/main/java/megabytesme/minelights/effects")
        copyShared("../common/src/main/java/megabytesme/minelights/mixin", "src/main/java/megabytesme/minelights/mixin")
        copyShared("../common/src/main/java/megabytesme/minelights/model", "src/main/java/megabytesme/minelights/model")
        copyShared("../common/src/main/java/megabytesme/minelights/network", "src/main/java/megabytesme/minelights/network")
        copyShared("../common/src/main/java/megabytesme/minelights/rgb", "src/main/java/megabytesme/minelights/rgb")
        copyShared("../common/src/main/java/megabytesme/minelights/runtime", "src/main/java/megabytesme/minelights/runtime")
        copyShared("overrides/src/main/java/megabytesme/minelights/runtime/PlayerDataCollector.java", "src/main/java/megabytesme/minelights/runtime")
        copyShared("../common/src/main/java/megabytesme/minelights/config/SimpleJsonConfig.java", "src/main/java/megabytesme/minelights/config")
        copyShared("../common/src/main/java/megabytesme/minelights/config/CompassPriority.java", "src/main/java/megabytesme/minelights/config")
        copyShared("../common/src/main/java/megabytesme/minelights/config/DimmingMode.java", "src/main/java/megabytesme/minelights/config")
        copyShared("../common/src/main/java/megabytesme/minelights/config/MineLightsConfig.java", "src/main/java/megabytesme/minelights/config")
        copyShared("../common/src/main/resources/assets/minelights", "src/main/resources/assets/minelights")
        copyShared("../common/src/main/resources/minelights.mixins.json", "src/main/resources")
    }
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    maven("https://api.modrinth.com/maven")
    maven("https://maven.shedaniel.me/")
    maven("https://maven.minecraftforge.net")
    maven("https://repo.spongepowered.org/repository/maven-public/")
    mavenCentral()
}

minecraft {
    mappings(mappingChannel, mappingVersion)
    useDefaultAccessTransformer()
    runs {
        configureEach {
            workingDir = project.file("run/$mcVersion")
            systemProperty("forge.logging.console.level", "info")
            if (mcVersion == "1.7.2") {
                // This development runtime uses a remapped Minecraft jar, which legacy FML otherwise rejects.
                systemProperty("fml.ignoreInvalidMinecraftCertificates", "true")
            }
            if (javaVersion >= 9 && stonecutter.eval(mcVersion, "<=1.18.2")) {
                jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
            }
        }
        register("client") {
            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:$mcVersion-$forgeVersion"))
    if (usesClothConfig) {
        compileOnly(clothConfigDependency)
    }
    if (clothConfigRuntime && usesClothConfig) {
        runtimeOnly(clothConfigDependency)
    }
    if (mcVersion in setOf("1.14.4", "1.15", "1.15.1", "1.15.2")) {
        compileOnly("org.spongepowered:mixin:0.8.2")
        annotationProcessor("org.spongepowered:mixin:0.8.2:processor")
        if (mcVersion != "1.15.2") {
            runtimeOnly("maven.modrinth:mixinbootstrap:hOGSWOX8")
        }
    }
    if (stonecutter.eval(mcVersion, ">=1.21.6")) {
        annotationProcessor("net.minecraftforge:eventbus-validator:7.0.5")
    }
}

java {
    withSourcesJar()
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

sourceSets.named("main") {
    java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/stonecutter/main/java")))
    if (!usesClothConfig) {
        java.exclude("megabytesme/minelights/config/ModMenuIntegration.java")
        java.exclude("megabytesme/minelights/config/LiveStatusEntry.java")
    }
    if (stonecutter.eval(mcVersion, ">=1.13.2")) {
        java.exclude("megabytesme/minelights/MineLightsLegacyGuiFactory.java")
    }
    if (stonecutter.eval(mcVersion, ">=1.13.2")) {
        java.exclude("megabytesme/minelights/MineLightsLegacyConfigScreen.java")
    }
    if (stonecutter.eval(mcVersion, "<1.13.2") || stonecutter.eval(mcVersion, ">=1.16.3")) {
        java.exclude("megabytesme/minelights/MineLightsTransitionalConfigScreen.java")
    }
    if (stonecutter.eval(mcVersion, "<1.21.4")) {
        java.exclude("megabytesme/minelights/MineLightsModernConfigScreen.java")
    }
    if (mcVersion == "1.13.2" || mcVersion == "1.14.2" || mcVersion == "1.14.3") {
        java.exclude("megabytesme/minelights/mixin/**")
    }
    if (usesLegacyForgeMetadata) {
        java.exclude("megabytesme/minelights/mixin/**")
        resources.exclude("META-INF/mods.toml")
        resources.exclude("minelights.mixins.json")
    }
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
    if (javaVersion >= 9) {
        options.release.set(javaVersion)
    }
    dependsOn(syncSharedSources)
    dependsOn("stonecutterPrepare")
    dependsOn("stonecutterGenerate")
    if (stonecutter.eval(mcVersion, "<=1.7.2")) {
        // Forge 1.7.2 and older use ASM 4, which rejects Java 8's class-file version.
        // Keep the Java 8 bytecode (including invokedynamic) but mark it as Java 7 so ASM 4 can scan it.
        doLast {
            destinationDirectory.get().asFile.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { classFile ->
                    RandomAccessFile(classFile, "rw").use { classBytes ->
                        if (classBytes.length() >= 8) {
                            val header = ByteArray(8)
                            classBytes.readFully(header)
                            val isClassFile = header[0] == 0xCA.toByte() && header[1] == 0xFE.toByte() &&
                                header[2] == 0xBA.toByte() && header[3] == 0xBE.toByte()
                            val majorVersion = ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
                            if (isClassFile && majorVersion == 52) {
                                classBytes.seek(6)
                                classBytes.writeShort(51)
                            }
                        }
                    }
                }
        }
    }
    if (mcVersion in setOf("1.14.4", "1.15", "1.15.1", "1.15.2")) {
        val srgMappings = minecraft.dependency.toSrgFile
        inputs.file(srgMappings)
        doFirst {
            val sourceMappings = project.file(srgMappings.get())
            val mappings = if (sourceMappings.name.endsWith(".gz")) {
                val uncompressed = layout.buildDirectory.file("generated/mixin/official-srg.tsrg").get().asFile
                uncompressed.parentFile.mkdirs()
                sourceMappings.inputStream().use { input ->
                    GZIPInputStream(input).use { gzip ->
                        gzip.bufferedReader().use { reader ->
                            val header = reader.readLine()
                            uncompressed.bufferedWriter().use { writer ->
                                if (header?.startsWith("tsrg2 ") != true) {
                                    writer.appendLine(header ?: "")
                                }
                                reader.copyTo(writer)
                            }
                        }
                    }
                }
                uncompressed
            } else {
                sourceMappings
            }
            val compileTemp = temporaryDir.apply { mkdirs() }
            options.compilerArgs.addAll(listOf(
                "-AreobfTsrgFile=${mappings.absolutePath}",
                "-AoutTsrgFile=${compileTemp.resolve("compileJava-mappings.tsrg").absolutePath}",
                "-AoutRefMapFile=${compileTemp.resolve("minelights.refmap.json").absolutePath}",
                "-AmappingTypes=tsrg",
                "-AdefaultObfuscationEnv=searge"
            ))
        }
    }
}

tasks.processResources {
    val templateProperties = mapOf(
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
        "minecraft" to mcDependency,
        "forge_version_range" to forgeVersionRange,
        "loader_version_range" to forgeLoaderRange,
        "minecraft_version" to mcVersion
    )

    templateProperties.forEach(inputs::property)
    if (usesLegacyForgeMetadata) {
        filesMatching("mcmod.info") { expand(templateProperties) }
    } else {
        filesMatching("META-INF/mods.toml") { expand(templateProperties) }
    }
    if (mcVersion in setOf("1.14.4", "1.15", "1.15.1", "1.15.2")) {
        val refmap = layout.buildDirectory.file("tmp/compileJava/minelights.refmap.json")
        dependsOn(tasks.named("compileJava"))
        from(refmap)
    }
    dependsOn(syncSharedSources)
    dependsOn("stonecutterPrepare")
    dependsOn("stonecutterGenerate")
    doLast {
        rootProject.file("src/main/resources/assets/minelights/icon.png")
            .copyTo(destinationDir.resolve(modIcon), overwrite = true)
        if (mcVersion == "1.14.4") {
            val mixinConfig = destinationDir.resolve("minelights.mixins.json")
            mixinConfig.writeText(mixinConfig.readText().replaceFirst("{", "{\n  \"refmap\": \"minelights.refmap.json\","))
        }
        if (usesClothConfig) {
            val modsToml = destinationDir.resolve("META-INF/mods.toml")
            modsToml.appendText("""

                [[dependencies.$modId]]
                modId = "$clothConfigModId"
                mandatory = true
                versionRange = "[1,)"
                ordering = "AFTER"
                side = "CLIENT"
            """.trimIndent() + "\n")
        }
        if (usesLegacyForgeMetadata) {
            destinationDir.resolve("minelights.mixins.json").delete()
            destinationDir.resolve("META-INF/mods.toml").delete()
        } else if (mcVersion == "1.13.2" || mcVersion == "1.14.2" || mcVersion == "1.14.3") {
            destinationDir.resolve("minelights.mixins.json").delete()
            val modsToml = destinationDir.resolve("META-INF/mods.toml")
            modsToml.writeText(modsToml.readText().replace(
                Regex("""(?m)^\[\[mixins]]\r?\nconfig = "minelights\.mixins\.json"\r?\n?"""),
                ""
            ))
        }
    }
}

tasks.named<Jar>("jar") {
    dependsOn(syncSharedSources)
    dependsOn("stonecutterPrepare")
    dependsOn("stonecutterGenerate")
    if (usesLegacyForgeMetadata) {
        from("src/main/resources") { include("mcmod.info") }
    } else {
        from("src/main/resources") { include("META-INF/mods.toml") }
    }
}

val productionJar = if (requiresSrgRuntimeMappings) {
    renamer.classes(tasks.named<Jar>("jar")) {
        map.from(minecraft.dependency.toSrgFile)
        archiveClassifier = "srg"
    }
    tasks.named<RenameJar>("renameJar").flatMap { it.output }.also {
        tasks.named("build") { dependsOn("renameJar") }
    }
} else {
    tasks.named<Jar>("jar").flatMap { it.archiveFile }
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(syncSharedSources)
    dependsOn("stonecutterPrepare")
    dependsOn("stonecutterGenerate")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(layout.buildDirectory.dir("generated/stonecutter/main/java"))
    if (usesLegacyForgeMetadata) {
        from("src/main/resources") { include("mcmod.info") }
    } else {
        from("src/main/resources") { include("META-INF/mods.toml") }
    }
}

if (stonecutter.current.isActive) {
    rootProject.tasks.register("buildActive") {
        group = "project"
        dependsOn(tasks.named("build"))
    }
}

val modrinthToken = providers.gradleProperty("modrinthToken")
    .orElse(providers.environmentVariable("MODRINTH_TOKEN"))
    .getOrElse("")
val modrinthDryRun = providers.gradleProperty("publish.dryRun")
    .map(String::toBoolean)
    .getOrElse(true)

publishMods {
    file = productionJar
    additionalFiles.from(tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile })
    displayName = "$modName $modVersion for $mcVersion (Forge)"
    version = "$modVersion+$mcVersion-forge"
    changelog = rootProject.file("../CHANGELOG.md").takeIf { it.exists() }?.readText() ?: "No changelog provided."
    type = STABLE
    modLoaders.add("forge")
    dryRun = modrinthDryRun

    modrinth {
        projectId = property("publish.modrinth").toString()
        accessToken = modrinthToken
        minecraftVersions.addAll(property("mod.mc_targets").toString().split(" "))
        if (mcVersion == "1.14.4") {
            requires("mixinbootstrap")
        }
        if (usesClothConfig) {
            requires(if (stonecutter.eval(mcVersion, ">=1.21.5")) "cloth-config-forge" else "cloth-config")
        }
    }
}
