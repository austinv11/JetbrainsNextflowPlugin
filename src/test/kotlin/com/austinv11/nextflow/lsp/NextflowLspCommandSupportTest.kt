package com.austinv11.nextflow.lsp

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import java.lang.reflect.Proxy

class NextflowLspCommandSupportTest : BasePlatformTestCase() {
    private val scriptCommand = "nextflow.server.convertScriptToTyped"
    private val pipelineCommand = "nextflow.server.convertPipelineToTyped"
    private var state = LspServerState.Running
    private var initialized: InitializeResult? = result(emptyList())
    private var requests = 0
    private lateinit var context: DataContext

    override fun setUp() {
        super.setUp()
        val file = myFixture.addFileToProject("main.nf", "workflow {}\n").virtualFile
        context = DataContext { key ->
            when (key) {
                CommonDataKeys.PROJECT.name -> project
                CommonDataKeys.VIRTUAL_FILE.name -> file
                else -> null
            }
        }
        val server = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(LspServer::class.java)) { _, method, _ ->
            when (method.name) {
                "getState" -> state
                "getInitializeResult" -> initialized
                "getProject" -> project
                "sendRequestSync" -> { requests++; error("Unsupported commands must never be sent") }
                "toString" -> "Regression test server"
                else -> null
            }
        } as LspServer
        val manager = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(LspServerManager::class.java)) { _, method, _ ->
            when (method.name) {
                "getServersForProvider" -> listOf(server)
                "toString" -> "Regression test server manager"
                else -> null
            }
        } as LspServerManager
        project.replaceService(LspServerManager::class.java, manager, testRootDisposable)
    }

    fun testRemovedCommandsAreHiddenWithNewerServerCapabilities() {
        initialized = result(listOf("nextflow.server.previewDag", "nextflow.server.previewConfig"))
        assertVisible(NextflowConvertScriptToTypedAction(), false)
        assertVisible(NextflowConvertPipelineToTypedAction(), false)
    }

    fun testOlderServersKeepSupportedConversions() {
        initialized = result(listOf(scriptCommand, pipelineCommand))
        assertVisible(NextflowConvertScriptToTypedAction(), true)
        assertVisible(NextflowConvertPipelineToTypedAction(), true)
    }

    fun testEachActionRequiresItsOwnAdvertisedCommand() {
        initialized = result(listOf(scriptCommand))
        assertVisible(NextflowConvertScriptToTypedAction(), true)
        assertVisible(NextflowConvertPipelineToTypedAction(), false)
    }

    fun testStoppedServersCannotOfferConversions() {
        initialized = result(listOf(scriptCommand, pipelineCommand))
        state = LspServerState.ShutdownNormally
        assertVisible(NextflowConvertScriptToTypedAction(), false)
        assertVisible(NextflowConvertPipelineToTypedAction(), false)
    }

    fun testMissingCommandProviderOrInitializationIsNotTreatedAsSupport() {
        for (value in listOf(null, InitializeResult(ServerCapabilities()))) {
            initialized = value
            assertVisible(NextflowConvertScriptToTypedAction(), false)
            assertVisible(NextflowConvertPipelineToTypedAction(), false)
        }
    }

    fun testCapabilitiesAreRecheckedAfterTheServerChanges() {
        val action = NextflowConvertScriptToTypedAction()
        initialized = result(listOf(scriptCommand))
        assertVisible(action, true)
        initialized = result(emptyList())
        assertVisible(action, false)
    }

    fun testInvokingAStaleUnsupportedActionDoesNotSendTheCommand() {
        for (action in listOf(NextflowConvertScriptToTypedAction(), NextflowConvertPipelineToTypedAction())) {
            action.actionPerformed(TestActionEvent.createTestEvent(action, context))
        }
        assertEquals(0, requests)
    }

    private fun assertVisible(action: AnAction, expected: Boolean) {
        val event = TestActionEvent.createTestEvent(action, context)
        action.update(event)
        assertEquals(expected, event.presentation.isVisible)
        assertEquals(expected, event.presentation.isEnabled)
    }

    private fun result(commands: List<String>) = InitializeResult(ServerCapabilities().apply {
        executeCommandProvider = ExecuteCommandOptions(commands)
    })
}
