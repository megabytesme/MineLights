plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.17.21" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.17.21" apply false
    id("me.modmuss50.mod-publish-plugin") version "0.8.+" apply false
}

stonecutter active "1.21.9"

stonecutter parameters {
    swaps["mod_version"] = "\"" + property("mod.version") + "\";"
    swaps["minecraft"] = "\"" + node.metadata.version + "\";"
    constants["release"] = property("mod.id") != "template"
    constants["loader_fabric"] = false
    constants["loader_forge"] = false
    constants["loader_neoforge"] = false
    constants["loader_quilt"] = true
}
