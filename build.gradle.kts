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
        bundledPlugin("org.jetbrains.plugins.textmate")
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

val downloadTextmateBundles by tasks.registering {
    val outputDir = layout.projectDirectory.dir("src/main/resources/syntaxes")

    // We register the whole directory as the output since the contents are dynamic
    outputs.dir(outputDir)

    doLast {
        outputDir.asFile.mkdirs()

        // Use the GitHub API to dynamically list the contents of the directory
        val apiUrl = URL("https://api.github.com/repos/nextflow-io/vscode-language-nextflow/contents/syntaxes")

        val connection = (apiUrl.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            // GitHub API requires a User-Agent header
            setRequestProperty("User-Agent", "Nextflow-IntelliJ-Plugin-Build")
            connectTimeout = 15000
            readTimeout = 15000
        }

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            error("Failed to fetch syntaxes directory list from GitHub. HTTP code: ${connection.responseCode}")
        }

        val responseStr = connection.inputStream.bufferedReader().use { it.readText() }

        // Gradle bundles Groovy, so we can securely use JsonSlurper in our Kotlin build script
        val jsonSlurper = groovy.json.JsonSlurper()

        @Suppress("UNCHECKED_CAST")
        val files = jsonSlurper.parseText(responseStr) as List<Map<String, Any>>

        files.forEach { fileNode ->
            val type = fileNode["type"] as? String
            val name = fileNode["name"] as? String
            val downloadUrlStr = fileNode["download_url"] as? String

            // Target the raw file URLs for all .json syntax files
            if (type == "file" && name != null && downloadUrlStr != null && name.endsWith(".json")) {
                println("Downloading syntax bundle: $name")
                val downloadConnection = (URL(downloadUrlStr).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "Nextflow-IntelliJ-Plugin-Build")
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val fileBytes = downloadConnection.inputStream.use { it.readBytes() }
                outputDir.file(name).asFile.writeBytes(fileBytes)
            }
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(downloadMermaid)
    dependsOn(downloadTextmateBundles)
}
