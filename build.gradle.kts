plugins {
  id("java-library")
}

repositories {
  mavenCentral()
  maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/");
  maven("https://maven.enginehub.org/repo/")
}

dependencies {
  compileOnly("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT")
  // last compatible Java 17 WorldGuard API
  compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.16")
}


java {
  toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
  processResources {
    val props = mapOf(
      "version" to version,
      "description" to project.property("description")
    )
    filesMatching("plugin.yml") {
      expand(props)
    }
  }
}
