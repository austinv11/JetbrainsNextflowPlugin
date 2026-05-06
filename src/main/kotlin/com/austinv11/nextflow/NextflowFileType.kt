package com.austinv11.nextflow

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.groovy.GroovyLanguage
import javax.swing.Icon

class NextflowFileType private constructor() : LanguageFileType(GroovyLanguage) {

    override fun getName() = "Nextflow"
    override fun getDescription() = "Nextflow pipeline file"
    override fun getDefaultExtension() = "nf"
    override fun getIcon(): Icon = NextflowIcons.FILE

    companion object {
        @JvmField
        val INSTANCE = NextflowFileType()
    }
}