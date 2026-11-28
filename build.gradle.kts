plugins {
    kotlin("jvm") version "2.4.21"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.ququla"
version = "1.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    jar {
        enabled = false
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }

    register("deployTest") {
        group = "deployment"
        description = "Build shadowJar and deploy to the test server via MC_DEPLOY_SCRIPT."
        dependsOn(shadowJar)
        val jarProvider = shadowJar.flatMap { it.archiveFile }
        val scriptProvider =
            providers
                .gradleProperty("deployScript")
                .orElse(providers.environmentVariable("MC_DEPLOY_SCRIPT"))
        doLast {
            val scriptPath =
                scriptProvider.orNull
                    ?: error("Set MC_DEPLOY_SCRIPT environment variable or pass -PdeployScript=<path>.")
            val jarFile = jarProvider.get().asFile
            val shell = if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "pwsh"
            val exitCode =
                ProcessBuilder(
                    shell,
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptPath,
                    "-JarPath",
                    jarFile.absolutePath,
                    "-Target",
                    "plugin",
                ).inheritIO()
                    .start()
                    .waitFor()
            if (exitCode != 0) {
                error("Deploy script failed with exit code $exitCode")
            }
        }
    }
}
