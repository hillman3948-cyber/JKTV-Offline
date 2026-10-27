package com.jktv.offline.assets

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetInstallerTest {

    @Test
    fun testDiffDetectionWithNewAndDeletedAssets() {
        val tempDir = Files.createTempDirectory("jktv-test-installer").toFile()
        try {
            val installer = AssetInstaller(tempDir)

            val pkg1 = AssetPackage(
                id = "songs-part-001",
                fileName = "songs-part-001.zip",
                url = "songs-part-001.zip",
                sizeBytes = 1000L,
                sha256 = "aaa111",
                category = AssetCategory.SONG_PACKAGE,
                isCritical = true
            )
            val pkg2 = AssetPackage(
                id = "default-gm",
                fileName = "default-gm-v1.0.0.sf2",
                url = "default-gm-v1.0.0.sf2",
                sizeBytes = 500L,
                sha256 = "bbb222",
                category = AssetCategory.SOUNDFONT,
                isCritical = true
            )
            val pkgVideoNew = AssetPackage(
                id = "bgv-moon",
                fileName = "Moon Over The City.mp4",
                url = "bgv/Moon Over The City.mp4",
                sizeBytes = 2000L,
                sha256 = "ccc333",
                category = AssetCategory.BACKGROUND_VIDEO,
                isCritical = false
            )

            // Local manifest only has songs and soundfont
            val localManifest = LibraryManifest(
                libraryVersion = "1.0.0",
                packages = listOf(pkg1, pkg2),
                deletedFiles = emptyList()
            )

            // Remote manifest has new video and lists bgv/Drunk.mp4 as deleted
            val remoteManifest = LibraryManifest(
                libraryVersion = "1.2.0",
                packages = listOf(pkg1, pkg2, pkgVideoNew),
                deletedFiles = listOf("bgv/Drunk.mp4")
            )

            // Create fake local files for pkg1 and pkg2 so they are recognized as locally present
            val libraryDir = File(tempDir, "karaoke/library").apply { mkdirs() }
            val songsDir = File(libraryDir, "songs").apply { mkdirs() }
            File(songsDir, "sample.mid").writeText("dummy midi")
            val sf2Dir = File(libraryDir, "soundfonts").apply { mkdirs() }
            val sf2File = File(sf2Dir, "default-gm-v1.0.0.sf2")
            sf2File.writeBytes(ByteArray(500))

            val diff = installer.calculateDiff(remote = remoteManifest, local = localManifest)

            assertTrue(diff.hasUpdates)
            assertEquals(1, diff.pendingPackages.size)
            assertEquals("bgv-moon", diff.pendingPackages[0].id)
            assertEquals(1, diff.newVideosCount)
            assertEquals(0, diff.newSongsCount)
            assertEquals(0, diff.newSoundfontsCount)
            assertEquals(listOf("bgv/Drunk.mp4"), diff.deletedFiles)
            assertEquals(2000L, diff.totalDownloadBytes)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testUpToDateLibraryHasNoPendingDiff() {
        val tempDir = Files.createTempDirectory("jktv-test-installer-uptodate").toFile()
        try {
            val installer = AssetInstaller(tempDir)

            val pkgVideo = AssetPackage(
                id = "bgv-moon",
                fileName = "Moon Over The City.mp4",
                url = "bgv/Moon Over The City.mp4",
                sizeBytes = 2000L,
                sha256 = "ccc333",
                category = AssetCategory.BACKGROUND_VIDEO,
                isCritical = false
            )

            val manifest = LibraryManifest(
                libraryVersion = "1.2.0",
                packages = listOf(pkgVideo),
                deletedFiles = emptyList()
            )

            // Create matching local video file
            val libraryDir = File(tempDir, "karaoke/library").apply { mkdirs() }
            val bgDir = File(libraryDir, "backgrounds").apply { mkdirs() }
            val videoFile = File(bgDir, "Moon Over The City.mp4")
            videoFile.writeBytes(ByteArray(2000))

            val diff = installer.calculateDiff(remote = manifest, local = manifest)

            assertFalse(diff.hasUpdates)
            assertTrue(diff.pendingPackages.isEmpty())
            assertEquals(0L, diff.totalDownloadBytes)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testManifestJsonParsing() {
        val json = """
            {
                "formatVersion": 1,
                "libraryVersion": "1.2.0",
                "soundfonts": [
                    {
                        "id": "default-gm",
                        "filename": "default-gm-v1.0.0.sf2",
                        "url": "default-gm-v1.0.0.sf2",
                        "sizeBytes": 98645536,
                        "sha256": "8862381c105be29202f99f2b3441fead25cfd94b0d33c5e0ecfdc58d8a2cd391"
                    }
                ],
                "songPackages": [
                    {
                        "id": "songs-part-001",
                        "filename": "songs-part-001.zip",
                        "url": "songs-part-001.zip",
                        "sizeBytes": 123456,
                        "sha256": "abcdef123456",
                        "songCount": 100
                    }
                ],
                "backgroundVideos": [
                    {
                        "id": "bgv-moon",
                        "filename": "Moon Over The City.mp4",
                        "url": "bgv/Moon Over The City.mp4",
                        "sizeBytes": 253932386,
                        "sha256": "12cc57e4de5b1ffdc4a5bc2462c582e9ff42d2337d83c4b185b563631af7235a"
                    }
                ],
                "deletedFiles": [
                    "bgv/Drunk.mp4",
                    "bgv/motion1.mp4"
                ]
            }
        """.trimIndent()

        val tempDir = Files.createTempDirectory("jktv-test-manifest-parse").toFile()
        try {
            val installer = AssetInstaller(tempDir)
            val manifest = installer.parseManifest(json)

            assertEquals("1.2.0", manifest.libraryVersion)
            assertEquals(3, manifest.packages.size)
            assertEquals(2, manifest.deletedFiles.size)
            assertTrue(manifest.deletedFiles.contains("bgv/Drunk.mp4"))

            val videoPkg = manifest.packages.first { it.id == "bgv-moon" }
            assertEquals(AssetCategory.BACKGROUND_VIDEO, videoPkg.category)
            assertFalse(videoPkg.isCritical)

            val songPkg = manifest.packages.first { it.id == "songs-part-001" }
            assertEquals(AssetCategory.SONG_PACKAGE, songPkg.category)
            assertTrue(songPkg.isCritical)

            val sfPkg = manifest.packages.first { it.id == "default-gm" }
            assertEquals(AssetCategory.SOUNDFONT, sfPkg.category)
            assertTrue(sfPkg.isCritical)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
