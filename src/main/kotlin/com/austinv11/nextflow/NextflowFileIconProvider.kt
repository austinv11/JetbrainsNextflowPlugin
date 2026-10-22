package com.austinv11.nextflow

import com.austinv11.nextflow.util.NextflowFileUtils
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class NextflowFileIconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file.isDirectory || project == null || project.isDisposed) return null
        return ReadAction.compute<Icon?, RuntimeException> {
            // Keep Nextflow icons even when TextMate supplies the file type and parser.
            if (NextflowFileUtils.isNextflowFile(file) || NextflowFileUtils.isNextflowConfig(project, file)) {
                NextflowIcons.FILE
            } else null
        }
    }
}
