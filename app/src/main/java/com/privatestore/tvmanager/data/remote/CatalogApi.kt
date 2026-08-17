package com.privatestore.tvmanager.data.remote

import com.privatestore.tvmanager.data.model.AppCatalogResponse
import retrofit2.http.GET
import retrofit2.http.Url

interface CatalogApi {
    /**
     * @Url absoluta en lugar de baseUrl fija: así RemoteConfig.CATALOG_URL puede
     * apuntar a cualquier host (servidor propio, Firebase, S3, etc.) sin recompilar
     * la configuración de Retrofit.
     */
    @GET
    suspend fun getCatalog(@Url url: String): AppCatalogResponse
}
