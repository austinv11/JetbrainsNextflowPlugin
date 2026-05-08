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
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    compileOnly("com.jetbrains.intellij.platform:lsp:241.14494.240")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.2")
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
    publishing {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
    signing {
        val signingKey: String? by project
        val signingPassword: String? by project
    }
}

val cleanGeneratedResources by tasks.registering(Delete::class) {
    // Only delete the generated file, not the whole directory (preview.html is static).
    delete(layout.projectDirectory.file("src/main/resources/mermaid/mermaid.min.js"))
    delete(layout.projectDirectory.dir("src/main/resources/textmate"))
}


tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
    dependsOn(cleanGeneratedResources)
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Didea.is.internal=true")
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
    mustRunAfter(cleanGeneratedResources)
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
    mustRunAfter(cleanGeneratedResources)
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

        // IntelliJ's TextMate engine doesn't resolve cross-bundle includes (e.g. `include:
        // "source.nextflow-groovy"`), so merge the Groovy grammar rules directly into the
        // nextflow and nextflow-config grammars and rewrite all external scope references
        // to internal #rule references.
        val groovyGrammarFile = outputDir.dir("syntaxes").file("groovy.tmLanguage.json").asFile
        if (groovyGrammarFile.exists()) {
            @Suppress("UNCHECKED_CAST")
            val groovyGrammar = jsonSlurper.parseText(groovyGrammarFile.readText()) as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val groovyRepo = groovyGrammar["repository"] as Map<String, Any>

            val includeReplacements = mapOf(
                "source.nextflow-groovy" to "#groovy",
                "source.nextflow-groovy#groovy" to "#groovy",
                "source.nextflow-groovy#groovy-code" to "#groovy-code",
                "source.nextflow-groovy#parameters" to "#parameters",
                "source.nextflow-groovy#types" to "#types",
            )

            for (name in listOf("nextflow.tmLanguage.json", "nextflow-config.tmLanguage.json")) {
                val grammarFile = outputDir.dir("syntaxes").file(name).asFile
                if (!grammarFile.exists()) continue

                @Suppress("UNCHECKED_CAST")
                val grammar = jsonSlurper.parseText(grammarFile.readText()) as MutableMap<String, Any>
                @Suppress("UNCHECKED_CAST")
                val repo = (grammar.getOrPut("repository") { mutableMapOf<String, Any>() }) as MutableMap<String, Any>
                groovyRepo.forEach { (k, v) -> repo[k] = v }

                var text = JsonOutput.toJson(grammar)
                includeReplacements.forEach { (from, to) ->
                    text = text.replace("\"$from\"", "\"$to\"")
                }
                grammarFile.writeText(text)
            }

            groovyGrammarFile.delete()
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

        // Drop the standalone groovy grammar — its rules are now merged into nextflow/nextflow-config.
        @Suppress("UNCHECKED_CAST")
        val grammars = (contributes["grammars"] as? List<Map<String, Any>>)
            ?.filter { it["scopeName"] != "source.nextflow-groovy" }

        // Strip only the 'icon' field from language entries (references images we don't ship).
        // 'configuration' is kept — it now resolves to the language-configuration.json we downloaded.
        // All extensions (.nf, .config) are kept so the TextMate highlighter fires for those files.
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
