package com.privatestore.tvmanager.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Desinstalación vía Intent del sistema: igual que la instalación, siempre
 * muestra el diálogo de confirmación nativo de Android. No existe una API
 * pública para desinstalar sin interacción del usuario fuera de contextos
 * de device-owner/MDM, que están fuera del alcance de esta app.
 *
 * Requiere el permiso REQUEST_DELETE_PACKAGES en el manifest: sin él,
 * UninstallerActivity del sistema rechaza el intent sin avisar (no lanza
 * excepción hacia este código, así que un try/catch acá no lo detectaría).
 */
class UninstallManager(private val context: Context) {

    fun requestUninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
