plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "eu.kotori"
version = "2.5.9"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://repo.eternalcode.pl/releases")
    maven("https://repo.faststats.dev/releases")
}

dependencies {
    // ── PROVIDED (server / other plugins cung cấp) ──
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("me.NoChance.PvPManager:pvpmanager:3.18.44")
    compileOnly("com.github.UlrichBR:UKoth-API:2.10.0-R2")
    compileOnly("com.github.retrooper:packetevents-spigot:2.12.1")

    // ── LIBRARY LOADER (server tải tự động qua plugin.yml `libraries`) ──
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("com.mysql:mysql-connector-j:8.4.0")
    compileOnly("com.h2database:h2:2.2.224")
    compileOnly("redis.clients:jedis:5.1.0")
    compileOnly("org.slf4j:slf4j-nop:2.0.13")

    // ── SHADE (đóng gói vào JAR + relocate) ──
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("dev.faststats.metrics:bukkit:0.22.0")
    implementation("com.github.SkytAsul:GlowingEntities:1.4.1")
}

tasks.shadowJar {
    archiveClassifier.set("")

    // Relocate chỉ 3 thư viện shade
    relocate("org.bstats", "eu.kotori.justTeams.libs.bstats")
    relocate("dev.faststats", "eu.kotori.justTeams.libs.faststats")
    relocate("fr.skytasul.glowingentities", "eu.kotori.justTeams.libs.glowingentities")

    // Loại bỏ META-INF không cần thiết
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
