package com.austinv11.nextflow

import com.austinv11.nextflow.lsp.NextflowLspServerSupportProvider
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
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
            var execFieldText = ""

            group("Execution") {
                row("Nextflow executable:") {
                    val execField = textFieldWithBrowseButton(
                        FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle("Select Nextflow Executable"),
                        project
                    ).bindText(
                        getter = { state.nextflowBinaryPath },
                        setter = { state.nextflowBinaryPath = it }
                    ).comment("Path to the Nextflow binary. Leave empty to auto-detect from PATH.")

                    execField.component.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { execFieldText = execField.component.text }
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { execFieldText = execField.component.text }
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { execFieldText = execField.component.text }
                    })
                    execFieldText = state.nextflowBinaryPath
                }
            }

            group("LSP Server") {
                row("Java Home:") {
                    textFieldWithBrowseButton(
                        FileChooserDescriptorFactory.createSingleFolderDescriptor()
                            .withTitle("Select Java Home Folder"),
                        project
                    ).bindText(
                        getter = { state.javaHome },
                        setter = { state.javaHome = it }
                    ).comment("Specify the folder path to the desired Java runtime (e.g., equivalent to JAVA_HOME). Leave empty to use IDE default.")
                }
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
                        val version = settings.detectInstalledVersion(execFieldText)
                        if (version != null) {
                            versionField.component.text = version
                        } else {
                            Messages.showErrorDialog(project, "Could not detect Nextflow version. Please check the executable path.", "Nextflow Detection Failed")
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
                    checkBox("Mahesh form (place process outputs at the end of the process body)")
                        .bindSelected(
                            getter = { state.formattingMaheshForm },
                            setter = { state.formattingMaheshForm = it }
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
