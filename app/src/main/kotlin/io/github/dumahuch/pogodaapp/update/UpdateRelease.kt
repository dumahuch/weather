package io.github.dumahuch.pogodaapp.update

import io.github.dumahuch.pogodaapp.data.JsonGuard
import org.json.JSONException
import org.json.JSONObject

/** Выпуск на GitHub, который можно поставить: номер версии, адрес APK, его SHA-256 (как его считает GitHub) и размер. */
data class ReleaseOffer(val version: String, val apkUrl: String, val sha256: String, val size: Long)

/** Почему обновление не удалось. Человеку текст по причине даёт `UpdateCenter` из строк ресурсов (en/de/ru), в коде русских слов нет. */
enum class UpdateFailure {
    NoConnection, ReleaseUnreadable, NotAnApp, WrongPackage, NotNewer, InstallNotStarted, InsecureAddress, InsecureRedirect,
    HttpStatus, SizeMismatch, TooSlow, TooBig, Incomplete, ChecksumMismatch,
}

/** Ошибка проверки, скачивания или установки обновления: [reason] (и [code] ответа сервера, если он есть); сообщение для журнала. */
class UpdateException(val reason: UpdateFailure, val code: Int? = null, cause: Throwable? = null) :
    Exception(if (code == null) reason.name else "${reason.name} $code", cause)

/**
 * Последний выпуск PogodaApp на GitHub (0.8.69). Берётся только выпуск, а не черновик или предварительный, и только файл
 * `PogodaApp-<версия>.apk` с адресом в этом же репозитории и с контрольной суммой SHA-256, которую GitHub считает сам.
 */
object UpdateRelease {
    private const val REPO = "dumahuch/weather"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"

    /** Пределы размера APK: настоящий весит около 38 МБ; что-то совсем другое не скачивается. */
    private const val MIN_APK_BYTES = 1L * 1024 * 1024
    const val MAX_APK_BYTES = 200L * 1024 * 1024

    private val VERSION = Regex("""\d{1,4}\.\d{1,4}\.\d{1,4}""")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private const val DIGEST_PREFIX = "sha256:"

    /** Ответ GitHub «последний выпуск» → предложение обновления; что-то не так (нет файла, суммы, чужой адрес): null. */
    fun parse(json: String): ReleaseOffer? {
        if (JsonGuard.tooDeep(json)) return null
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return null
        }
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.optString("tag_name")
        val version = tag.removePrefix("v")
        if (!tag.startsWith("v") || !VERSION.matches(version)) return null
        val name = "PogodaApp-$version.apk"
        val assets = root.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name") != name) continue
            val url = asset.optString("browser_download_url")
            val digest = asset.optString("digest")
            val size = asset.optLong("size", -1L)
            if (url != "$DOWNLOAD_PREFIX$tag/$name") return null
            if (!digest.startsWith(DIGEST_PREFIX)) return null
            val sha = digest.removePrefix(DIGEST_PREFIX)
            if (!SHA256.matches(sha)) return null
            if (size !in MIN_APK_BYTES..MAX_APK_BYTES) return null
            return ReleaseOffer(version, url, sha, size)
        }
        return null
    }

    /** Уведомлять ли о выпуске: он новее установленного, и о нём ещё не уведомляли. */
    fun shouldNotify(offer: ReleaseOffer, installed: String, notifiedVersion: String?): Boolean =
        VersionOrder.isNewer(offer.version, installed) && offer.version != notifiedVersion
}

/** Сравнение версий вида «0.8.68» (у отладочной сборки «0.8.68-debug»: хвост после дефиса не считается). */
object VersionOrder {
    fun parts(name: String): List<Int>? {
        val core = name.substringBefore('-')
        val pieces = core.split('.')
        if (pieces.size != 3) return null
        return pieces.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    }

    /** Истина, если [candidate] новее [installed]; непонятный номер у любой стороны: ложь (ничего не предлагать). */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parts(candidate) ?: return false
        val b = parts(installed) ?: return false
        for (index in a.indices) {
            if (a[index] != b[index]) return a[index] > b[index]
        }
        return false
    }
}
