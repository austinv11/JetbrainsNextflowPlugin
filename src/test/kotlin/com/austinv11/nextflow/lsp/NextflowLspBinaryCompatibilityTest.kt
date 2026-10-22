package com.austinv11.nextflow.lsp

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class NextflowLspBinaryCompatibilityTest {
    @Test
    fun `requests link through the interface instead of the removed default argument bridge`() {
        // LspServer inherits sendRequestSync from LspClient in newer 2026.1 IDEs.
        // JVM interface calls resolve inherited methods; Kotlin's static $default calls cannot.
        val requestClasses = listOf(
            "lsp.NextflowFormatAction",
            "lsp.NextflowLspCommandsSupport",
            "lsp.NextflowConvertPipelineToTypedAction",
            "lsp.NextflowConvertScriptToTypedAction",
            "lsp.NextflowConfigPreviewDialogKt",
            "toolwindow.NextflowProjectPanel"
        )
        for (className in requestClasses) {
            val resource = "/com/austinv11/nextflow/${className.replace('.', '/')}.class"
            val reader = javaClass.getResourceAsStream(resource)!!.use { ClassReader(it) }
            var interfaceCalls = 0
            reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean
                        ) {
                            if (owner != "com/intellij/platform/lsp/api/LspServer") return
                            assertFalse(name == "sendRequestSync\$default", "$className uses the removed LSP default-argument bridge")
                            if (name == "sendRequestSync") {
                                assertEquals(Opcodes.INVOKEINTERFACE, opcode, className)
                                assertEquals("(ILkotlin/jvm/functions/Function1;)Ljava/lang/Object;", descriptor, className)
                                interfaceCalls++
                            }
                        }
                    }
                }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            assertEquals(1, interfaceCalls, "Expected a request with an explicit timeout in $className")
        }
    }
}
