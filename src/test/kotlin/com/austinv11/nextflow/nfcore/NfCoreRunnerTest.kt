package com.austinv11.nextflow.nfcore

import com.austinv11.nextflow.NextflowSettings
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.terminal.view.TerminalSendTextBuilder
import java.lang.reflect.Proxy

class NfCoreRunnerTest : BasePlatformTestCase() {

    fun testPluginCanLoadTheReworkedTerminalApi() {
        val plugin = PluginManager.getInstance().findEnabledPlugin(PluginId.getId("com.austinv11.nextflow"))!!
        assertNotNull(plugin.pluginClassLoader!!.loadClass(TerminalToolWindowTabsManager::class.java.name))
    }

    fun testCommandIsExecutedInANamedTerminalTabWithTheProjectDirectory() {
        var command: String? = null
        var execute = false
        val options = mutableMapOf<String, Any?>()
        val textBuilder = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TerminalSendTextBuilder::class.java)) { self, method, args ->
            when (method.name) {
                "shouldExecute" -> { execute = true; self }
                "send" -> { command = args[0] as String; null }
                else -> null
            }
        } as TerminalSendTextBuilder
        val view = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TerminalView::class.java)) { _, method, _ ->
            if (method.name == "createSendTextBuilder") textBuilder else null
        } as TerminalView
        val tab = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TerminalToolWindowTab::class.java)) { _, method, _ ->
            if (method.name == "getView") view else null
        } as TerminalToolWindowTab
        val tabBuilder = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TerminalToolWindowTabBuilder::class.java)) { self, method, args ->
            if (method.name == "createTab") tab else {
                options[method.name] = args?.firstOrNull()
                self
            }
        } as TerminalToolWindowTabBuilder
        val manager = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TerminalToolWindowTabsManager::class.java)) { _, method, _ ->
            if (method.name == "createTabBuilder") tabBuilder else null
        } as TerminalToolWindowTabsManager
        project.replaceService(TerminalToolWindowTabsManager::class.java, manager, testRootDisposable)
        NextflowSettings.getInstance(project).state.nfCoreBinaryPath = "nf-core-test"

        NfCoreRunner.executeCommandArgs(project, "Lint", listOf("lint", "--help"))

        val executable = if (SystemInfo.isWindows) "wsl nf-core-test" else "nf-core-test"
        assertEquals("$executable lint --help", command)
        assertTrue("The command must execute, rather than just being inserted", execute)
        assertEquals("nf-core: Lint", options["tabName"])
        assertEquals(project.basePath, options["workingDirectory"])
        assertEquals(true, options["requestFocus"])
        assertEquals(true, options["deferSessionStartUntilUiShown"])
    }
}
