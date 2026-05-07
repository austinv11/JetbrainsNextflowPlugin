package com.austinv11.nextflow

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project
import java.util.concurrent.TimeUnit

@State(
    name = "NextflowSettings",
    storages = [Storage("nextflow-lsp.xml")]
)
@Service(Service.Level.PROJECT)
class NextflowSettings : PersistentStateComponent<NextflowSettings.State> {

    data class State(
        // Server
        var customLspJarPath: String = "",

        // Diagnostics
        var errorReportingMode: String = "warnings",

        // Language
        var languageVersion: String = DEFAULT_LANGUAGE_VERSION,

        // Completion
        var completionExtended: Boolean = true,
        var completionMaxItems: Int = 50,

        // Formatting
        var formattingHarshilAlignment: Boolean = false,
        var formattingSortDeclarations: Boolean = false,

        // Files
        var filesExclude: String = ".git,.nf-test,work",

        // Advanced
        var debugMode: Boolean = false,
    )

    private var myState = State().apply {
        if (languageVersion == DEFAULT_LANGUAGE_VERSION) {
            detectInstalledVersion()?.let { languageVersion = it }
        }
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun detectInstalledVersion(): String? {
        return runCatching {
            val process = ProcessBuilder("nextflow", "-v")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching null
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            val matcher = Regex("""(\d+)\.(\d+)""").find(output)
            matcher?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }
        }.getOrNull()
    }

    companion object {
        private const val DEFAULT_LANGUAGE_VERSION = "26.04"

        fun getInstance(project: Project): NextflowSettings =
            project.getService(NextflowSettings::class.java)
    }
}
