package com.privatestore.tvmanager.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Maneja el permiso "Instalar apps desconocidas" (REQUEST_INSTALL_PACKAGES).
 * Este permiso es especial: no se pide con un diálogo runtime normal, se concede
 * por app desde Ajustes > Apps > Acceso especial > Instalar apps desconocidas.
 */
object PermissionUtils {

    /**
     * true si el usuario ya autorizó a esta app a instalar paquetes desconocidos.
     * En API < 26 el permiso es a nivel de sistema completo (checkbox global en
     * Ajustes > Seguridad), así que se asume concedido si el usuario llegó a instalar
     * la app fuera de Play Store.
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Abre la pantalla de Ajustes de Android TV donde el usuario concede el permiso
     * para ESTA app en concreto (no hay diálogo in-app posible desde API 26+).
     */
    fun buildInstallPermissionSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }
}
