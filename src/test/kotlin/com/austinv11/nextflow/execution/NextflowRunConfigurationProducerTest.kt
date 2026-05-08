package com.austinv11.nextflow.execution

import com.austinv11.nextflow.NextflowFileType
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement

class NextflowRunConfigurationProducerTest : BasePlatformTestCase() {

    fun testProducer() {
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
        """.trimIndent()

        val file = myFixture.configureByText(NextflowFileType.INSTANCE, fileText)
        val producer = NextflowRunConfigurationProducer()

        // 1. Unnamed workflow
        var element = findElementByText(file, "workflow", 0)
        var context = ConfigurationContext(element)
        var configurationFromContext = producer.createConfigurationFromContext(context)
        assertNotNull(configurationFromContext)
        var config = configurationFromContext!!.configuration as NextflowRunConfiguration
        assertTrue(config.entryName.isNullOrBlank())

        // 2. Named workflow (foo)
        element = findElementByText(file, "workflow", 1)
        context = ConfigurationContext(element)
        configurationFromContext = producer.createConfigurationFromContext(context)
        assertNotNull(configurationFromContext)
        config = configurationFromContext!!.configuration as NextflowRunConfiguration
        assertEquals("foo", config.entryName)

        // 3. Named workflow with newlines (bar)
        element = findElementByText(file, "workflow", 2)
        context = ConfigurationContext(element)
        configurationFromContext = producer.createConfigurationFromContext(context)
        assertNotNull(configurationFromContext)
        config = configurationFromContext!!.configuration as NextflowRunConfiguration
        assertEquals("bar", config.entryName)

        // 4. Named process (blast)
        element = findElementByText(file, "process", 0)
        context = ConfigurationContext(element)
        configurationFromContext = producer.createConfigurationFromContext(context)
        assertNotNull(configurationFromContext)
        config = configurationFromContext!!.configuration as NextflowRunConfiguration
        assertEquals("blast", config.entryName)
    }

    private fun findElementByText(file: PsiElement, text: String, occurrence: Int): PsiElement {
        var count = 0
        var current: PsiElement? = file.firstChild
        while (current != null) {
            if (current is LeafPsiElement && current.text == text) {
                if (count == occurrence) return current
                count++
            }
            current = current.nextSibling
        }
        throw AssertionError("Element not found")
    }
}
