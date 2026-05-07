package com.austinv11.nextflow

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

class NextflowFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        return if (file.name == "nextflow.config") {
            NextflowFileType.INSTANCE
        } else {
            null
        }
    }
}

