package io.github.dumahuch.pogodaapp.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import java.io.File

/**
 * Передаёт скачанный APK системному установщику (PackageInstaller). Сначала проверяется, что в файле это же приложение и
 * версия новее установленной; подпись сверяет сам Android (чужая подпись: отказ). Приложение обновляет само себя, поэтому
 * просит установку без окна подтверждения (USER_ACTION_NOT_REQUIRED, Android 12+); если система всё же попросит
 * подтверждение, окно откроет [UpdateInstallReceiver].
 */
class UpdateInstaller(private val context: Context) {

    /** Разрешил ли человек этому приложению ставить приложения («Установка неизвестных приложений»). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Открывает в настройках телефона разрешение «Установка неизвестных приложений» для этого приложения. */
    fun openPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(apk: File, installedCode: Long) {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(0))
            ?: throw UpdateException("Скачанный файл не приложение")
        if (archive.packageName != context.packageName) throw UpdateException("В файле другое приложение")
        if (archive.longVersionCode <= installedCode) throw UpdateException("В файле не новее установленной версии")
        val installer = manager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite(SESSION_FILE, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                session.commit(statusSender(id))
            }
        } catch (e: Exception) {
            installer.abandonSession(id)
            throw if (e is UpdateException) e else UpdateException("Установка не началась: ${e.message}", e)
        }
    }

    /** Куда система сообщит итог: только в [UpdateInstallReceiver] этого приложения (явный адрес, изменяемый: система кладёт итог). */
    private fun statusSender(sessionId: Int) = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    ).intentSender

    private companion object {
        const val SESSION_FILE = "base.apk"
    }
}

/** Итог установки от системы: окно подтверждения (если система его требует), успех или ошибка в строку настроек. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val center = UpdateCenter.get(context)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Окно подтверждения системного установщика; приложение в этот момент на экране (установку начал человек).
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    center.onInstallFailed(null)
                    return
                }
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: RuntimeException) {
                    Log.w(TAG, "окно подтверждения не открылось", e)
                    center.onInstallFailed(e.message)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> center.onInstalled()
            else -> {
                Log.w(TAG, "установка не удалась: $status")
                center.onInstallFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), aborted = status == PackageInstaller.STATUS_FAILURE_ABORTED)
            }
        }
    }

    private companion object {
        const val TAG = "PogodaUpdate"
    }
}

/**
 * Приложение заменено новой версией (любым способом: отсюда, Obtainium, вручную). Ставит заново проверку обновлений,
 * стирает скачанный файл и, если обновление начали в настройках приложения, присылает «PogodaApp обновлён до X»:
 * после установки система закрывает приложение, и без этого похоже на падение.
 */
class UpdateReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val center = UpdateCenter.get(context)
        try {
            center.afterReplaced()
            UpdateJobs.sync(context, center.state.value.auto)
        } catch (e: RuntimeException) {
            Log.w("PogodaUpdate", "после обновления", e)
        }
    }
}
