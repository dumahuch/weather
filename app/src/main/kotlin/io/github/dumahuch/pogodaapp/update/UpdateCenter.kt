package io.github.dumahuch.pogodaapp.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.dumahuch.pogodaapp.AppVersion
import io.github.dumahuch.pogodaapp.MainActivity
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.data.HttpJson
import io.github.dumahuch.pogodaapp.settings.PreferencesSettingsStorage
import io.github.dumahuch.pogodaapp.weather.ForecastException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

/** Что сейчас с обновлением: для строки в настройках. */
enum class UpdateStatus { Idle, Checking, UpToDate, Available, NeedsPermission, Downloading, Installing, Failed }

/**
 * Состояние раздела «Обновления»: установленная версия, выключатель автопроверки, найденный новый выпуск (только если он
 * новее установленного), проценты скачивания, время последней проверки и текст ошибки.
 */
data class UpdateUi(
    val installed: String,
    val auto: Boolean = true,
    val status: UpdateStatus = UpdateStatus.Idle,
    val offer: ReleaseOffer? = null,
    val progress: Int = 0,
    val checkedAt: Instant? = null,
    val error: String? = null,
) {
    /** Идёт работа: кнопки ждут. */
    val busy: Boolean
        get() = status == UpdateStatus.Checking || status == UpdateStatus.Downloading || status == UpdateStatus.Installing
}

/**
 * Обновление приложения из самого приложения (0.8.69): проверка последнего выпуска на GitHub (раз в сутки фоном и
 * кнопкой), скачивание APK с проверкой суммы и передача системе. Один на процесс: состояние видят и настройки, и
 * приёмник итога установки.
 */
class UpdateCenter private constructor(private val context: Context) {
    private val store = UpdateStore(PreferencesSettingsStorage(context))
    private val version = AppVersion.read(context)
    private val userAgent = "PogodaApp/${version.name}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val mutableState = MutableStateFlow(initial())
    val state: StateFlow<UpdateUi> = mutableState.asStateFlow()

    private fun initial(): UpdateUi {
        val offer = store.offer?.takeIf { VersionOrder.isNewer(it.version, version.name) }
        return UpdateUi(
            installed = version.name,
            auto = store.auto,
            status = if (offer != null) UpdateStatus.Available else UpdateStatus.Idle,
            offer = offer,
            checkedAt = store.checkedAt,
        )
    }

    fun setAuto(on: Boolean) {
        store.auto = on
        mutableState.update { it.copy(auto = on) }
        UpdateJobs.sync(context, on)
    }

