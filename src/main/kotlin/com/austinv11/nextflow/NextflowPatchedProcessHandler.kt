package com.austinv11.nextflow

import com.intellij.execution.process.OSProcessHandler
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset

class NextflowPatchedProcessHandler(
    process: Process,
    commandLine: String,
    charset: Charset
) : OSProcessHandler(process, commandLine, charset) {

    override fun createProcessOutReader(): Reader {
        val processInput = process.inputStream
        val patched = LspProgressPatchInputStream(processInput)
        return InputStreamReader(patched, charset)
    }
}

