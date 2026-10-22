package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.NextflowSettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class NextflowLspUpdateChecker(private val project: Project) : Disposable {
    private val log = Logger.getInstance(NextflowLspUpdateChecker::class.java)
    private val checking = AtomicBoolean()
    private val installing = AtomicBoolean()
    private var scheduledCheck: ScheduledFuture<*>? = null
    internal var notification: Notification? = null
        private set
    private var notifiedVersion: String? = null

    @Synchronized
    fun start() {
        if (scheduledCheck != null || ApplicationManager.getApplication().isUnitTestMode) return
        scheduledCheck = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { checkForUpdates(false) }, 60, 3600, TimeUnit.SECONDS
        )
    }

    fun checkNow() {
        ApplicationManager.getApplication().executeOnPooledThread { checkForUpdates(true) }
    }

    internal fun checkForUpdates(
        manual: Boolean,
        cache: NextflowLspServerCache = NextflowLspServerDownloader.cache,
        now: Long = System.currentTimeMillis()
    ) {
        if (project.isDisposed || installing.get() || !checking.compareAndSet(false, true)) return
        try {
            val state = NextflowSettings.getInstance(project).state
            val snapshot = state.copy()
            if (snapshot.customLspJarPath.isNotBlank()) {
                if (manual) notifyInfo("Automatic updates are disabled while a Custom JAR is selected.")
                return
            }
            val series = NextflowLspVersion.parse(snapshot.languageVersion)?.series
                ?: throw IllegalArgumentException("Set a Nextflow release series, such as 26.04, in Nextflow settings first.")
            if (!manual && (!snapshot.checkLspUpdates ||
                    (snapshot.lastLspUpdateSeries == series && now - snapshot.lastLspUpdateCheck in 0 until TimeUnit.DAYS.toMillis(1)))) return

            val release = cache.findUpdate(snapshot.languageVersion)
            if (!settingsStillMatch(series)) return
            state.lastLspUpdateCheck = now
            state.lastLspUpdateSeries = series
            if (release == null) {
                if (manual) notifyInfo("The installed language server is up to date for Nextflow $series.")
                return
            }
            val version = release.version.toString()
            if (!manual && (state.dismissedLspUpdateVersion == version ||
                    (notifiedVersion == version && notification?.isExpired == false))) return
            notification?.expire()
            notifiedVersion = version
            notification = groupNotification(
                "Nextflow language server $version is available",
                "Install the update for Nextflow $series and restart this project's language server?",
                NotificationType.INFORMATION
            ).apply {
                addAction(NotificationAction.createSimpleExpiring("Install update") { install(release, series, cache) })
                addAction(NotificationAction.createSimpleExpiring("Dismiss") {
                    if (!project.isDisposed) NextflowSettings.getInstance(project).state.dismissedLspUpdateVersion = version
                })
                notify(project)
            }
        } catch (e: Exception) {
            if (e is com.intellij.openapi.progress.ProcessCanceledException || e is InterruptedException) return
            log.warn("Could not check for Nextflow language-server updates", e)
            if (manual && !project.isDisposed) {
                groupNotification("Language-server update check failed", e.message ?: "Please retry later.", NotificationType.WARNING).notify(project)
            }
        } finally {
            checking.set(false)
        }
    }

    private fun install(release: NextflowLspRelease, series: String, cache: NextflowLspServerCache) {
        if (!installing.compareAndSet(false, true)) return
        if (!settingsStillMatch(series)) {
            installing.set(false)
            notifyInfo("Nextflow settings changed. Check for updates again for the selected version.")
            return
        }
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Updating Nextflow language server", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    cache.install(release)
                    ApplicationManager.getApplication().invokeLater {
                        if (!settingsStillMatch(series)) return@invokeLater
                        NextflowSettings.getInstance(project).state.dismissedLspUpdateVersion = ""
                        LspServerManager.getInstance(project).stopAndRestartIfNeeded(NextflowLspServerSupportProvider::class.java)
                        notifyInfo("Installed language server ${release.version}. Restarting this project's language server.")
                    }
                } catch (e: Exception) {
                    if (e is com.intellij.openapi.progress.ProcessCanceledException || e is InterruptedException) return
                    log.warn("Could not install Nextflow language-server update", e)
                    if (!project.isDisposed) {
                        groupNotification("Language-server update failed", "${e.message ?: "Download failed"}. The existing server has been kept.", NotificationType.WARNING).notify(project)
                    }
                } finally {
                    installing.set(false)
                }
            }
        })
    }

    private fun settingsStillMatch(series: String): Boolean {
        if (project.isDisposed) return false
        val state = NextflowSettings.getInstance(project).state
        return state.customLspJarPath.isBlank() && NextflowLspVersion.parse(state.languageVersion)?.series == series
    }

    private fun notifyInfo(message: String) {
        if (!project.isDisposed) groupNotification("Nextflow language server", message, NotificationType.INFORMATION).notify(project)
    }

    private fun groupNotification(title: String, message: String, type: NotificationType): Notification =
        NotificationGroupManager.getInstance().getNotificationGroup("Nextflow Language Server")
            .createNotification(title, message, type)

    override fun dispose() {
        scheduledCheck?.cancel(true)
        notification?.expire()
    }
}
