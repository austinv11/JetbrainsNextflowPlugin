package com.austinv11.nextflow.lsp

import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile

internal data class NextflowLspVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<NextflowLspVersion> {
    val series: String get() = "$major.${minor.toString().padStart(2, '0')}"
    override fun toString(): String = "$series.$patch"
    override fun compareTo(other: NextflowLspVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        fun parse(value: String): NextflowLspVersion? {
            val match = Regex("""v?(\d+)\.(\d+)(?:\.(\d+))?""").matchEntire(value.trim()) ?: return null
            val patchText = match.groupValues[3]
            return NextflowLspVersion(
                match.groupValues[1].toIntOrNull() ?: return null,
                match.groupValues[2].toIntOrNull() ?: return null,
                if (patchText.isEmpty()) 0 else patchText.toIntOrNull() ?: return null
            )
        }
    }
}

internal data class NextflowLspRelease(val version: NextflowLspVersion, val downloadUrl: String)

internal data class NextflowLspReleasePage(
    val releases: List<NextflowLspRelease>,
    val hasNext: Boolean,
    val rawCount: Int = releases.size
) {
    companion object {
        fun parse(json: String, hasNext: Boolean): NextflowLspReleasePage = try {
            val raw = JsonParser.parseString(json).asJsonArray
            val releases = raw.mapNotNull { element ->
                val release = element.asJsonObject
                if (release.get("draft")?.asBoolean == true || release.get("prerelease")?.asBoolean == true) return@mapNotNull null
                val version = NextflowLspVersion.parse(release.get("tag_name")?.asString.orEmpty()) ?: return@mapNotNull null
                val asset = release.getAsJsonArray("assets")?.firstOrNull {
                    it.asJsonObject.get("name")?.asString == "language-server-all.jar"
                }?.asJsonObject ?: return@mapNotNull null
                val url = asset.get("browser_download_url")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                NextflowLspRelease(version, url)
            }
            NextflowLspReleasePage(releases, hasNext, raw.size())
        } catch (e: RuntimeException) {
            throw IOException("Invalid language-server release response", e)
        }
    }
}

internal data class NextflowLspCachedServer(val file: File, val version: String?)

