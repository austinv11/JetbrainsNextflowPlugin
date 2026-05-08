package com.austinv11.nextflow.execution

import com.austinv11.nextflow.NextflowFileType
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.execution.lineMarker.RunLineMarkerContributor

class NextflowRunLineMarkerContributorTest : BasePlatformTestCase() {

    fun testLineMarkers() {
        val fileText = """
            // Unnamed workflow
            workflow {
            }

            // Named workflow
            workflow foo {
            }

            // Named workflow with newlines
            workflow
            bar
            {
            }
            
            // Named process
            process blast {
            }
            
            // Should NOT have line marker - just a random word
            def workflow_foo = 1
            
            // Should NOT have line marker - just a variable named workflow
            def workflow = 1
        """.trimIndent()

        val file = myFixture.configureByText(NextflowFileType.INSTANCE, fileText)

        val contributor = NextflowRunLineMarkerContributor()

        var element = file.firstChild
        val actualMarkersText = mutableListOf<String>()
        while (element != null) {
            val info = contributor.getInfo(element)
            if (info != null) {
                actualMarkersText.add(element.text)
            }
            element = element.nextSibling
        }

        println("FOUND MARKERS: " + actualMarkersText)
        assertEquals(listOf("workflow", "workflow", "workflow", "process"), actualMarkersText)
    }
}
