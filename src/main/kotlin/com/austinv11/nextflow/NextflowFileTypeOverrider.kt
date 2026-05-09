package com.austinv11.nextflow

import com.austinv11.nextflow.util.NextflowFileUtils
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

class NextflowFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        return if (NextflowFileUtils.isNextflowConfig(file) || NextflowFileUtils.isNextflowTest(file)) {
            NextflowFileType.INSTANCE
        } else {
            null
        }
    }
}
