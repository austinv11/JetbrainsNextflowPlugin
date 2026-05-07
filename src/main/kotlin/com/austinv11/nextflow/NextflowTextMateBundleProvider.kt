package com.austinv11.nextflow

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider

class NextflowTextMateBundleProvider : TextMateBundleProvider {

    override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
        val pluginPath = PluginManagerCore.getPlugin(
            PluginId.getId("com.austinv11.nextflow")
        )?.pluginPath ?: return emptyList()

        return listOf(
            TextMateBundleProvider.PluginBundle(
                "Nextflow", pluginPath.resolve("resources/nextflow.tmLanguage.json")
            ), TextMateBundleProvider.PluginBundle(
                "Nextflow Config", pluginPath.resolve("resources/nextflow-config.tmLanguage.json")
            ), TextMateBundleProvider.PluginBundle(
                "Nextflow Markdown Injection", pluginPath.resolve("resources/nextflow-markdown-injection.tmLanguage.json")
            ), TextMateBundleProvider.PluginBundle(
                "Nextflow Groovy", pluginPath.resolve("resources/groovy.tmLanguage.json")
            )
        )
    }
}