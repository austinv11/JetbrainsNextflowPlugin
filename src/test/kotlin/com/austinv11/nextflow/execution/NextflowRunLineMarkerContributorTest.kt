package com.austinv11.nextflow.execution

import com.austinv11.nextflow.NextflowFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowRunLineMarkerContributorTest : BasePlatformTestCase() {
    fun testLineMarkers() {
        val isGroovyAvailable = try {
            Class.forName("org.jetbrains.plugins.groovy.highlighter.GroovyCommenter")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

        if (!isGroovyAvailable) {
            println("Skipping test because Groovy plugin is disabled")
            return
        }

        myFixture.configureByText(NextflowFileType.INSTANCE, "process foo { }")
        val markers = myFixture.findAllGutters()
    }
}
