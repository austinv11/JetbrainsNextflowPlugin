package com.austinv11.nextflow

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.InputStream

class LspProgressPatchInputStream(private val input: InputStream) : InputStream() {

    private var buffer: ByteArray = ByteArray(0)
    private var offset: Int = 0

    override fun read(): Int {
        if (offset >= buffer.size) {
            if (!fillBuffer()) return -1
        }
        return buffer[offset++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (offset >= buffer.size) {
            if (!fillBuffer()) return -1
        }
        val toRead = minOf(len, buffer.size - offset)
        System.arraycopy(buffer, offset, b, off, toRead)
        offset += toRead
        return toRead
    }

    private fun fillBuffer(): Boolean {
        val headers = mutableListOf<String>()
        var contentLength: Int? = null

        val firstLine = readLine() ?: return false
        if (!firstLine.startsWith("Content-Length", ignoreCase = true)) {
            buffer = (firstLine + "\n").toByteArray(Charsets.UTF_8)
            offset = 0
            return true
        }
        headers.add(firstLine)
        val firstIdx = firstLine.indexOf(':')
        if (firstIdx > 0 && firstLine.substring(0, firstIdx).equals("Content-Length", ignoreCase = true)) {
            contentLength = firstLine.substring(firstIdx + 1).trim().toIntOrNull()
        }

        while (true) {
            val line = readLine() ?: return false
            if (line.isEmpty()) break
            headers.add(line)
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).equals("Content-Length", ignoreCase = true)) {
                contentLength = line.substring(idx + 1).trim().toIntOrNull()
            }
        }

        val length = contentLength ?: return false
        val body = readFully(length)
        val patchedBody = patchBody(body)

        val headerBuilder = StringBuilder()
        headerBuilder.append("Content-Length: ").append(patchedBody.size).append("\r\n")
        headers.filterNot { it.startsWith("Content-Length", ignoreCase = true) }
               .forEach { headerBuilder.append(it).append("\r\n") }
        headerBuilder.append("\r\n")

        buffer = headerBuilder.toString().toByteArray(Charsets.UTF_8) + patchedBody
        offset = 0
        return true
    }

    private fun readLine(): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val ch = input.read()
            if (ch == -1) return if (out.size() == 0) null else out.toString(Charsets.UTF_8.name())
            if (ch == '\n'.code) break
            if (ch != '\r'.code) out.write(ch)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun readFully(length: Int): ByteArray {
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val r = input.read(buf, read, length - read)
            if (r == -1) break
            read += r
        }
        return if (read == length) buf else buf.copyOf(read)
    }

    private fun patchBody(body: ByteArray): ByteArray {
        val text = body.toString(Charsets.UTF_8)
        val root = try {
            JsonParser.parseString(text).asJsonObject
        } catch (_: Exception) {
            return body
        }

        if (root["method"]?.asString != "$/progress") return body
        val params = root["params"] as? JsonObject ?: return body
        val value = params["value"] as? JsonObject ?: return body
        if (value["kind"]?.asString != "begin") return body

        if (!value.has("title") || value["title"].isJsonNull) {
            value.addProperty("title", "")
            return root.toString().toByteArray(Charsets.UTF_8)
        }

        return body
    }
}
