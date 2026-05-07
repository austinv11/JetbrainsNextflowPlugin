package com.austinv11.nextflow

import com.intellij.execution.process.OSProcessHandler
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Wraps the server process so that getInputStream() is intercepted by
 * LspProgressPatchInputStream before any reader (lsp4j or the OSProcessHandler
 * pumping thread) sees the raw bytes. A single LspProgressPatchInputStream
 * instance is shared across all callers via the Process wrapper, avoiding
 * the competing-readers deadlock that occurs when createProcessOutReader()
 * creates a fresh wrapper per call.
 */
class NextflowPatchedProcessHandler(
    process: Process,
    commandLine: String,
) : OSProcessHandler(PatchedProcess(process), commandLine, Charsets.UTF_8)

private class PatchedProcess(private val delegate: Process) : Process() {
    // Single shared instance — the only reader of delegate.inputStream.
    private val patchedInput = LspProgressPatchInputStream(delegate.inputStream)

    override fun getInputStream(): InputStream = patchedInput
    override fun getOutputStream(): OutputStream = delegate.outputStream
    override fun getErrorStream(): InputStream = delegate.errorStream
    override fun waitFor(): Int = delegate.waitFor()
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = delegate.waitFor(timeout, unit)
    override fun exitValue(): Int = delegate.exitValue()
    override fun destroy() = delegate.destroy()
    override fun destroyForcibly(): Process = delegate.destroyForcibly()
    override fun isAlive(): Boolean = delegate.isAlive
}
