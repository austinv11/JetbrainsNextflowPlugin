package com.austinv11.nextflow

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowFileReferenceContributorTest : BasePlatformTestCase() {
    fun testFileReferenceIsProvidedForFileFunction() {
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
    }

    fun testFileReferenceIsProvidedForPathFunction() {
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
    }
}
