package com.austinv11.nextflow.lsp

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class NextflowLspServerCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testVersionsAcceptSeriesPatchAndOptionalPrefix() {
        assertEquals("26.04", NextflowLspVersion.parse("26.04")?.series)
        assertEquals("26.04", NextflowLspVersion.parse(" v26.04.2 ")?.series)
        assertEquals("26.04.0", NextflowLspVersion.parse("26.04").toString())
        for (invalid in listOf("", "latest", "26", "26.04-edge", "26.04.2.3", "../../26.04", "26.04.999999999999")) {
            assertNull(invalid, NextflowLspVersion.parse(invalid))
        }
    }

    @Test
    fun testReleaseParsingIgnoresDraftsPrereleasesAndMissingJars() {
        val json = """[
            {"tag_name":"v26.04.2","assets":[{"name":"language-server-all.jar","browser_download_url":"https://example.test/server.jar"}]},
            {"tag_name":"v26.04.3","draft":true,"assets":[{"name":"language-server-all.jar","browser_download_url":"draft"}]},
            {"tag_name":"v26.04.4","prerelease":true,"assets":[{"name":"language-server-all.jar","browser_download_url":"prerelease"}]},
            {"tag_name":"v26.04.5-edge","assets":[{"name":"language-server-all.jar","browser_download_url":"edge"}]},
            {"tag_name":"v26.04.6","assets":[{"name":"standalone.tar.gz","browser_download_url":"bundle"}]},
            {"tag_name":"v26.04.7","assets":[{"name":"language-server-all.jar","browser_download_url":""}]}
        ]"""
        val page = NextflowLspReleasePage.parse(json, true)
        assertTrue(page.hasNext)
        assertEquals(listOf(release("26.04.2", "https://example.test/server.jar")), page.releases)
    }

    @Test
    fun testOfficialJarIsSelectedAlongsideStandaloneBundles() {
        val json = """[{
            "tag_name":"v26.10.1",
            "assets":[
                {"name":"nextflow-language-server-26.10.1-linux-x64.tar.gz","browser_download_url":"native-linux"},
                {"name":"nextflow-language-server-26.10.1-macos-arm64.tar.gz.sha256","browser_download_url":"checksum"},
                {"name":"language-server-all.jar","browser_download_url":"https://github.com/nextflow-io/language-server/releases/download/v26.10.1/language-server-all.jar"}
            ]
        }]"""
        val page = NextflowLspReleasePage.parse(json, false)
        assertEquals(listOf(release("26.10.1", "https://github.com/nextflow-io/language-server/releases/download/v26.10.1/language-server-all.jar")), page.releases)
    }

    @Test
    fun testInvalidReleaseResponseFallsBackToCachedJar() {
        val expected = cachedJar("26.04.2")
        for (response in listOf("not json", "{\"message\":\"API error\"}", "[null]")) {
            val resolver = NextflowLspServerCache(temporaryFolder.root,
                fetchPage = { NextflowLspReleasePage.parse(response, false) },
                download = { _, _ -> error("Must not download") })
            assertThrows(IOException::class.java) { resolver.latestRelease("26.04") }
            assertEquals(expected, resolver.resolve("26.04").file)
        }
    }

    @Test
    fun testLaterPageFailureUsesCacheInsteadOfIncompleteReleaseResults() {
        val expected = cachedJar("26.04.2")
        val resolver = NextflowLspServerCache(temporaryFolder.root,
            fetchPage = { page ->
                if (page == 1) NextflowLspReleasePage(listOf(release("26.04.3")), true)
                else throw IOException("Next page unavailable")
            }, download = { _, _ -> error("Cannot select newest patch from incomplete results") })
        assertThrows(IOException::class.java) { resolver.latestRelease("26.04") }
        assertEquals(expected, resolver.resolve("26.04").file)
    }

    @Test
    fun testSelectsHighestNumericPatchAcrossAllPagesRegardlessOfPublicationOrder() {
        val requestedPages = mutableListOf<Int>()
        val downloads = mutableListOf<String>()
        val cache = NextflowLspServerCache(temporaryFolder.root, fetchPage = { page ->
            requestedPages.add(page)
            when (page) {
                1 -> NextflowLspReleasePage(listOf(release("26.10.1"), release("26.04.2")), true)
                2 -> NextflowLspReleasePage(listOf(release("26.04.10"), release("25.10.99")), false)
                else -> error("Unexpected page $page")
            }
        }, download = { url, file -> downloads.add(url); writeJar(file) })

        val selected = cache.resolve("26.04.1")
        assertEquals("26.04.10", selected.version)
        assertEquals(File(temporaryFolder.root, "servers/26.04.10/language-server-all.jar"), selected.file)
        assertEquals(listOf(1, 2), requestedPages)
        assertEquals(listOf("26.04.10"), downloads)
    }

    @Test
    fun testReusesCompleteExactVersionWithoutDownloadingAgain() {
        val expected = cachedJar("26.04.2")
        val selected = cache(releases = listOf(release("26.04.2")), download = { _, _ -> fail("Already cached") })
            .resolve("26.04")
        assertEquals(expected, selected.file)
        assertEquals("26.04.2", File(temporaryFolder.root, "last-used-version.txt").readText())
    }

    @Test
    fun testOfflineFallbackPrefersHighestMatchingPatchOverLastUsedDifferentSeries() {
        cachedJar("26.04.2")
        val expected = cachedJar("26.04.10")
        cachedJar("26.10.1")
        File(temporaryFolder.root, "last-used-version.txt").writeText("26.10.1")
        assertEquals(expected, offlineCache().resolve("26.04").file)
    }

    @Test
    fun testOfflineFallbackUsesPreviouslySuccessfulVersionWhenNoSeriesMatches() {
        val expected = cachedJar("26.04.2")
        cachedJar("26.10.1")
        File(temporaryFolder.root, "last-used-version.txt").writeText("26.04.2")
        val selected = offlineCache().resolve("25.10")
        assertEquals(expected, selected.file)
        assertEquals("26.04.2", selected.version)
    }

    @Test
    fun testMissingReleaseFallsBackToLegacyJarWithoutChangingIt() {
        val jar = File(temporaryFolder.root, "language-server-all.jar")
        writeJar(jar)
        val original = jar.readBytes()
        val versionFile = File(temporaryFolder.root, "version.txt").apply { writeText("v25.10.3") }
        val selected = cache().resolve("26.04")
        assertEquals(jar, selected.file)
        assertEquals("v25.10.3", selected.version)
        assertArrayEquals(original, jar.readBytes())
        assertEquals("v25.10.3", versionFile.readText())
    }

    @Test
    fun testLegacyJarWithUnknownVersionRemainsAvailable() {
        val jar = File(temporaryFolder.root, "language-server-all.jar")
        writeJar(jar)
        val selected = offlineCache().resolve("26.04")
        assertEquals(jar, selected.file)
        assertNull(selected.version)
    }

    @Test
    fun testPluginDirectoryCacheMigratesWithoutDownloadingOrModifyingPluginFiles() {
        val oldJar = cachedJar("26.04.2")
        val original = oldJar.readBytes()
        val originalModified = oldJar.lastModified()
        val pointer = File(temporaryFolder.root, "last-used-version.txt").apply { writeText("26.04.2") }
        val newDirectory = File(temporaryFolder.root, "ide-system/nextflow-language-server")
        val resolver = NextflowLspServerCache(newDirectory,
            fetchPage = { NextflowLspReleasePage(listOf(release("26.04.2")), false) },
            download = { _, _ -> error("Existing plugin cache must be reused") },
            legacyDirectories = listOf(temporaryFolder.root))

        val selected = resolver.resolve("26.04")
        assertEquals(File(newDirectory, "servers/26.04.2/language-server-all.jar"), selected.file)
        assertArrayEquals(original, selected.file.readBytes())
        assertArrayEquals(original, oldJar.readBytes())
        assertEquals(originalModified, oldJar.lastModified())
        assertEquals("26.04.2", pointer.readText())
    }

    @Test
    fun testOfflinePluginCacheFallbackOnlyWritesMetadataToSystemDirectory() {
        val oldJar = cachedJar("26.04.2")
        val pointer = File(temporaryFolder.root, "last-used-version.txt").apply { writeText("26.10.1") }
        val originalModified = pointer.lastModified()
        val newDirectory = File(temporaryFolder.root, "ide-system/nextflow-language-server")
        val resolver = NextflowLspServerCache(newDirectory,
            fetchPage = { throw IOException("Offline") },
            download = { _, _ -> error("Offline") },
            legacyDirectories = listOf(temporaryFolder.root))

        val selected = resolver.resolve("26.04")
        assertEquals(File(newDirectory, "servers/26.04.2/language-server-all.jar"), selected.file)
        assertArrayEquals(oldJar.readBytes(), selected.file.readBytes())
        assertEquals("26.04.2", File(newDirectory, "last-used-version.txt").readText())
        assertEquals("26.10.1", pointer.readText())
        assertEquals(originalModified, pointer.lastModified())
    }

    @Test
    fun testUnavailableServerWithoutCacheReportsActionableError() {
        val failure = assertThrows(IOException::class.java) { cache().resolve("26.04") }
        assertTrue(failure.message.orEmpty().contains("26.04"))
        assertTrue(failure.message.orEmpty().contains("Custom JAR"))
    }

    @Test
    fun testInvalidVersionFailsBeforeNetworkOrCacheAccess() {
        val resolver = NextflowLspServerCache(temporaryFolder.root,
            fetchPage = { error("Must not fetch for an invalid version") },
            download = { _, _ -> error("Must not download") })
        assertThrows(IllegalArgumentException::class.java) { resolver.resolve("latest") }
    }

    @Test
    fun testInterruptedDownloadPreservesWorkingJarAndCleansTemporaryFile() {
        val working = cachedJar("26.04.2")
        val original = working.readBytes()
        val resolver = cache(listOf(release("26.04.3"))) { _, file ->
            file.writeText("partial response")
            throw IOException("Connection interrupted")
        }
        assertThrows(IOException::class.java) { resolver.install(release("26.04.3")) }
        assertEquals(working, resolver.resolve("26.04").file)
        assertArrayEquals(original, working.readBytes())
        assertFalse(File(temporaryFolder.root, "servers/26.04.3/language-server-all.jar").exists())
        assertTrue(temporaryFolder.root.walkTopDown().none { it.extension == "tmp" })
    }

    @Test
    fun testInvalidDownloadedJarIsNeverPublished() {
        val working = cachedJar("26.04.2")
        val resolver = cache(listOf(release("26.04.3"))) { _, file -> file.writeText("not a jar") }
        assertThrows(IOException::class.java) { resolver.install(release("26.04.3")) }
        assertEquals(working, resolver.resolve("26.04").file)
        assertFalse(File(temporaryFolder.root, "servers/26.04.3/language-server-all.jar").exists())
    }

    @Test
    fun testCorruptCachedJarIsReplacedAfterSuccessfulDownload() {
        val broken = File(temporaryFolder.root, "servers/26.04.2/language-server-all.jar")
        broken.parentFile.mkdirs()
        broken.writeText("broken")
        val selected = cache(listOf(release("26.04.2"))).resolve("26.04")
        assertEquals(broken, selected.file)
        assertTrue(broken.length() > 6)
    }

    @Test
    fun testCorruptCacheIsNotUsedAsFallback() {
        val broken = File(temporaryFolder.root, "language-server-all.jar")
        broken.writeText("broken")
        assertThrows(IOException::class.java) { offlineCache().resolve("26.04") }
    }

    @Test
    fun testDifferentSeriesKeepSeparateJars() {
        val resolver = cache(listOf(release("26.04.2"), release("26.10.1")))
        val first = resolver.resolve("26.04")
        val original = first.file.readBytes()
        val second = resolver.resolve("26.10")
        assertNotEquals(first.file, second.file)
        assertArrayEquals(original, first.file.readBytes())
        assertTrue(second.file.isFile)
    }

    @Test
    fun testConcurrentRequestsForSameVersionDownloadOnce() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        var downloads = 0
        val resolver = cache(listOf(release("26.04.2"))) { _, file ->
            downloads++
            entered.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
            writeJar(file)
        }
        try {
            val first = executor.submit<NextflowLspCachedServer> { resolver.resolve("26.04") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = executor.submit<NextflowLspCachedServer> { resolver.resolve("26.04") }
            finish.countDown()
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
            assertEquals(1, downloads)
        } finally {
            finish.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun testEmptyFirstPageIsRetriedBeforeConcludingThatReleaseIsUnavailable() {
        var attempts = 0
        val resolver = NextflowLspServerCache(temporaryFolder.root,
            fetchPage = {
                attempts++
                if (attempts < 3) NextflowLspReleasePage(emptyList(), false)
                else NextflowLspReleasePage(listOf(release("26.04.5")), false)
            }, download = { _, file -> writeJar(file) })
        assertEquals("26.04.5", resolver.resolve("26.04").version)
        assertEquals(3, attempts)
    }

    @Test
    fun testPersistentlyEmptyFirstPageHasBoundedRetriesAndClearCause() {
        var attempts = 0
        val resolver = NextflowLspServerCache(temporaryFolder.root,
            fetchPage = { attempts++; NextflowLspReleasePage(emptyList(), false) },
            download = { _, _ -> error("No releases") })
        val failure = assertThrows(IOException::class.java) { resolver.resolve("26.04") }
        assertEquals(3, attempts)
        assertTrue(failure.cause?.message.orEmpty().contains("empty"))
    }

    @Test
    fun testUpdateCheckDoesNotInstallUntilExplicitlyAccepted() {
        val installed = cachedJar("26.04.2")
        val resolver = cache(listOf(release("26.04.3"), release("26.10.1")))
        val update = resolver.findUpdate("26.04")!!
        assertEquals("26.04.3", update.version.toString())
        assertEquals(installed, resolver.resolve("26.04").file)
        assertFalse(File(temporaryFolder.root, "servers/26.04.3/language-server-all.jar").exists())
        resolver.install(update)
        assertEquals("26.04.3", resolver.resolve("26.04").version)
        assertTrue(installed.isFile)
    }

    @Test
    fun testUpdateCheckDoesNotOfferDowngradesOrSwitchSeries() {
        cachedJar("26.04.5")
        val resolver = cache(listOf(release("26.04.4"), release("26.10.1")))
        assertNull(resolver.findUpdate("26.04"))
    }

    @Test
    fun testStartupWithInstalledSeriesMakesNoReleaseRequests() {
        val installed = cachedJar("26.04.2")
        val resolver = NextflowLspServerCache(temporaryFolder.root,
            fetchPage = { error("Installed server should start independently of update checks") },
            download = { _, _ -> error("Installed server should not be replaced") })
        assertEquals(installed, resolver.resolve("26.04").file)
    }

    private fun release(version: String, url: String = version) = NextflowLspRelease(NextflowLspVersion.parse(version)!!, url)

    private fun cache(
        releases: List<NextflowLspRelease> = emptyList(),
        download: (String, File) -> Unit = { _, file -> writeJar(file) }
    ) = NextflowLspServerCache(temporaryFolder.root, { NextflowLspReleasePage(releases, false) }, download)

    private fun offlineCache() = NextflowLspServerCache(temporaryFolder.root,
        fetchPage = { throw IOException("Offline") }, download = { _, _ -> error("Offline") })

    private fun cachedJar(version: String): File =
        File(temporaryFolder.root, "servers/$version/language-server-all.jar").also { writeJar(it) }

    private fun writeJar(file: File) {
        file.parentFile.mkdirs()
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.MAIN_CLASS] = "nextflow.lsp.TestServer"
        }
        JarOutputStream(file.outputStream(), manifest).use { }
    }
}
