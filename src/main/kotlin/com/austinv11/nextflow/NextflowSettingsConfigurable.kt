package com.austinv11.nextflow

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel

class NextflowSettingsConfigurable(private val project: Project) : BoundConfigurable("Nextflow") {

    private val settings = NextflowSettings.getInstance(project)

    override fun createPanel(): DialogPanel {
        return panel {
            group("LSP Configuration") {
                row("Error Reporting Mode:") {
                    comboBox(listOf("off", "errors", "warnings", "paranoid"))
                        .bindItem(
                            getter = { settings.state.errorReportingMode },
                            setter = { settings.state.errorReportingMode = it ?: "warnings" }
                        )
                }.rowComment(
                    "Controls the level of diagnostics reported by the Nextflow language server:<br>" +
                            "<b>off</b> – no diagnostics<br>" +
                            "<b>errors</b> – errors only<br>" +
                            "<b>warnings</b> – errors and warnings (default)<br>" +
                            "<b>paranoid</b> – errors, warnings, and best-practice hints"
                )
            }
        }
    }

    override fun apply() {
        super.apply()
        LspServerManager.getInstance(project)
            .stopAndRestartIfNeeded(NextflowLspServerSupportProvider::class.java)
    }
}