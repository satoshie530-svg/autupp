package com.privatestore.tvmanager.data.model

/**
 * Estado derivado de comparar lo instalado en el dispositivo contra el catálogo remoto.
 */
sealed class AppStatus {
    data object NotInstalled : AppStatus()
    data class UpToDate(val installedVersionName: String) : AppStatus()
    data class UpdateAvailable(
        val installedVersionName: String,
        val remoteVersionName: String
    ) : AppStatus()

    /** Descarga silenciosa en 2do plano: la app sigue usable (Abrir/Desinstalar
     *  siguen disponibles), no es un estado "ocupado" como Downloading. */
    data class AutoDownloading(val progress: Int, val remoteVersionName: String) : AppStatus()

    /** El APK de la nueva versión ya está en cacheDir y verificado (su
     *  versionCode coincide con el del catálogo): instalar ya no requiere red,
     *  va directo al diálogo de confirmación del sistema. */
    data class ReadyToInstall(val remoteVersionName: String) : AppStatus()

    data object CheckingUpdate : AppStatus()

    /** Entre pulsar "Instalación limpia" y que el usuario confirme el diálogo de
     *  desinstalación del sistema (o lo cancele). */
    data object UninstallPending : AppStatus()

    // isCleanReinstall permite distinguir en la UI una descarga/instalación normal
    // de la reinstalación automática que sigue a una "instalación limpia".
    data class Downloading(val progress: Int, val isCleanReinstall: Boolean = false) : AppStatus()
    data class Installing(val isCleanReinstall: Boolean = false) : AppStatus()
    data class Error(val message: String) : AppStatus()
}

data class InstalledAppInfo(
    val versionCode: Long,
    val versionName: String
)

/** Estado de la actualización del propio Administrador de Apps (no una app gestionada). */
sealed class ManagerUpdateState {
    data object UpToDate : ManagerUpdateState()
    data class UpdateAvailable(val versionName: String, val changelog: String) : ManagerUpdateState()
    data class Downloading(val progress: Int) : ManagerUpdateState()
    data object Installing : ManagerUpdateState()
    data class Error(val message: String) : ManagerUpdateState()
}
