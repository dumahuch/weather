package io.github.dumahuch.pogodaapp

import android.content.Context
import android.content.pm.PackageManager

/** Версия установленного приложения: то, что видно на экране и в «О приложении». */
data class AppVersion(val name: String, val code: Long) {
    companion object {
        fun read(context: Context): AppVersion {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
            return AppVersion(name = info.versionName ?: "?", code = info.longVersionCode)
        }
    }
}
