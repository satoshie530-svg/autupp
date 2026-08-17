package com.privatestore.tvmanager.data.model

import kotlinx.serialization.Serializable

/**
 * Representa una entrada del catálogo remoto (una de las apps distribuidas).
 * La cantidad de apps es dinámica: la define este JSON, no una lista fija
 * compilada en la app. Debe coincidir 1:1 con el JSON servido por el endpoint remoto.
 */
@Serializable
data class AppCatalogItem(
    val packageName: String,
    val appName: String,
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val iconUrl: String? = null,
    // SHA-256 en hex del .apk, opcional. Si viene, se verifica el archivo
    // descargado antes de ofrecer instalarlo (protege contra una subida a
    // medias o corrupta al servidor, no solo contra ataques de red).
    val sha256: String? = null
)

/**
 * Actualización del propio Administrador de Apps (no una de las apps gestionadas).
 * Se compara contra la versión instalada de este mismo paquete.
 */
@Serializable
data class ManagerAppInfo(
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val sha256: String? = null
)

@Serializable
data class AppCatalogResponse(
    val apps: List<AppCatalogItem>,
    val banner: String? = null,
    val managerApp: ManagerAppInfo? = null
)
