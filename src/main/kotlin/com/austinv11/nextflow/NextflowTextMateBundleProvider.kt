package com.austinv11.nextflow

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

class NextflowTextMateBundleProvider : TextMateBundleProvider {

    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        val path = resolveBundlePath() ?: return emptyList()
        return listOf(TextMateBundleProvider.PluginBundle("Nextflow", path))
    }

    private fun resolveBundlePath(): Path? {
        return try {
            extractViaClassLoader()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractViaClassLoader(): Path? {
        val loader = javaClass.classLoader
        val manifestText = loader.getResourceAsStream("textmate/manifest.txt")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: return null

        val resources = manifestText.lines().filter { it.isNotBlank() }.associateWith { relativePath ->
            loader.getResourceAsStream("textmate/$relativePath")?.use { it.readBytes() } ?: return null
        }.toMutableMap()
        inlineInjections(resources)

        val destDir = Paths.get(PathManager.getSystemPath(), "nextflow-textmate-bundle")
        val stampFile = destDir.resolve(".manifest-stamp")
        val digest = MessageDigest.getInstance("SHA-256")
        resources.toSortedMap().forEach { (name, bytes) ->
            digest.update(name.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(bytes)
        }
        val stamp = digest.digest().joinToString("") { "%02x".format(it) }

        // Include content in the stamp: upstream grammars can change without new filenames.
        val existingStamp = runCatching { stampFile.toFile().readText() }.getOrNull()
        if (existingStamp == stamp && resources.keys.all { Files.exists(destDir.resolve(it)) }) {
            return destDir
        }

        Files.createDirectories(destDir)
        for ((relativePath, bytes) in resources) {
            val target = destDir.resolve(relativePath)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }

        stampFile.toFile().writeText(stamp)
        return destDir
    }

    private fun inlineInjections(resources: MutableMap<String, ByteArray>) {
        // IntelliJ reads grammar-local injections, but not VS Code's package.json injectTo.
        val pkg = JsonParser.parseString(resources.getValue("package.json").toString(Charsets.UTF_8)).asJsonObject
        val grammars = pkg.getAsJsonObject("contributes").getAsJsonArray("grammars").map { it.asJsonObject }
        for (target in grammars.filter { !it.has("injectTo") }) {
            val scope = target.get("scopeName").asString
            val applicable = grammars.filter { entry ->
                entry.getAsJsonArray("injectTo")?.any { it.asString == scope } == true
            }
            if (applicable.isEmpty()) continue
            val path = target.get("path").asString.removePrefix("./")
            val grammar = JsonParser.parseString(resources.getValue(path).toString(Charsets.UTF_8)).asJsonObject
            val injections = grammar.getAsJsonObject("injections") ?: JsonObject().also { grammar.add("injections", it) }
            val repository = grammar.getAsJsonObject("repository") ?: JsonObject().also { grammar.add("repository", it) }
            for (entry in applicable) {
                val source = JsonParser.parseString(resources.getValue(entry.get("path").asString.removePrefix("./"))
                    .toString(Charsets.UTF_8)).asJsonObject
                if (source.get("scopeName").asString == "nextflow.script.injection") adaptScriptEmbedding(source)
                val prefix = source.get("scopeName").asString + "."
                prefixIncludes(source, prefix)
                source.getAsJsonObject("repository")?.entrySet()?.forEach { (key, value) -> repository.add(prefix + key, value) }
                injections.add(source.get("injectionSelector").asString, JsonObject().apply {
                    add("patterns", source.getAsJsonArray("patterns"))
                })
            }
            resources[path] = grammar.toString().toByteArray(Charsets.UTF_8)
        }
    }

    private fun adaptScriptEmbedding(grammar: JsonObject) {
        // IntelliJ loses nested scopes when these begin/while regions span shell statements.
        // A non-consuming end leaves the triple quotes for the enclosing string rule.
        val repository = grammar.getAsJsonObject("repository")
        for (key in listOf("embedded-shell", "embedded-shell-single", "embedded-python", "embedded-r")) {
            val rule = repository.getAsJsonObject(key) ?: continue
            if (!rule.has("while")) continue
            rule.remove("while")
            rule.addProperty("end", if (key.endsWith("-single")) "(?=[\\t ]*''')" else "(?=[\\t ]*\"\"\")")
            val languageScope = rule.getAsJsonArray("patterns").first().asJsonObject.get("include").asString
            rule.addProperty("contentName", rule.get("contentName").asString + " " + languageScope)
        }
    }

    private fun prefixIncludes(node: JsonElement, prefix: String) {
        when {
            node.isJsonArray -> node.asJsonArray.forEach { prefixIncludes(it, prefix) }
            node.isJsonObject -> node.asJsonObject.let { obj ->
                val include = obj.get("include")?.asString
                if (include?.startsWith("#") == true) obj.addProperty("include", "#" + prefix + include.drop(1))
                obj.entrySet().forEach { prefixIncludes(it.value, prefix) }
            }
        }
    }
}
