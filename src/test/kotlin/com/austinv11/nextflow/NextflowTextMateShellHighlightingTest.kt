package com.austinv11.nextflow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateHighlighter
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateHighlightingLexer

class NextflowTextMateShellHighlightingTest : BasePlatformTestCase() {
    fun testAwkPipelineColors() {
        val dollar = '$'
        val bash = """echo "Aggregating"
sleep 10
awk -F',' 'NR>1 {print \${dollar}2, \${dollar}3}' ${dollar}{matrix_csv} | sort | awk '{a[\${dollar}1]+=\${dollar}2} END {for (i in a) print i "\t" a[i]}' > ${dollar}{sample_id}_features.tsv"""
        val wrapped = "process EXAMPLE {\nscript:\n\"\"\"\n$bash\n\"\"\"\n}"
        val embedded = scopes(wrapped)
        val standalone = scopes(bash, "example.sh")
        for (word in listOf("echo", "Aggregating", "sleep", "10", "awk", "-F", "NR>1", "sort", "|", "> \${sample_id}")) {
            val offset = bash.indexOf(word)
            val actual = embedded.first { offset + wrapped.indexOf(bash) in it.start until it.end }
            val expected = standalone.first { offset in it.start until it.end }
            assertTrue("Bash embedding must survive preceding lines at '$word': $actual",
                actual.scope.contains("meta.embedded.block.shellscript"))
            assertEquals("Embedded '$word' must use the same colors as a Bash file", expected.colors, actual.colors)
        }
        for (variable in listOf("matrix_csv", "sample_id")) {
            val offset = wrapped.indexOf(variable)
            assertTrue("Nextflow interpolation must survive preceding Bash lines: $variable", embedded.any {
                offset in it.start until it.end && it.scope.contains("variable.other.interpolated.nextflow")
            })
        }
        val closingQuote = wrapped.lastIndexOf("\"\"\"")
        assertTrue("Closing quotes must stay part of the Nextflow string", embedded.any {
            closingQuote in it.start until it.end && it.scope.contains("string.quoted.double.multiline.nextflow")
        })
    }

    fun testTripleQuotedScriptsUseBashScopesWithoutGroovy() {
        for (quote in listOf("\"\"\"", "'''")) {
            val text = listOf(
                "process EXAMPLE {", "    script:", "    $quote",
                "    if [ -f input.txt ]; then", "      echo hello", "    fi",
                "    $quote", "}", "def outside = 1"
            ).joinToString("\n")
            val scopes = scopes(text)
            val offset = text.indexOf("if [")
            assertTrue("Expected Bash highlighting for $quote at $offset: $scopes", scopes.any {
                offset in it.start until it.end && it.scope.contains("keyword.control") && it.scope.contains("shell")
            })
            val outside = text.indexOf("outside")
            assertFalse("Bash scopes must end with the script string ($quote): $scopes", scopes.any {
                outside in it.start until it.end && it.scope.contains("meta.embedded.block.shellscript")
            })
        }
    }

    fun testOrdinaryMultilineStringsAreNotBash() {
        val text = "def message = '''\nif this is only text\n'''\n"
        val scopes = scopes(text)
        assertFalse(scopes.any { it.scope.contains("meta.embedded.block.shellscript") })
        val content = text.indexOf("if this")
        assertTrue("Ordinary strings retain lexical coloring without semantic string colors", scopes.any {
            content in it.start until it.end && "DEFAULT_STRING" in it.colors
        })
    }

    fun testNextflowInterpolationInsideBash() {
        val dollar = '$'
        val text = "process EXAMPLE {\nscript:\n\"\"\"\necho $dollar{params.message}\n\"\"\"\n}"
        val offset = text.indexOf("params.message")
        assertTrue("Nextflow interpolation must take priority over Bash expansion", scopes(text).any {
            offset in it.start until it.end && it.scope.contains("variable.other.interpolated.nextflow")
        })
    }

    fun testSingleQuotedBashDoesNotInterpolateNextflow() {
        val text = "process EXAMPLE {\nscript:\n'''\necho \${HOME}\n'''\n}"
        assertFalse(scopes(text).any { it.scope.contains("variable.other.interpolated.nextflow") })
    }

    private fun scopes(text: String, fileName: String = "main.nf"): List<TokenScope> {
        val service = TextMateService.getInstance()
        val language = service.getLanguageDescriptorByFileName(fileName)!!
        val lexer = TextMateHighlightingLexer(language, -1)
        val highlighter = TextMateHighlighter(lexer)
        lexer.start(text)
        return buildList {
            while (lexer.tokenType != null) {
                val scope = (lexer.tokenType as? TextMateElementType)?.scope?.toString().orEmpty()
                val colors = highlighter.getTokenHighlights(lexer.tokenType).map { it.externalName }
                add(TokenScope(lexer.tokenStart, lexer.tokenEnd, scope, colors))
                lexer.advance()
            }
        }
    }

    private data class TokenScope(val start: Int, val end: Int, val scope: String, val colors: List<String>)
}