/** Resolves official releases independently of IDE state, with immutable, versioned JARs. */
internal class NextflowLspServerCache(
    private val directory: File,
    private val fetchPage: (Int) -> NextflowLspReleasePage,
    private val download: (String, File) -> Unit,
    private val logFailure: (Exception) -> Unit = {},
    private val legacyDirectories: List<File> = emptyList()
) {
    private val versionsDirectory = File(directory, "servers")
    private val lastUsedFile = File(directory, "last-used-version.txt")

    // The application shares one cache instance; serialize resolution so projects cannot race downloads.
    @Synchronized
    fun resolve(languageVersion: String): NextflowLspCachedServer {
        val requested = requestedVersion(languageVersion)
        // Updating an installed series requires consent through the update notification.
        try {
            matchingCached(requested)?.let { cached ->
                val version = NextflowLspVersion.parse(cached.version.orEmpty())!!
                return store(version) { destination ->
                    Files.copy(cached.file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            return install(latestRelease(languageVersion))
        } catch (e: IOException) {
            logFailure(e)
            return fallback(requested) ?: throw IOException(
                "Could not obtain a Nextflow ${requested.series} language server and no usable cached JAR exists. " +
                    "Retry when online or configure a Custom JAR in Nextflow settings.", e
            )
        }
    }

    @Synchronized
    fun latestRelease(languageVersion: String): NextflowLspRelease {
        val requested = requestedVersion(languageVersion)
        var newest: NextflowLspRelease? = null
        val availableSeries = sortedSetOf<String>()
        var pageNumber = 1
        do {
            var page = fetchPage(pageNumber)
            // An empty first page has been observed even though the repository has published releases.
            if (pageNumber == 1) {
                repeat(2) {
                    if (page.rawCount == 0) page = fetchPage(1)
                }
                if (page.rawCount == 0) throw IOException("GitHub returned an empty language-server release list after three attempts. Retry the update check.")
            }
            availableSeries.addAll(page.releases.map { it.version.series })
            val candidate = page.releases.filter { it.version.series == requested.series }.maxByOrNull { it.version }
            if (candidate != null && (newest == null || candidate.version > newest.version)) newest = candidate
            pageNumber++
        } while (page.hasNext)
        return newest ?: throw IOException(
            "No stable language-server JAR release found for Nextflow ${requested.series}. Available release series: ${availableSeries.joinToString()}"
        )
    }

    @Synchronized
    fun findUpdate(languageVersion: String): NextflowLspRelease? {
        val current = matchingCached(requestedVersion(languageVersion))
            ?.version?.let { NextflowLspVersion.parse(it) }
        val latest = latestRelease(languageVersion)
        return latest.takeIf { current == null || it.version > current }
    }

    @Synchronized
    fun install(release: NextflowLspRelease): NextflowLspCachedServer = store(release.version) { destination ->
        val existing = cachedServers().firstOrNull { NextflowLspVersion.parse(it.version.orEmpty()) == release.version }
        if (existing != null) {
            Files.copy(existing.file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } else {
            download(release.downloadUrl, destination)
        }
    }

    private fun store(version: NextflowLspVersion, writeJar: (File) -> Unit): NextflowLspCachedServer {
        val jar = File(versionsDirectory, "$version/language-server-all.jar")
        if (!isUsableJar(jar)) {
            Files.createDirectories(jar.parentFile.toPath())
            val temporary = Files.createTempFile(jar.parentFile.toPath(), "download-", ".tmp").toFile()
            try {
                writeJar(temporary)
                if (!isUsableJar(temporary)) throw IOException("Downloaded language-server JAR is incomplete or invalid")
                publish(temporary, jar)
            } finally {
                Files.deleteIfExists(temporary.toPath())
            }
        }
        return remember(NextflowLspCachedServer(jar, version.toString()))
    }

    private fun requestedVersion(languageVersion: String): NextflowLspVersion = NextflowLspVersion.parse(languageVersion)
        ?: throw IllegalArgumentException("Invalid Nextflow version. Use a release series such as 26.04 or 26.04.2, or configure a Custom JAR in Nextflow settings.")

    private fun matchingCached(requested: NextflowLspVersion): NextflowLspCachedServer? = cachedServers()
        .filter { NextflowLspVersion.parse(it.version.orEmpty())?.series == requested.series }
        .maxByOrNull { NextflowLspVersion.parse(it.version.orEmpty())!! }

    private fun fallback(requested: NextflowLspVersion): NextflowLspCachedServer? {
        val cached = cachedServers()
        val matching = matchingCached(requested)
        if (matching != null) return remember(matching)
        val lastUsed = (listOf(directory) + legacyDirectories).firstNotNullOfOrNull { root ->
            runCatching { File(root, "last-used-version.txt").readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()
        }
        return cached.firstOrNull { it.version == lastUsed } ?: cached.maxByOrNull { it.file.lastModified() }
    }

    // Older caches under the plugin installation directory are read-only: writes there reload the plugin in runIde.
    private fun cachedServers(): List<NextflowLspCachedServer> = (listOf(directory) + legacyDirectories).flatMap { root ->
        val cached = File(root, "servers").listFiles().orEmpty().mapNotNull { folder ->
            val version = NextflowLspVersion.parse(folder.name) ?: return@mapNotNull null
            val jar = File(folder, "language-server-all.jar")
            if (folder.isDirectory && isUsableJar(jar)) NextflowLspCachedServer(jar, version.toString()) else null
        }.toMutableList()
        val legacyJar = File(root, "language-server-all.jar")
        if (isUsableJar(legacyJar)) {
            val legacyVersion = runCatching { File(root, "version.txt").readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()
            cached.add(NextflowLspCachedServer(legacyJar, legacyVersion))
        }
        cached
    }

    private fun remember(server: NextflowLspCachedServer): NextflowLspCachedServer {
        try {
            Files.createDirectories(directory.toPath())
            val temporary = Files.createTempFile(directory.toPath(), "last-used-", ".tmp").toFile()
            try {
                temporary.writeText(server.version.orEmpty())
                publish(temporary, lastUsedFile)
            } finally {
                Files.deleteIfExists(temporary.toPath())
            }
        } catch (e: IOException) {
            logFailure(e) // A metadata failure must not prevent use of a complete JAR.
        }
        return server
    }

    private fun isUsableJar(file: File): Boolean = file.isFile && runCatching {
        JarFile(file).use { !it.manifest?.mainAttributes?.getValue("Main-Class").isNullOrBlank() }
    }.getOrDefault(false)

    private fun publish(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
