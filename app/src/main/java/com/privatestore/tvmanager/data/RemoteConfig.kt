package com.privatestore.tvmanager.data

object RemoteConfig {
    /**
     * TEMPORAL: servidor de pruebas local en esta LAN, mientras se termina de
     * montar el hosting real (GitHub Releases + EasyPanel). Reemplazar por la
     * URL HTTPS definitiva antes de distribuir la app fuera de esta prueba.
     */
    const val CATALOG_URL = "http://192.168.100.4:8000/catalog.json"
}
