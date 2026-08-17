package com.privatestore.tvmanager.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.privatestore.tvmanager.data.model.InstalledAppInfo

/**
 * Utilidades de consulta de paquetes instalados. No requiere QUERY_ALL_PACKAGES:
 * como cada packageName se consulta explícitamente, basta con la visibilidad
 * declarada en <queries> del manifest (Android 11+).
 */
object PackageUtils {

    /**
     * Devuelve versionCode/versionName si [packageName] está instalado, o null si no.
     */
    fun getInstalledInfo(context: Context, packageName: String): InstalledAppInfo? {
        val info = getPackageInfoOrNull(context, packageName) ?: return null
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return InstalledAppInfo(
            versionCode = versionCode,
            versionName = info.versionName.orEmpty()
        )
    }

    fun isInstalled(context: Context, packageName: String): Boolean =
        getPackageInfoOrNull(context, packageName) != null

    private fun getPackageInfoOrNull(context: Context, packageName: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** Intent para abrir la app si ya está instalada; null si no tiene launcher. */
    fun getLaunchIntent(context: Context, packageName: String) =
        context.packageManager.getLaunchIntentForPackage(packageName)
}
