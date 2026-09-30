import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import net.minecraftforge.renamer.gradle.RenameJar
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
val requiresSrgRuntimeMappings = stonecutter.eval(mcVersion, "<=1.20.4")
val mappingChannel = if (mcVersion == "1.14.3") "snapshot" else "official"
val mappingVersion = if (mcVersion == "1.14.3") "20190719-1.14.3" else mcVersion
val mcDependency = property("mod.mc_dep").toString()
val clothConfigVersion = property("deps.cloth_config").toString()
val clothConfigProject = if (stonecutter.eval(mcVersion, ">=1.21.5")) "cloth-config-forge" else "cloth-config"
val clothConfigDependency = if (mcVersion == "1.16.2") {
    "maven.modrinth:9s6osm5g:tR748cRj"
} else if (mcVersion == "1.18" || mcVersion == "1.18.1" || mcVersion == "1.18.2") {
    "maven.modrinth:9s6osm5g:ZbWG3eJW"
} else if (mcVersion == "1.16.4" || mcVersion == "1.16.5") {
    "maven.modrinth:9s6osm5g:31tLmbMI"
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
    compileOnly(clothConfigDependency)
    if (clothConfigRuntime) {
        runtimeOnly(clothConfigDependency)
    }
    if (mcVersion == "1.14.4") {
        compileOnly("org.spongepowered:mixin:0.8.2")
        annotationProcessor("org.spongepowered:mixin:0.8.2:processor")
        runtimeOnly("maven.modrinth:mixinbootstrap:hOGSWOX8")
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
    if (stonecutter.eval(mcVersion, "<=1.16.2")) {
        java.exclude("megabytesme/minelights/config/LiveStatusEntry.java")
        java.exclude("megabytesme/minelights/config/ModMenuIntegration.java")
    }
    if (mcVersion == "1.14.3") {
        java.exclude("megabytesme/minelights/mixin/**")
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
    if (mcVersion == "1.14.4") {
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
        "loader_version_range" to forgeLoaderRange
    )

    templateProperties.forEach(inputs::property)
    filesMatching("META-INF/mods.toml") { expand(templateProperties) }
    if (mcVersion == "1.14.4") {
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
        if (mcVersion == "1.14.3") {
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
    from("src/main/resources") { include("META-INF/mods.toml") }
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
    from("src/main/resources") { include("META-INF/mods.toml") }
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
    }
}
