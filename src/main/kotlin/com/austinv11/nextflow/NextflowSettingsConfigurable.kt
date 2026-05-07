package com.austinv11.nextflow

import com.austinv11.nextflow.lsp.NextflowLspServerSupportProvider
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class NextflowSettingsConfigurable(private val project: Project) : BoundConfigurable("Nextflow") {

    private val settings = NextflowSettings.getInstance(project)

    override fun createPanel(): DialogPanel {
        val state = settings.state
        return panel {
            group("LSP Server") {
                row("Custom JAR:") {
                    textFieldWithBrowseButton(
                        FileChooserDescriptorFactory.createSingleFileDescriptor("jar")
                            .withTitle("Select Nextflow LSP Server JAR"),
                        project
                    ).bindText(
                        getter = { state.customLspJarPath },
                        setter = { state.customLspJarPath = it }
                    ).comment("Path to a local <b>language-server-all.jar</b>. Leave empty to auto-download the latest release from GitHub.")
                }
            }

            group("Diagnostics") {
                row("Error Reporting:") {
                    comboBox(listOf("off", "errors", "warnings", "paranoid"))
                        .bindItem(
                            getter = { state.errorReportingMode },
                            setter = { state.errorReportingMode = it ?: "warnings" }
                        )
                }.rowComment(
                    "<b>off</b> – disabled · <b>errors</b> – errors only · " +
                    "<b>warnings</b> – errors and warnings (default) · " +
                    "<b>paranoid</b> – all hints and best-practice checks"
                )
            }

            group("Language") {
                row("Nextflow Version:") {
                    val versionField = textField()
                        .bindText(
                            getter = { state.languageVersion },
                            setter = { state.languageVersion = it.trim() }
                        )
                    button("Detect") {
                        settings.detectInstalledVersion()?.let { detected ->
                            versionField.component.text = detected
                        }
                    }
                }.rowComment("Language version used for analysis, e.g. <b>26.04</b>.")
            }

            group("Completion") {
                row {
                    checkBox("Extended completion (additional DSL keywords and built-in patterns)")
                        .bindSelected(
                            getter = { state.completionExtended },
                            setter = { state.completionExtended = it }
                        )
                }
                row("Max items:") {
                    intTextField(1..1000)
                        .bindIntText(
                            getter = { state.completionMaxItems },
                            setter = { state.completionMaxItems = it }
                        )
                }.rowComment("Maximum number of completion suggestions returned by the server.")
            }

            group("Formatting") {
                row {
                    checkBox("Harshil alignment (align channel operators)")
                        .bindSelected(
                            getter = { state.formattingHarshilAlignment },
                            setter = { state.formattingHarshilAlignment = it }
                        )
                }
                row {
                    checkBox("Sort declarations alphabetically")
                        .bindSelected(
                            getter = { state.formattingSortDeclarations },
                            setter = { state.formattingSortDeclarations = it }
                        )
                }
            }

            group("Files") {
                row("Exclude patterns:") {
                    expandableTextField()
                        .bindText(
                            getter = { state.filesExclude },
                            setter = { state.filesExclude = it }
                        )
                }.rowComment("Comma-separated list of directories or glob patterns excluded from analysis.")
            }

            group("Advanced") {
                row {
                    checkBox("Enable LSP server debug logging")
                        .bindSelected(
                            getter = { state.debugMode },
                            setter = { state.debugMode = it }
                        )
                }
            }
        }
    }

    override fun apply() {
        super.apply()
        LspServerManager.getInstance(project)
            .stopAndRestartIfNeeded(NextflowLspServerSupportProvider::class.java)
    }
}
