package com.austinv11.nextflow.execution

import com.austinv11.nextflow.NextflowFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowRunConfigurationProducerTest : BasePlatformTestCase() {
    fun testProducer() {
        val isJavaAvailable = try {
            Class.forName("com.intellij.execution.remote.RemoteConfiguration")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

        if (!isJavaAvailable) {
            println("Skipping test because java support is disabled")
            return
        }
    }
}