    /** Кнопка «Проверить сейчас». */
    fun checkNow() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(status = UpdateStatus.Checking, error = null) }
        scope.launch {
            try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Фоновая проверка раз в сутки: при новой версии уведомление, один раз на версию и только если уведомления разрешены. */
    fun checkInBackground() {
        val offer = fetch() ?: return
        if (!UpdateRelease.shouldNotify(offer, version.name, store.notified)) return
        if (!manager.areNotificationsEnabled()) return
        notifyAvailable(offer)
        store.notified = offer.version
    }

    /** Кнопка «Установить X»: без разрешения на установку сначала открываются настройки телефона. */
    fun install(installer: UpdateInstaller) {
        val current = mutableState.value
        val offer = current.offer ?: return
        if (current.busy) return
        if (!installer.canInstall()) {
            mutableState.update { it.copy(status = UpdateStatus.NeedsPermission, error = null) }
            installer.openPermissionSettings()
            return
        }
        mutableState.update { it.copy(status = UpdateStatus.Downloading, progress = 0, error = null) }
        scope.launch {
            val file = File(downloadDir(), "PogodaApp-${offer.version}.apk")
            try {
                downloadDir().listFiles()?.forEach { it.delete() }
                ApkDownload.download(offer, file, userAgent) { percent -> mutableState.update { it.copy(progress = percent) } }
                mutableState.update { it.copy(status = UpdateStatus.Installing) }
                store.started = offer.version
                installer.install(file, version.code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                file.delete()
                store.started = null
                fail(e)
            }
        }
    }

    /** Окно настроек снова на экране: если человек выдал разрешение на установку, кнопка снова «Установить». */
    fun onResumed(installer: UpdateInstaller) {
        if (mutableState.value.status == UpdateStatus.NeedsPermission && installer.canInstall()) {
            mutableState.update { it.copy(status = UpdateStatus.Available) }
        }
    }

    /** Система поставила новую версию: этот процесс она сейчас закроет. */
    fun onInstalled() {
        Log.i(TAG, "обновление установлено")
    }

    /** Установка не удалась или человек отменил её в окне системы ([aborted]): кнопка снова «Установить». */
    fun onInstallFailed(message: String?, aborted: Boolean = false) {
        store.started = null
        downloadDir().listFiles()?.forEach { it.delete() }
        mutableState.update {
            if (aborted) {
                it.copy(status = UpdateStatus.Available, error = null)
            } else {
                it.copy(status = UpdateStatus.Failed, error = message ?: context.getString(R.string.settings_update_install_failed))
            }
        }
    }

    /** Приложение заменено новой версией: файл стирается, о пришедшем отсюда обновлении приходит уведомление. */
    fun afterReplaced() {
        downloadDir().listFiles()?.forEach { it.delete() }
        val started = store.started
        store.started = null
        if (started != null && started == version.name && manager.areNotificationsEnabled()) notifyUpdated(version.name)
    }

    private fun fetch(): ReleaseOffer? {
        val json = try {
            HttpJson.get(UpdateRelease.LATEST_URL, userAgent, source = "GitHub")
        } catch (e: ForecastException) {
            throw UpdateException(UpdateFailure.NoConnection, cause = e)
        }
        val offer = UpdateRelease.parse(json) ?: throw UpdateException(UpdateFailure.ReleaseUnreadable)
        val now = Instant.now()
        store.offer = offer
        store.checkedAt = now
        val newer = offer.takeIf { VersionOrder.isNewer(it.version, version.name) }
        mutableState.update {
            it.copy(
                status = if (newer != null) UpdateStatus.Available else UpdateStatus.UpToDate,
                offer = newer,
                checkedAt = now,
                error = null,
            )
        }
        return newer
    }

    /** Текст ошибки для настроек на языке приложения; подробности остаются в журнале. */
    private fun describe(e: Exception): String {
        val reason = (e as? UpdateException)?.reason ?: return context.getString(R.string.update_error_unknown)
        return when (reason) {
            UpdateFailure.HttpStatus -> context.getString(R.string.update_error_http_status, (e as UpdateException).code ?: 0)
            else -> context.getString(reasonText(reason))
        }
    }

    private fun reasonText(reason: UpdateFailure): Int = when (reason) {
        UpdateFailure.NoConnection -> R.string.update_error_no_connection
        UpdateFailure.ReleaseUnreadable -> R.string.update_error_release_unreadable
        UpdateFailure.NotAnApp -> R.string.update_error_not_an_app
        UpdateFailure.WrongPackage -> R.string.update_error_wrong_package
        UpdateFailure.NotNewer -> R.string.update_error_not_newer
        UpdateFailure.InstallNotStarted -> R.string.update_error_install_not_started
        UpdateFailure.InsecureAddress -> R.string.update_error_insecure_address
        UpdateFailure.InsecureRedirect -> R.string.update_error_insecure_redirect
        UpdateFailure.HttpStatus -> R.string.update_error_unknown
        UpdateFailure.SizeMismatch -> R.string.update_error_size_mismatch
        UpdateFailure.TooSlow -> R.string.update_error_too_slow
        UpdateFailure.TooBig -> R.string.update_error_too_big
        UpdateFailure.Incomplete -> R.string.update_error_incomplete
        UpdateFailure.ChecksumMismatch -> R.string.update_error_checksum
    }

    private fun fail(e: Exception) {
        Log.w(TAG, "обновление", e)
        mutableState.update {
            it.copy(status = UpdateStatus.Failed, error = describe(e))
        }
    }

    private fun downloadDir(): File = File(context.cacheDir, DOWNLOAD_DIR)

    private fun notifyAvailable(offer: ReleaseOffer) {
        createChannel()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notify_update_title, offer.version))
            .setContentText(context.getString(R.string.notify_update_text))
            .setContentIntent(open(openUpdates = true))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun notifyUpdated(name: String) {
        createChannel()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notify_updated_title, name))
            .setContentText(context.getString(R.string.notify_updated_text))
            .setContentIntent(open(openUpdates = false))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** Канал создаётся при каждом показе: его название следует за языком. Тихий: без звука и вибрации. */
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_update),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_update_hint)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun open(openUpdates: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (openUpdates) intent.putExtra(EXTRA_OPEN_UPDATES, true)
        return PendingIntent.getActivity(
            context,
            if (openUpdates) REQUEST_OPEN_UPDATES else REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        /** В намерении из уведомления «Доступна версия»: открыть настройки на разделе «Обновления». */
        const val EXTRA_OPEN_UPDATES = "io.github.dumahuch.pogodaapp.extra.OPEN_UPDATES"
        private const val TAG = "PogodaUpdate"
        private const val CHANNEL_ID = "app_update"
        private const val NOTIFICATION_ID = 7501
        private const val REQUEST_OPEN_UPDATES = 500
        private const val REQUEST_OPEN_APP = 501
        private const val DOWNLOAD_DIR = "update"

        @Volatile
        private var instance: UpdateCenter? = null

        fun get(context: Context): UpdateCenter = instance ?: synchronized(this) {
            instance ?: UpdateCenter(context.applicationContext).also { instance = it }
        }
    }
}
