plugins {
    base
}

val fabricDir = layout.projectDirectory.dir("fabric")
val neoforgeDir = layout.projectDirectory.dir("neoforge")
val defaultNeoForgeVersions = listOf(
    "1.20.2",
    "1.20.5",
    "1.21.2",
    "1.21.6",
    "1.21.8",
    "1.21.9",
    "26.1",
)

fun mergedProjectProperties(vararg overrides: Pair<String, String>): Map<String, String> =
    gradle.startParameter.projectProperties + overrides

val cleanFabric = tasks.register<GradleBuild>("cleanFabric") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Cleans the dedicated Fabric workspace."
    dir = fabricDir.asFile
    tasks = listOf("clean")
    startParameter.projectProperties = mergedProjectProperties()
}

val cleanNeoForge = tasks.register<GradleBuild>("cleanNeoForge") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Cleans the dedicated NeoForge workspace."
    dir = neoforgeDir.asFile
    tasks = listOf("clean")
    startParameter.projectProperties = mergedProjectProperties(
        "neoforge.versions" to defaultNeoForgeVersions.joinToString(",")
    )
}

tasks.register<GradleBuild>("buildFabric") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Builds all Fabric targets from the dedicated Fabric workspace."
    dir = fabricDir.asFile
    tasks = listOf("clean", "build")
    startParameter.projectProperties = mergedProjectProperties()
}

tasks.register<GradleBuild>("buildNeoForge") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Builds the supported NeoForge targets from the dedicated NeoForge workspace."
    dir = neoforgeDir.asFile
    tasks = listOf("clean", "build")
    startParameter.projectProperties = mergedProjectProperties(
        "neoforge.versions" to defaultNeoForgeVersions.joinToString(",")
    )
}

val publishFabric = tasks.register<GradleBuild>("publishFabric") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Publishes all Fabric targets from the dedicated Fabric workspace."
    dir = fabricDir.asFile
    tasks = listOf("publishMods")
    startParameter.projectProperties = mergedProjectProperties()
}

val publishNeoForge = tasks.register<GradleBuild>("publishNeoForge") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Publishes the supported NeoForge targets from the dedicated NeoForge workspace."
    dir = neoforgeDir.asFile
    tasks = listOf("publishMods")
    startParameter.projectProperties = mergedProjectProperties(
        "neoforge.versions" to defaultNeoForgeVersions.joinToString(",")
    )
}

tasks.named("build") {
    dependsOn("buildFabric", "buildNeoForge")
}

tasks.named("clean") {
    dependsOn(cleanFabric, cleanNeoForge)
}

tasks.register("publish") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Publishes all configured Fabric and NeoForge targets."
    dependsOn(publishFabric, publishNeoForge)
}

tasks.register("verifyWorkspaces") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that the Fabric and NeoForge workspaces are present."
    doLast {
        val requiredDirs = listOf(fabricDir.asFile, neoforgeDir.asFile, layout.projectDirectory.dir("common").asFile)
        val missing = requiredDirs.filterNot { it.exists() }
        check(missing.isEmpty()) {
            "Missing required workspace directories: ${missing.joinToString { it.absolutePath }}"
        }
    }
}
