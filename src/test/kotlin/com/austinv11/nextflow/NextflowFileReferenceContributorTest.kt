package com.austinv11.nextflow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiElement
import com.intellij.patterns.ElementPattern
import com.intellij.util.ProcessingContext

class NextflowFileReferenceContributorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("path/to/my_file.txt", "content")
    }

    fun testFileReferenceIsProvidedForFileFunction() {
        myFixture.configureByText("main.nf", "file('path/to/my_<caret>file.txt')")
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!

        var providerToTest: PsiReferenceProvider? = null

        val contributor = NextflowFileReferenceContributor()
        contributor.registerReferenceProviders(object : PsiReferenceRegistrar() {
            override fun <T : PsiElement> registerReferenceProvider(pattern: ElementPattern<T>, provider: PsiReferenceProvider, priority: Double) {
                providerToTest = provider
            }
        })

        val references = providerToTest!!.getReferencesByElement(element, ProcessingContext())

        assertTrue("References should not be empty", references.isNotEmpty())
        val resolved = references.last().resolve()
        assertNotNull("Reference should resolve", resolved)
        assertEquals("my_file.txt", (resolved as com.intellij.psi.PsiFileSystemItem).name)
    }

    fun testFileReferenceIsProvidedForPathFunction() {
        myFixture.configureByText("main.nf", "path('path/to/my_<caret>file.txt')")
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!

        var providerToTest: PsiReferenceProvider? = null
        val contributor = NextflowFileReferenceContributor()
        contributor.registerReferenceProviders(object : PsiReferenceRegistrar() {
            override fun <T : PsiElement> registerReferenceProvider(pattern: ElementPattern<T>, provider: PsiReferenceProvider, priority: Double) {
                providerToTest = provider
            }
        })

        val references = providerToTest!!.getReferencesByElement(element, ProcessingContext())

        assertTrue("References should not be empty", references.isNotEmpty())
        val resolved = references.last().resolve()
        assertNotNull("Reference should resolve", resolved)
        assertEquals("my_file.txt", (resolved as com.intellij.psi.PsiFileSystemItem).name)
    }

    fun testNoReferenceForTemplatedString() {
        myFixture.configureByText("main.nf", "file(\"path/to/\${var}/my_<caret>file.txt\")")
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!

        var providerToTest: PsiReferenceProvider? = null
        val contributor = NextflowFileReferenceContributor()
        contributor.registerReferenceProviders(object : PsiReferenceRegistrar() {
            override fun <T : PsiElement> registerReferenceProvider(pattern: ElementPattern<T>, provider: PsiReferenceProvider, priority: Double) {
                providerToTest = provider
            }
        })

        val references = providerToTest!!.getReferencesByElement(element, ProcessingContext())
        assertTrue("Templated strings should not have file references from our contributor", references.isEmpty())
    }

    fun testNoReferenceForOtherFunctions() {
        myFixture.configureByText("main.nf", "someOtherFunc('path/to/my_<caret>file.txt')")
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!

        var providerToTest: PsiReferenceProvider? = null
        val contributor = NextflowFileReferenceContributor()
        contributor.registerReferenceProviders(object : PsiReferenceRegistrar() {
            override fun <T : PsiElement> registerReferenceProvider(pattern: ElementPattern<T>, provider: PsiReferenceProvider, priority: Double) {
                providerToTest = provider
            }
        })

        val references = providerToTest!!.getReferencesByElement(element, ProcessingContext())
        assertTrue("Should not have file references from our contributor", references.isEmpty())
    }
}
