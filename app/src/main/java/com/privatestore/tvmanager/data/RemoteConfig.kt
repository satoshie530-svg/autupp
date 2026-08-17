package com.privatestore.tvmanager.data

object RemoteConfig {
    /**
     * catalog.json vive en EasyPanel (editable a mano vía filebrowser); los
     * .apk se hospedan como GitHub Releases de satoshie530-svg/autupp.
     * No resuelve hasta que el servicio "apps-cdn" esté desplegado en el VPS.
     */
    const val CATALOG_URL = "https://apps.xplayer.pro/catalog.json"
}
