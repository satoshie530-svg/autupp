package com.privatestore.tvmanager.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.privatestore.tvmanager.data.model.InstalledAppInfo
import java.io.File

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
        return InstalledAppInfo(
            versionCode = extractVersionCode(info),
            versionName = info.versionName.orEmpty()
        )
    }

    /** Ruta fija donde InstallManager guarda el APK descargado de [packageName]. */
    fun cachedApkFile(context: Context, packageName: String): File =
        File(File(context.cacheDir, "apks"), "$packageName.apk")

    /**
     * Lee el versionCode de un APK ya descargado en disco, sin instalarlo.
     * Permite confirmar que un archivo cacheado sigue siendo la versión que el
     * catálogo pide, en vez de asumirlo ciegamente (podría ser de una
     * descarga vieja, o estar corrupto/truncado).
     */
    fun getApkFileVersionCode(context: Context, apkFile: File): Long? {
        if (!apkFile.exists()) return null
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.path,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkFile.path, 0)
        }
        return info?.let { extractVersionCode(it) }
    }

    private fun extractVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
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
