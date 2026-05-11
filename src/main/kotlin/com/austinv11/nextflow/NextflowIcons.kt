package com.austinv11.nextflow

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object NextflowIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/nextflow-icon.svg", NextflowIcons::class.java)

    @JvmField
    val NF_CORE: Icon = IconLoader.getIcon("/icons/nf-core-icon.svg", NextflowIcons::class.java)
}
