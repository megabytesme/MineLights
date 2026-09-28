import org.gradle.internal.os.OperatingSystem

plugins {
    base
}

val fabricDir = layout.projectDirectory.dir("fabric")
val neoforgeDir = layout.projectDirectory.dir("neoforge")
val quiltDir = layout.projectDirectory.dir("quilt")
val defaultNeoForgeVersions = listOf(
    "1.20.2",
    "1.20.5",
    "1.21.2",
    "1.21.6",
    "1.21.8",
    "1.21.9",
    "26.1",
    "26.2",
    "26.3",
)

fun mergedProjectProperties(vararg overrides: Pair<String, String>): Map<String, String> =
    gradle.startParameter.projectProperties + overrides

fun projectPropertyArgs(vararg overrides: Pair<String, String>): List<String> =
    mergedProjectProperties(*overrides).entries.map { (key, value) -> "-P$key=$value" }

val gradleWrapper = if (OperatingSystem.current().isWindows) "gradlew.bat" else "./gradlew"

fun registerWorkspaceTask(
    name: String,
    descriptionText: String,
    workspaceDir: String,
    gradleTaskName: String,
    vararg overrides: Pair<String, String>,
) = tasks.register<Exec>(name) {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = descriptionText
    workingDir = layout.projectDirectory.asFile
    commandLine(
        gradleWrapper,
        "-p",
        workspaceDir,
        *projectPropertyArgs(*overrides).toTypedArray(),
        gradleTaskName
    )
}

val cleanFabric = registerWorkspaceTask(
    name = "cleanFabric",
    descriptionText = "Cleans the dedicated Fabric workspace.",
    workspaceDir = "fabric",
    gradleTaskName = "clean"
)

val cleanNeoForge = registerWorkspaceTask(
    name = "cleanNeoForge",
    descriptionText = "Cleans the dedicated NeoForge workspace.",
    workspaceDir = "neoforge",
    gradleTaskName = "clean",
    "neoforge.versions" to defaultNeoForgeVersions.joinToString(",")
)

val cleanQuilt = registerWorkspaceTask(
    name = "cleanQuilt",
    descriptionText = "Cleans the dedicated Quilt workspace.",
    workspaceDir = "quilt",
    gradleTaskName = "clean"
)

val buildFabric = registerWorkspaceTask(
    name = "buildFabric",
    descriptionText = "Builds all Fabric targets from the dedicated Fabric workspace.",
    workspaceDir = "fabric",
    gradleTaskName = "build"
)

val buildNeoForge = registerWorkspaceTask(
    name = "buildNeoForge",
    descriptionText = "Builds the supported NeoForge targets from the dedicated NeoForge workspace.",
    workspaceDir = "neoforge",
    gradleTaskName = "build",
    "neoforge.versions" to defaultNeoForgeVersions.joinToString(",")
)

val buildQuilt = registerWorkspaceTask(
    name = "buildQuilt",
    descriptionText = "Builds all supported Quilt targets from the dedicated Quilt workspace.",
    workspaceDir = "quilt",
    gradleTaskName = "build"
)

val publishFabric = registerWorkspaceTask(
    name = "publishFabric",
    descriptionText = "Publishes all Fabric targets from the dedicated Fabric workspace.",
    workspaceDir = "fabric",
    gradleTaskName = "publishMods"
)

val publishNeoForge = registerWorkspaceTask(
    name = "publishNeoForge",
    descriptionText = "Publishes the supported NeoForge targets from the dedicated NeoForge workspace.",
    workspaceDir = "neoforge",
    gradleTaskName = "publishMods",
    "neoforge.versions" to defaultNeoForgeVersions.joinToString(",")
)

val publishQuilt = registerWorkspaceTask(
    name = "publishQuilt",
    descriptionText = "Publishes the supported Quilt targets from the dedicated Quilt workspace.",
    workspaceDir = "quilt",
    gradleTaskName = "publishMods"
)

tasks.named("build") {
    dependsOn(buildFabric, buildNeoForge, buildQuilt)
}

tasks.named("clean") {
    dependsOn(cleanFabric, cleanNeoForge, cleanQuilt)
}

tasks.register("publish") {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Publishes all configured Fabric, NeoForge, and Quilt targets."
    dependsOn(publishFabric, publishNeoForge, publishQuilt)
}

tasks.register("verifyWorkspaces") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that the Fabric, NeoForge, and Quilt workspaces are present."
    doLast {
        val requiredDirs = listOf(fabricDir.asFile, neoforgeDir.asFile, quiltDir.asFile, layout.projectDirectory.dir("common").asFile)
        val missing = requiredDirs.filterNot { it.exists() }
        check(missing.isEmpty()) {
            "Missing required workspace directories: ${missing.joinToString { it.absolutePath }}"
        }
    }
}
