package com.austinv11.nextflow

import com.austinv11.nextflow.util.NextflowFileUtils
import com.austinv11.nextflow.util.NextflowEnvironmentUtils
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

class NextflowFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        // If Groovy isn't available, we don't map anything to NextflowFileType here so that TextMate can automatically intercept
        // the files based on its bundled grammar package.json (which maps .nf, .config, and .groovy).
        if (!NextflowEnvironmentUtils.isGroovyAvailable) {
            return null
        }

        return if (NextflowFileUtils.isNextflowFile(file)) {
            NextflowFileType.INSTANCE
        } else {
            null
        }
    }
}