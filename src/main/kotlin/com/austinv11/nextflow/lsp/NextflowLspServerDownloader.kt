package com.austinv11.nextflow.lsp

import com.google.gson.JsonParser
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import java.io.File

object NextflowLspServerDownloader {
    private val LOG = Logger.getInstance(NextflowLspServerDownloader::class.java)
    private val PLUGIN_DIR = File(PathManager.getPluginsPath(), "jetbrains-nextflow-lsp")
    private val JAR_FILE = File(PLUGIN_DIR, "language-server-all.jar")
    private val VERSION_FILE = File(PLUGIN_DIR, "version.txt")
    private const val GITHUB_API_LATEST = "https://api.github.com/repos/austinv11/language-server/releases/latest" // "https://api.github.com/repos/nextflow-io/language-server/releases/latest"  FIXME: Using a patched version, switch back to true repo once https://github.com/nextflow-io/language-server/pull/154 is merged and released

    fun getOrDownloadLspServer(): File? {
        if (!PLUGIN_DIR.exists()) PLUGIN_DIR.mkdirs()

        try {
            val response = HttpRequests.request(GITHUB_API_LATEST)
                .accept("application/vnd.github.v3+json")
                .readString(null)

            val json = JsonParser.parseString(response).asJsonObject
            val latestVersion = json.get("tag_name").asString

            if (JAR_FILE.exists() && VERSION_FILE.exists() && VERSION_FILE.readText() == latestVersion) {
                LOG.info("Nextflow LSP jar is up-to-date ($latestVersion) at ${JAR_FILE.absolutePath}")
                return JAR_FILE
            }

            val downloadUrl = json.getAsJsonArray("assets")
                .map { it.asJsonObject }
                .firstOrNull { it.get("name").asString == "language-server-all.jar" }
                ?.get("browser_download_url")?.asString

            if (downloadUrl != null) {
                LOG.info("Downloading Nextflow LSP jar (version $latestVersion) from $downloadUrl")
                HttpRequests.request(downloadUrl).saveToFile(JAR_FILE, null)
                VERSION_FILE.writeText(latestVersion)
                LOG.info("Nextflow LSP jar downloaded to ${JAR_FILE.absolutePath}")
            } else {
                LOG.warn("language-server-all.jar not found in the latest GitHub release assets")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to check or download Nextflow LSP release (jar path: ${JAR_FILE.absolutePath})", e)
        }

        return if (JAR_FILE.exists()) JAR_FILE else {
            LOG.error("Nextflow LSP jar not found at ${JAR_FILE.absolutePath} and download failed")
            null
        }
    }
}