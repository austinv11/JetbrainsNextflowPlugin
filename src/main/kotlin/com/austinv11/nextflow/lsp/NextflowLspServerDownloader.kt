package com.austinv11.nextflow.lsp

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.io.File

object NextflowLspServerDownloader {
    private val LOG = Logger.getInstance(NextflowLspServerDownloader::class.java)
    internal val cache by lazy {
        NextflowLspServerCache(
            File(PathManager.getSystemPath(), "nextflow-language-server"),
            fetchPage = ::fetchReleasePage,
            download = { url, destination ->
                HttpRequests.request(url).userAgent("JetbrainsNextflowPlugin").connectTimeout(10_000).readTimeout(60_000).saveToFile(destination, null)
            },
            logFailure = { LOG.warn("Failed to obtain matching Nextflow LSP release", it) },
            legacyDirectories = listOf(File(PathManager.getPluginsPath(), "JetbrainsNextflowPlugin"))
        )
    }

    internal fun fetchReleasePage(page: Int): NextflowLspReleasePage {
        val url = "https://api.github.com/repos/nextflow-io/language-server/releases?per_page=100&page=$page"
        return HttpRequests.request(url)
            .userAgent("JetbrainsNextflowPlugin")
            .accept("application/vnd.github+json")
            .tuner { it.setRequestProperty("Cache-Control", "no-cache") }
            .connectTimeout(10_000)
            .readTimeout(30_000)
            .connect { request ->
                NextflowLspReleasePage.parse(
                    request.readString(),
                    request.connection.getHeaderField("Link")?.contains("rel=\"next\"") == true
                ).also {
                    LOG.info("GitHub language-server release page $page: ${it.rawCount} releases, ${it.releases.size} usable JARs, hasNext=${it.hasNext}")
                }
            }
    }

    fun getOrDownloadLspServer(project: Project, languageVersion: String): File {
        val requestedSeries = NextflowLspVersion.parse(languageVersion)?.series
        val resolved = cache.resolve(languageVersion)
        if (NextflowLspVersion.parse(resolved.version.orEmpty())?.series != requestedSeries) {
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("Nextflow Language Server")
                .createNotification(
                    "Using a cached Nextflow language server",
                    "No matching server could be obtained for Nextflow $requestedSeries. " +
                        "Using cached server ${resolved.version ?: "(unknown version)"}. Diagnostics may differ from your Nextflow version.",
                    NotificationType.WARNING
                )
            notification.addAction(NotificationAction.createSimpleExpiring("Nextflow settings") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "com.austinv11.nextflow.settings")
            })
            notification.notify(project)
        }
        LOG.info("Using Nextflow LSP ${resolved.version ?: "unknown version"} at ${resolved.file.absolutePath}")
        return resolved.file
    }
}
