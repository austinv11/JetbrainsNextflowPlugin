package com.austinv11.nextflow.nfcore

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NfCoreRunnerTest : BasePlatformTestCase() {

    fun testExecuteCommandArgsDoesNotCrashWithoutTerminal() {
        NfCoreRunner.executeCommandArgs(project, "Test", listOf("foo", "bar"))
    }
}
