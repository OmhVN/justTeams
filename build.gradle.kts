plugins {
    java
}

subprojects {
    apply(plugin = "java")

    group = "eu.kotori"
    version = "2.5.9"

    configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    configurations.matching { it.isCanBeResolved }.configureEach {
        attributes {
            attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
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

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
