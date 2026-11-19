package io.github.dumahuch.pogodaapp.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class ApkDownloadTest {

    private val body = ByteArray(300_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
    private val later = System.nanoTime() + 60_000_000_000L

    private fun offer(size: Long = body.size.toLong(), hash: String = sha) = ReleaseOffer("0.8.69", "https://x", hash, size)

    @Test
    fun copiesTheWholeFileWhenSizeAndChecksumMatch() {
        val out = ByteArrayOutputStream()
        val progress = ArrayList<Int>()
        ApkDownload.copyVerified(ByteArrayInputStream(body), out, offer(), later) { progress += it }
        assertArrayEquals(body, out.toByteArray())
        assertEquals(100, progress.last())
        assertEquals(progress.sorted().distinct(), progress)
    }

    @Test
    fun otherChecksumIsRefused() {
        assertThrows(UpdateException::class.java) {
            ApkDownload.copyVerified(ByteArrayInputStream(body), ByteArrayOutputStream(), offer(hash = "0".repeat(64)), later) {}
        }
    }

    @Test
    fun shorterOrLongerFileIsRefused() {
        assertThrows(UpdateException::class.java) {
            ApkDownload.copyVerified(ByteArrayInputStream(body), ByteArrayOutputStream(), offer(size = body.size + 1L), later) {}
        }
        assertThrows(UpdateException::class.java) {
            ApkDownload.copyVerified(ByteArrayInputStream(body), ByteArrayOutputStream(), offer(size = body.size - 1L), later) {}
        }
    }

    @Test
    fun stopsAfterTheDeadline() {
        val past = System.nanoTime() - 1
        assertThrows(UpdateException::class.java) {
            ApkDownload.copyVerified(ByteArrayInputStream(body), ByteArrayOutputStream(), offer(), past) {}
        }
    }
}
