import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.HttpURLConnection
import java.net.URI

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
        val url = URI.create("https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js").toURL()
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
    val outputDir = layout.projectDirectory.dir("src/main/resources/textmate")

    // We register the whole directory as the output since the contents are dynamic
    outputs.dir(outputDir)

    doLast {
        outputDir.asFile.mkdirs()
        outputDir.dir("syntaxes").asFile.mkdirs()

        // Use the GitHub API to dynamically list the contents of the directory
        val apiUrl = URI.create("https://api.github.com/repos/nextflow-io/vscode-language-nextflow/contents/syntaxes").toURL()

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
        val jsonSlurper = JsonSlurper()

        @Suppress("UNCHECKED_CAST")
        val files = jsonSlurper.parseText(responseStr) as List<Map<String, Any>>

        files.forEach { fileNode ->
            val type = fileNode["type"] as? String
            val name = fileNode["name"] as? String
            val downloadUrlStr = fileNode["download_url"] as? String

            // Target the raw file URLs for all .json syntax files
            if (type == "file" && name != null && downloadUrlStr != null && name.endsWith(".json")) {
                println("Downloading syntax bundle: $name")
                val downloadConnection = (URI.create(downloadUrlStr).toURL().openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "Nextflow-IntelliJ-Plugin-Build")
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val fileBytes = downloadConnection.inputStream.use { it.readBytes() }
                outputDir.dir("syntaxes").file(name).asFile.writeBytes(fileBytes)
            }
        }

        // Download language-configuration.json to the bundle root so the 'configuration' field
        // in each language entry resolves correctly.
        val langConfigBytes = (URI.create("https://raw.githubusercontent.com/nextflow-io/vscode-language-nextflow/refs/heads/main/language-configuration.json").toURL().openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "Nextflow-IntelliJ-Plugin-Build")
            connectTimeout = 15000
            readTimeout = 15000
        }.inputStream.use { it.readBytes() }
        outputDir.file("language-configuration.json").asFile.writeBytes(langConfigBytes)

        // Dynamically generate a minimal package.json understood by IntelliJ's TextMate plugin.
        // We keep 'grammars' and 'languages', stripping only VSCode-only fields that reference
        // files not present in the bundle (icon images).
        val pkgConnection = (URI.create("https://raw.githubusercontent.com/nextflow-io/vscode-language-nextflow/refs/heads/main/package.json").toURL().openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "Nextflow-IntelliJ-Plugin-Build")
            connectTimeout = 15000
            readTimeout = 15000
        }
        val pkgResponseStr = pkgConnection.inputStream.bufferedReader().use { it.readText() }

        @Suppress("UNCHECKED_CAST")
        val canonicalPackageJson = jsonSlurper.parseText(pkgResponseStr) as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val contributes = canonicalPackageJson["contributes"] as Map<String, Any>

        // Keep grammars as-is — paths like ./syntaxes/foo.json match what we downloaded.
        val grammars = contributes["grammars"]

        // Strip only the 'icon' field from language entries (references images we don't ship).
        // 'configuration' is kept — it now resolves to the language-configuration.json we downloaded.
        @Suppress("UNCHECKED_CAST")
        val languages = (contributes["languages"] as? List<Map<String, Any>>)?.map { lang ->
            lang.filterKeys { it != "icon" }
        }

        outputDir.file("package.json").asFile.writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(mapOf(
                "name" to "nextflow-vscode-bundle",
                "version" to canonicalPackageJson["version"],
                "contributes" to mapOf(
                    "languages" to languages,
                    "grammars" to grammars,
                )
            )))
        )

        // Generate manifest.txt so the runtime provider can enumerate resources
        // without opening the plugin JAR (which blocks on Windows).
        val manifestContent = buildString {
            outputDir.asFile.walkTopDown()
                .filter { it.isFile && it.name != "manifest.txt" }
                .sortedBy { it.path }
                .forEach { file ->
                    appendLine(file.relativeTo(outputDir.asFile).path.replace('\\', '/'))
                }
        }
        outputDir.file("manifest.txt").asFile.writeText(manifestContent)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(downloadMermaid)
    dependsOn(downloadTextmateBundles)
}
