pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "dev.kikugie.stonecutter" -> useModule("dev.kikugie:stonecutter:${requested.version}")
                "dev.kikugie.loom-back-compat" -> useModule("dev.kikugie:loom-back-compat:${requested.version}")
            }
        }
    }
    repositories {
        mavenLocal()
        mavenCentral()
        // NOTE: gradlePluginPortal() 在部分网络下 TLS 握手失败，插件从下面这些仓库解析
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
    // 26.1+ 与旧版本的 loom 兼容层
    id("dev.kikugie.loom-back-compat") version "0.3"
}

stonecutter {
    create(rootProject) {
        fun match(project: String, vararg loaders: String, version: String = project) {
            for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        // 目前只出 26.2-fabric；以后加版本在这里加一行即可（源码保持共享）。
        match("26.2", "fabric", version = "26.2")
        vcsVersion = "26.2-fabric"
    }
}

rootProject.name = "rail-switch"
