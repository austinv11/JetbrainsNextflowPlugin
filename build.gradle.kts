import java.net.HttpURLConnection
import java.net.URL

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "com.austinv11.nextflow"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1.1")
        bundledPlugin("org.intellij.groovy")
    }

    compileOnly("com.jetbrains.intellij.platform:lsp:241.14494.240")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.austinv11.nextflow"
        name = "Nextflow LSP Support"
        vendor {
            name = "austinv11"
        }
        ideaVersion {
            sinceBuild = "241.0"
        }
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.test {
    useJUnitPlatform()
}

val downloadMermaid by tasks.registering {
    val outputDir = layout.projectDirectory.dir("src/main/resources/mermaid")
    val outputFile = outputDir.file("mermaid.min.js")
    outputs.file(outputFile)
    doLast {
        outputDir.asFile.mkdirs()
        val url = URL("https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "text/javascript,application/javascript")
            setRequestProperty("Accept-Encoding", "identity")
            connectTimeout = 15000
            readTimeout = 15000
        }

        val bytes = connection.inputStream.use { input -> input.readBytes() }
        val textStart = bytes.take(256).toByteArray().toString(Charsets.UTF_8).trimStart()
        if (textStart.startsWith("<")) {
            error("Downloaded Mermaid JS looks like HTML. Check CDN availability.")
        }
        outputFile.asFile.writeBytes(bytes)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(downloadMermaid)
}
