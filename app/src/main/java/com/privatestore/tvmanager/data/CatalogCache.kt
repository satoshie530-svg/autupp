package com.privatestore.tvmanager.data

import android.content.Context
import com.privatestore.tvmanager.data.model.AppCatalogResponse
import kotlinx.serialization.json.Json

/**
 * Persiste el último catalog.json obtenido con éxito. Sin esto, un TV box sin
 * red al arrancar (típico: la app abre antes de que el WiFi termine de
 * conectar) mostraría una pantalla vacía en vez de la última lista conocida
 * de apps mientras refresca en segundo plano.
 */
class CatalogCache(context: Context) {

    private val prefs = context.getSharedPreferences("catalog_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun save(response: AppCatalogResponse) {
        prefs.edit()
            .putString(KEY_CATALOG_JSON, json.encodeToString(AppCatalogResponse.serializer(), response))
            .apply()
    }

    fun load(): AppCatalogResponse? {
        val raw = prefs.getString(KEY_CATALOG_JSON, null) ?: return null
        return runCatching { json.decodeFromString(AppCatalogResponse.serializer(), raw) }.getOrNull()
    }

    private companion object {
        const val KEY_CATALOG_JSON = "last_catalog_json"
    }
}
