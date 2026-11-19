package io.github.dumahuch.pogodaapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleaseTest {

    private val sha = "a15f69b8968be5f4c2a9225279e763bc4ad76d9e94b67db907b66d0adc64ff69"

    private fun release(
        tag: String = "v0.8.69",
        name: String = "PogodaApp-0.8.69.apk",
        url: String = "https://github.com/dumahuch/weather/releases/download/v0.8.69/PogodaApp-0.8.69.apk",
        digest: String? = "sha256:$sha",
        size: Long = 37_740_652,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): String {
        val digestField = digest?.let { "\"digest\": \"$it\"," } ?: ""
        return """
            {"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease, "assets": [
              {"name": "notes.txt", "browser_download_url": "https://example.com/x", "size": 10},
              {"name": "$name", $digestField "browser_download_url": "$url", "size": $size}
            ]}
        """.trimIndent()
    }

    @Test
    fun readsTheApkOfTheLatestRelease() {
        val offer = UpdateRelease.parse(release())
        assertEquals(
            ReleaseOffer("0.8.69", "https://github.com/dumahuch/weather/releases/download/v0.8.69/PogodaApp-0.8.69.apk", sha, 37_740_652),
            offer,
        )
    }

    @Test
    fun skipsDraftsPrereleasesAndOddTags() {
        assertNull(UpdateRelease.parse(release(draft = true)))
        assertNull(UpdateRelease.parse(release(prerelease = true)))
        assertNull(UpdateRelease.parse(release(tag = "0.8.69")))
        assertNull(UpdateRelease.parse(release(tag = "v0.8")))
    }

    @Test
    fun refusesForeignAddressesMissingChecksumAndOddSizes() {
        assertNull(UpdateRelease.parse(release(url = "https://evil.example/PogodaApp-0.8.69.apk")))
        assertNull(UpdateRelease.parse(release(url = "http://github.com/dumahuch/weather/releases/download/v0.8.69/PogodaApp-0.8.69.apk")))
        assertNull(UpdateRelease.parse(release(digest = null)))
        assertNull(UpdateRelease.parse(release(digest = "sha1:abc")))
        assertNull(UpdateRelease.parse(release(digest = "sha256:XYZ")))
        assertNull(UpdateRelease.parse(release(size = 1000)))
        assertNull(UpdateRelease.parse(release(size = 500L * 1024 * 1024)))
    }

    @Test
    fun needsTheApkNamedAfterTheVersion() {
        assertNull(UpdateRelease.parse(release(name = "PogodaApp-0.8.68.apk")))
    }

    @Test
    fun brokenOrTooDeepAnswersGiveNothing() {
        assertNull(UpdateRelease.parse("не JSON"))
        assertNull(UpdateRelease.parse("[".repeat(100) + "]".repeat(100)))
    }

    @Test
    fun comparesVersionsByNumbers() {
        assertTrue(VersionOrder.isNewer("0.8.69", "0.8.68"))
        assertTrue(VersionOrder.isNewer("0.8.100", "0.8.99"))
        assertTrue(VersionOrder.isNewer("0.9.0", "0.8.99"))
        assertTrue(VersionOrder.isNewer("1.0.0", "0.99.99"))
        assertFalse(VersionOrder.isNewer("0.8.68", "0.8.68"))
        assertFalse(VersionOrder.isNewer("0.8.67", "0.8.68"))
        // отладочная сборка «-debug» сравнивается по номеру
        assertTrue(VersionOrder.isNewer("0.8.69", "0.8.68-debug"))
        assertFalse(VersionOrder.isNewer("0.8.68", "0.8.68-debug"))
        // непонятный номер: ничего не предлагать
        assertFalse(VersionOrder.isNewer("0.8.69", "?"))
        assertFalse(VersionOrder.isNewer("abc", "0.8.68"))
    }

    @Test
    fun notifiesOncePerNewVersion() {
        val offer = ReleaseOffer("0.8.69", "u", sha, 1)
        assertTrue(UpdateRelease.shouldNotify(offer, "0.8.68", null))
        assertTrue(UpdateRelease.shouldNotify(offer, "0.8.68", "0.8.68"))
        assertFalse(UpdateRelease.shouldNotify(offer, "0.8.68", "0.8.69"))
        assertFalse(UpdateRelease.shouldNotify(offer, "0.8.69", null))
    }
}
