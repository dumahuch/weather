package io.github.dumahuch.pogodaapp.update

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Скачивание APK выпуска в файл с проверкой по дороге: размер ровно как у выпуска, SHA-256 совпадает с суммой GitHub,
 * не дольше [TOTAL_TIMEOUT_MS]. Не совпало: файл стирается, [UpdateException]. Подпись приложения потом сверяет Android.
 */
internal object ApkDownload {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val TOTAL_TIMEOUT_MS = 10 * 60_000L
    private const val CHUNK_BYTES = 64 * 1024
    private const val NANOS_PER_MILLI = 1_000_000L

    fun download(offer: ReleaseOffer, target: File, userAgent: String, onProgress: (Int) -> Unit) {
        val address = URI(offer.apkUrl)
        if (address.scheme != "https") throw UpdateException(UpdateFailure.InsecureAddress)
        val deadline = System.nanoTime() + TOTAL_TIMEOUT_MS * NANOS_PER_MILLI
        val connection = address.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("User-Agent", userAgent)
            val status = connection.responseCode
            // GitHub отдаёт файл после перенаправления на свой сервер файлов: и там только https.
            if (connection.url.protocol != "https") throw UpdateException(UpdateFailure.InsecureRedirect)
            if (status !in 200..299) throw UpdateException(UpdateFailure.HttpStatus, code = status)
            val length = connection.contentLengthLong
            if (length >= 0 && length != offer.size) throw UpdateException(UpdateFailure.SizeMismatch)
            target.parentFile?.mkdirs()
            try {
                connection.inputStream.use { input ->
                    target.outputStream().use { output -> copyVerified(input, output, offer, deadline, onProgress) }
                }
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        } catch (e: IOException) {
            throw UpdateException(UpdateFailure.NoConnection, cause = e)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Копирует [input] в [output], считая SHA-256 и байты; больше размера выпуска, позже [deadlineNanos], меньше в конце или
     * другая сумма: [UpdateException]. [onProgress] получает проценты, только когда они меняются.
     */
    fun copyVerified(input: InputStream, output: OutputStream, offer: ReleaseOffer, deadlineNanos: Long, onProgress: (Int) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        val chunk = ByteArray(CHUNK_BYTES)
        var total = 0L
        var percent = -1
        while (true) {
            if (System.nanoTime() - deadlineNanos > 0) throw UpdateException(UpdateFailure.TooSlow)
            val count = input.read(chunk)
            if (count < 0) break
            total += count
            if (total > offer.size) throw UpdateException(UpdateFailure.TooBig)
            digest.update(chunk, 0, count)
            output.write(chunk, 0, count)
            val now = (total * 100 / offer.size).toInt()
            if (now != percent) {
                percent = now
                onProgress(now)
            }
        }
        if (total != offer.size) throw UpdateException(UpdateFailure.Incomplete)
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (hex != offer.sha256) throw UpdateException(UpdateFailure.ChecksumMismatch)
    }
}
