package com.austinv11.nextflow

import com.intellij.lang.Language
import org.jetbrains.plugins.groovy.GroovyLanguage

object NextflowLanguage : Language(GroovyLanguage, "Nextflow") {
    private fun readResolve(): Any = NextflowLanguage
}
