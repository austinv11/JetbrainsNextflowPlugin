package com.austinv11.nextflow.execution

import com.austinv11.nextflow.util.NextflowEnvironmentUtils
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume.assumeTrue

class NextflowRunLineMarkerContributorTest : BasePlatformTestCase() {
    fun testLineMarkers() {
        assumeTrue("Native run markers require Groovy", NextflowEnvironmentUtils.isGroovyAvailable)
        // Opening an editor also starts LSP, whose asynchronous restarts race daemon highlighting.
        // Exercise this contributor directly without launching an unrelated server.
        val file = myFixture.addFileToProject("main.nf", "process foo { }\nworkflow { }\n")
        val contributor = NextflowRunLineMarkerContributor()
        val markers = PsiTreeUtil.collectElements(file) { it is LeafPsiElement && it.text in listOf("process", "workflow") }
            .mapNotNull { contributor.getLineMarkerInfo(it) }
        assertEquals(2, markers.size)
        assertEquals(listOf("process", "workflow"), markers.map { it.element!!.text })
    }
}
