package com.austinv11.nextflow.lsp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams

internal data class NextflowConfigPreviewRequest(
    val uri: String,
    val process: String,
    val profiles: List<String> = emptyList(),
    val qualifiedName: String = process
) {
    fun toParams() = ExecuteCommandParams(
        "nextflow.server.previewConfig", listOf(uri, process, profiles, qualifiedName)
    )

    companion object {
        fun fromCommand(command: Command): NextflowConfigPreviewRequest {
            val args = Gson().toJsonTree(command.arguments)
            require(args.isJsonArray && args.asJsonArray.size() >= 2) {
                "The configuration preview is missing its source file or process name."
            }
            val values = args.asJsonArray
            val uri = values[0].stringValue()
            val process = values[1].stringValue()
            require(!uri.isNullOrBlank() && !process.isNullOrBlank()) {
                "The configuration preview is missing its source file or process name."
            }
            val profiles = if (values.size() > 2 && !values[2].isJsonNull) values[2].stringList() else emptyList()
            val qualifiedName = if (values.size() > 3) values[3].stringValue() ?: process else process
            return NextflowConfigPreviewRequest(normalizeNextflowFileUri(uri), process, profiles, qualifiedName)
        }
    }
}

internal data class NextflowConfigPreview(
    val process: String,
    val labels: List<String>,
    val profiles: List<String>,
    val activeProfiles: List<String>,
    val directives: List<Directive>
) {
    data class Directive(val name: String, val layers: List<Layer>)
    data class Layer(
        val value: String,
        val source: String,
        val profile: String?,
        val file: String,
        val line: Int?,
        val active: Boolean
    )

    fun renderText(): String = buildString {
        appendLine("Process: $process")
        appendLine("Labels: ${labels.joinToString().ifEmpty { "none" }}")
        appendLine("Active profiles: ${activeProfiles.joinToString().ifEmpty { "none" }}")
        appendLine()
        if (directives.isEmpty()) appendLine("No matching process directives.")
        for (directive in directives) {
            for (layer in directive.layers) {
                if (layer.active) appendLine("${directive.name} = ${layer.value}")
                else appendLine("  Overridden: ${directive.name} = ${layer.value}")
                val location = layer.file + (layer.line?.let { ":$it" } ?: "")
                append("    From $location (${layer.source}")
                layer.profile?.let { append(", profile: $it") }
                appendLine(")")
            }
            appendLine()
        }
    }

    companion object {
        fun fromResponse(response: Any?): NextflowConfigPreview {
            val root = Gson().toJsonTree(response).objectValue()
            root.get("error")?.stringValue()?.takeIf { it.isNotBlank() }?.let { throw IllegalStateException(it) }
            val result = root.get("result")?.objectValue() ?: unsupportedPreview()
            val directives = result.get("directives")?.takeIf { it.isJsonArray }?.asJsonArray ?: unsupportedPreview()
            return NextflowConfigPreview(
                result.requiredString("process"), result.stringList("labels"), result.stringList("profiles"),
                result.stringList("activeProfiles"), directives.map { entry ->
                    val directive = entry.objectValue()
                    val layers = directive.get("layers")?.takeIf { it.isJsonArray }?.asJsonArray ?: unsupportedPreview()
                    Directive(directive.requiredString("name"), layers.map { item ->
                        val layer = item.objectValue()
                        val active = layer.get("active")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                            ?.asBoolean ?: unsupportedPreview()
                        val line = layer.get("line")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                            ?.asInt?.takeIf { it > 0 }
                        Layer(layer.requiredString("value"), layer.requiredString("source"), layer.get("profile")?.stringValue(),
                            layer.requiredString("file"), line, active)
                    })
                }
            )
        }
    }
}

private fun JsonElement.stringValue(): String? = takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
private fun JsonElement.objectValue(): JsonObject = takeIf { it.isJsonObject }?.asJsonObject ?: unsupportedPreview()
private fun JsonObject.requiredString(name: String): String = get(name)?.stringValue() ?: unsupportedPreview()
private fun JsonObject.stringList(name: String): List<String> = get(name)?.takeUnless { it.isJsonNull }?.stringList() ?: emptyList()
private fun JsonElement.stringList(): List<String> {
    if (!isJsonArray) unsupportedPreview()
    return asJsonArray.map { it.stringValue() ?: unsupportedPreview() }
}
private fun unsupportedPreview(): Nothing = throw IllegalArgumentException("The language server returned an unsupported configuration preview.")
