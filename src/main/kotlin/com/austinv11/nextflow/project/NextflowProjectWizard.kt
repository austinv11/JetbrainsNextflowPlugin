package com.austinv11.nextflow.project

import com.austinv11.nextflow.NextflowIcons
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import javax.swing.Icon

class NextflowProjectWizard : LanguageGeneratorNewProjectWizard {
    override val name: String = "Nextflow"
    override val ordinal: Int = 100
    override val icon: Icon = NextflowIcons.FILE

    override fun createStep(parent: NewProjectWizardStep): NewProjectWizardStep {
        return Step(parent)
    }

    class Step(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {
        override fun setupProject(project: Project) {
            val baseDir = project.guessProjectDir() ?: return

            ApplicationManager.getApplication().runWriteAction {
                try {
                    // main.nf
                    val mainNf = baseDir.createChildData(this, "main.nf")
                    mainNf.setBinaryContent("""
                        #!/usr/bin/env nextflow

                        log.info ""${'"'}
                            P I P E L I N E
                            ===============
                        ""${'"'}.stripIndent()

                        workflow {
                            // Your pipeline logic here
                        }
                    """.trimIndent().toByteArray())

                    // nextflow.config
                    val nextflowConfig = baseDir.createChildData(this, "nextflow.config")
                    nextflowConfig.setBinaryContent("""
                        manifest {
                            name = 'Nextflow Project'
                            description = 'A Nextflow pipeline'
                            mainScript = 'main.nf'
                        }

                        params {
                            // Pipeline parameters
                        }
                    """.trimIndent().toByteArray())

                    // Directories
                    baseDir.createChildDirectory(this, "modules")
                    baseDir.createChildDirectory(this, "conf")

                } catch (e: Exception) {
                    // Log or handle error if needed
                }
            }
        }
    }
}
