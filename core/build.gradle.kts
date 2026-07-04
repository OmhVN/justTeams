dependencies {
    implementation(project(":api"))

    compileOnly("dev.folia:folia-api:26.1.2.build.8-stable")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("me.NoChance.PvPManager:pvpmanager:3.18.44")
    compileOnly("com.github.UlrichBR:UKoth-API:2.10.0-R2")
    compileOnly("com.github.retrooper:packetevents-spigot:2.12.1")

    // Library loader
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("com.mysql:mysql-connector-j:9.2.0")
    compileOnly("com.h2database:h2:2.2.224")
    compileOnly("redis.clients:jedis:5.1.0")
    compileOnly("org.slf4j:slf4j-nop:2.0.18")

    // Shade dependencies
    implementation("org.bstats:bstats-bukkit:3.2.1")
    implementation("dev.faststats.metrics:bukkit:0.22.0")
    implementation("com.github.SkytAsul:GlowingEntities:1.4.1")
}
