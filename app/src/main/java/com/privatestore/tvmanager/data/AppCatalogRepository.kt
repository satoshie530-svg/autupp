package com.privatestore.tvmanager.data

import android.content.Context
import com.privatestore.tvmanager.data.model.AppCatalogResponse
import com.privatestore.tvmanager.data.model.AppStatus
import com.privatestore.tvmanager.data.model.AppUiState
import com.privatestore.tvmanager.data.remote.ApiClient
import com.privatestore.tvmanager.data.remote.CatalogApi
import com.privatestore.tvmanager.util.PackageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppCatalogRepository(
    private val context: Context,
    private val catalogApi: CatalogApi = ApiClient.catalogApi,
    private val cache: CatalogCache = CatalogCache(context)
) {

    /** Último catálogo guardado en disco, si existe. Para arrancar la UI antes de tocar la red. */
    fun loadCachedCatalog(): AppCatalogResponse? = cache.load()

    suspend fun fetchCatalog(): Result<AppCatalogResponse> = withContext(Dispatchers.IO) {
        runCatching { catalogApi.getCatalog(RemoteConfig.CATALOG_URL) }
            .onSuccess { cache.save(it) }
    }

    /**
     * El catálogo es la única fuente de verdad de qué apps existen: no hay una
     * lista fija compilada en la app. Si una app deja de estar en el catálogo,
     * deja de mostrarse (aunque siga instalada en el TV) — es intencional: es
     * la forma de "dar de baja" una app sin tocar código.
     */
    fun buildLocalStates(catalog: AppCatalogResponse?): List<AppUiState> {
        val items = catalog?.apps.orEmpty()
        return items.map { catalogItem ->
            val installedInfo = PackageUtils.getInstalledInfo(context, catalogItem.packageName)

            val status = when {
                installedInfo == null -> AppStatus.NotInstalled
                catalogItem.versionCode > installedInfo.versionCode ->
                    AppStatus.UpdateAvailable(installedInfo.versionName, catalogItem.versionName)
                else -> AppStatus.UpToDate(installedInfo.versionName)
            }

            AppUiState(
                packageName = catalogItem.packageName,
                displayName = catalogItem.appName,
                iconUrl = catalogItem.iconUrl,
                catalogItem = catalogItem,
                installedInfo = installedInfo,
                status = status
            )
        }
    }
}
