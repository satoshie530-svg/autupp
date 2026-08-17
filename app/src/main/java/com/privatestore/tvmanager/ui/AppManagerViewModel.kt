package com.privatestore.tvmanager.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.privatestore.tvmanager.data.AppCatalogRepository
import com.privatestore.tvmanager.data.model.AppCatalogResponse
import com.privatestore.tvmanager.data.model.AppStatus
import com.privatestore.tvmanager.data.model.AppUiState
import com.privatestore.tvmanager.data.model.ManagerUpdateState
import com.privatestore.tvmanager.util.DownloadEvent
import com.privatestore.tvmanager.util.InstallManager
import com.privatestore.tvmanager.util.PackageUtils
import com.privatestore.tvmanager.util.PermissionUtils
import com.privatestore.tvmanager.util.UninstallManager
import com.privatestore.tvmanager.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppCatalogRepository(application)
    private val installManager = InstallManager(application)
    private val uninstallManager = UninstallManager(application)

    // Arranca con lo último guardado en disco (si hay) en vez de vacío, para no
    // mostrar una pantalla en blanco mientras se resuelve el primer fetch de red.
    private var lastCatalog: AppCatalogResponse? = repository.loadCachedCatalog()

    private val _uiState = MutableStateFlow(repository.buildLocalStates(lastCatalog))
    val uiState: StateFlow<List<AppUiState>> = _uiState.asStateFlow()

    private val _banner = MutableStateFlow(lastCatalog?.banner)
    val banner: StateFlow<String?> = _banner.asStateFlow()

    private val _managerUpdateState = MutableStateFlow<ManagerUpdateState>(ManagerUpdateState.UpToDate)
    val managerUpdateState: StateFlow<ManagerUpdateState> = _managerUpdateState.asStateFlow()

    private val _hasInstallPermission =
        MutableStateFlow(PermissionUtils.canRequestPackageInstalls(application))
    val hasInstallPermission: StateFlow<Boolean> = _hasInstallPermission.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _catalogError = MutableStateFlow<String?>(null)
    val catalogError: StateFlow<String?> = _catalogError.asStateFlow()

    // Paquete pendiente de reinstalar una vez el sistema confirme que se desinstaló
    // (flujo de "instalación limpia").
    private var pendingCleanInstallPackage: String? = null

    init {
        refreshCatalog()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.fetchCatalog()
            result.onSuccess {
                lastCatalog = it
                _catalogError.value = null
                updateManagerUpdateState(it)
            }
            // Si falla, se conserva el último catálogo conocido (lastCatalog) en lugar
            // de descartarlo: así una app instalada no pasa a "sin datos" solo porque
            // el servidor esté caído en este momento.
            result.onFailure {
                _catalogError.value = it.toUserMessage()
            }
            refreshLocalStates()
            _isRefreshing.value = false
        }
    }

    /** Llamar desde Activity.onResume(): el usuario puede volver de Ajustes o del
     *  instalador/desinstalador del sistema, así que hay que releer el estado real. */
    fun onActivityResumed() {
        _hasInstallPermission.value =
            PermissionUtils.canRequestPackageInstalls(getApplication())
        refreshLocalStates()

        val pending = pendingCleanInstallPackage
        if (pending != null) {
            // Un solo intento: este resume es el que corresponde al diálogo de
            // desinstalación que acabamos de lanzar, se haya confirmado o cancelado.
            // Si no se limpia aquí incondicionalmente, un usuario que cancela la
            // desinstalación deja el flag colgado, y una desinstalación *no
            // relacionada* de esa misma app en cualquier momento futuro dispararía
            // una reinstalación automática que nadie pidió.
            pendingCleanInstallPackage = null
            if (!PackageUtils.isInstalled(getApplication(), pending)) {
                install(pending, isCleanReinstall = true)
            }
            // Si sigue instalada, el usuario canceló la desinstalación: no hacemos nada más.
        }
    }

    fun install(packageName: String, isCleanReinstall: Boolean = false) {
        val catalogItem = lastCatalog?.apps?.firstOrNull { it.packageName == packageName } ?: return

        // Sin este permiso el instalador del sistema solo mostrará su propia pantalla
        // de "fuente no permitida"; mejor evitar gastar datos descargando el APK y
        // guiar directo a Ajustes, como pide el flujo de permisos del proyecto.
        if (!_hasInstallPermission.value) {
            openInstallPermissionSettings()
            return
        }

        // Se fija en 0% de forma síncrona (antes de tocar la red) para que la tarjeta
        // pase directo a la barra de progreso sin pasar por "No instalada" un instante,
        // que es justo lo que pasaría si esto se deja solo dentro de la corrutina.
        updateStatus(packageName, AppStatus.Downloading(progress = 0, isCleanReinstall = isCleanReinstall))

        viewModelScope.launch {
            installManager.downloadApk(catalogItem.downloadUrl, packageName).collect { event ->
                when (event) {
                    is DownloadEvent.Progress ->
                        updateStatus(packageName, AppStatus.Downloading(event.percent, isCleanReinstall))

                    is DownloadEvent.Done -> {
                        updateStatus(packageName, AppStatus.Installing(isCleanReinstall))
                        // Este intent SIEMPRE muestra el diálogo de confirmación del
                        // sistema; la app nunca instala en segundo plano.
                        installManager.requestInstall(event.file)
                    }

                    is DownloadEvent.Failed ->
                        updateStatus(packageName, AppStatus.Error(event.message))
                }
            }
        }
    }

    fun uninstall(packageName: String) {
        uninstallManager.requestUninstall(packageName)
    }

    /** Instalación limpia: desinstala primero, y al detectar (en onActivityResumed)
     *  que ya no está instalada, dispara automáticamente la descarga + instalación. */
    fun cleanInstall(packageName: String) {
        pendingCleanInstallPackage = packageName
        updateStatus(packageName, AppStatus.UninstallPending)
        uninstallManager.requestUninstall(packageName)
    }

    /**
     * Actualiza el propio Administrador de Apps. Mismo mecanismo que install():
     * descarga + Intent.ACTION_VIEW con el diálogo oficial del sistema. Android
     * mata y reinicia este proceso solo una vez confirmada la instalación, así
     * que no hace falta manejar un "después" explícito acá.
     */
    fun installManagerUpdate() {
        val managerApp = lastCatalog?.managerApp ?: return

        if (!_hasInstallPermission.value) {
            openInstallPermissionSettings()
            return
        }

        _managerUpdateState.value = ManagerUpdateState.Downloading(0)

        viewModelScope.launch {
            val selfPackageName = getApplication<Application>().packageName
            installManager.downloadApk(managerApp.downloadUrl, selfPackageName).collect { event ->
                when (event) {
                    is DownloadEvent.Progress ->
                        _managerUpdateState.value = ManagerUpdateState.Downloading(event.percent)

                    is DownloadEvent.Done -> {
                        _managerUpdateState.value = ManagerUpdateState.Installing
                        installManager.requestInstall(event.file)
                    }

                    is DownloadEvent.Failed ->
                        _managerUpdateState.value = ManagerUpdateState.Error(event.message)
                }
            }
        }
    }

    fun openApp(packageName: String) {
        val intent = PackageUtils.getLaunchIntent(getApplication(), packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun openInstallPermissionSettings() {
        val intent = PermissionUtils.buildInstallPermissionSettingsIntent(getApplication())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    private fun refreshLocalStates() {
        _uiState.value = repository.buildLocalStates(lastCatalog)
        _banner.value = lastCatalog?.banner
    }

    private fun updateManagerUpdateState(response: AppCatalogResponse) {
        val managerApp = response.managerApp
        if (managerApp == null) {
            _managerUpdateState.value = ManagerUpdateState.UpToDate
            return
        }
        val selfPackageName = getApplication<Application>().packageName
        val installed = PackageUtils.getInstalledInfo(getApplication(), selfPackageName)
        _managerUpdateState.value = if (installed != null && managerApp.versionCode > installed.versionCode) {
            ManagerUpdateState.UpdateAvailable(managerApp.versionName, managerApp.changelog)
        } else {
            ManagerUpdateState.UpToDate
        }
    }

    private fun updateStatus(packageName: String, status: AppStatus) {
        _uiState.value = _uiState.value.map {
            if (it.packageName == packageName) it.copy(status = status) else it
        }
    }
}
