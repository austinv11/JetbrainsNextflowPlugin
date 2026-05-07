package com.austinv11.nextflow

import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class NextflowTextMateBundleProvider : TextMateBundleProvider {

    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        val path = resolveBundlePath() ?: return emptyList()
        return listOf(TextMateBundleProvider.PluginBundle("Nextflow", path))
    }

    private fun resolveBundlePath(): Path? {
        val url = javaClass.classLoader.getResource("textmate/package.json") ?: return null
        return try {
            val uri = url.toURI()
            when (uri.scheme) {
                // Development (runIde): resources are plain files on the classpath — use directly.
                "file" -> Paths.get(uri).parent
                // Production: extract via classloader streams to avoid opening the JAR directly,
                // which blocks on Windows due to mandatory file locking.
                "jar"  -> extractViaClassLoader()
                else   -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractViaClassLoader(): Path? {
        val loader = javaClass.classLoader
        val manifestText = loader.getResourceAsStream("textmate/manifest.txt")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: return null

        val destDir = Paths.get(PathManager.getSystemPath(), "nextflow-textmate-bundle")
        val stampFile = destDir.resolve(".manifest-stamp")

        // Skip re-extraction if the bundle was already extracted with this exact manifest.
        val existingStamp = runCatching { stampFile.toFile().readText() }.getOrNull()
        if (existingStamp == manifestText && destDir.resolve("package.json").toFile().exists()) {
            return destDir
        }

        Files.createDirectories(destDir)
        val paths = manifestText.lines().filter { it.isNotBlank() }
        for (relativePath in paths) {
            val target = destDir.resolve(relativePath)
            Files.createDirectories(target.parent)
            loader.getResourceAsStream("textmate/$relativePath")?.use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        stampFile.toFile().writeText(manifestText)
        return destDir
    }
}
