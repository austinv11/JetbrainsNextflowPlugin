package com.austinv11.nextflow

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class NextflowFileType private constructor() : LanguageFileType(NextflowLanguage.INSTANCE) {

    override fun getName() = "Nextflow"
    override fun getDescription() = "Nextflow pipeline file"
    override fun getDefaultExtension() = "nf"
    override fun getIcon(): Icon = NextflowIcons.FILE

    companion object {
        @JvmField
        val INSTANCE = NextflowFileType()
    }
}

class NextflowLanguage private constructor() : Language("Nextflow") {

    companion object {
        @JvmField
        val INSTANCE = NextflowLanguage()
    }}
