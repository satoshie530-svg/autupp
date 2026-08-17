package com.privatestore.tvmanager.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // baseUrl es obligatoria para Retrofit aunque CatalogApi.getCatalog use @Url
    // absoluta; nunca se usa realmente para resolver la petición.
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://placeholder.invalid/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val catalogApi: CatalogApi = retrofit.create(CatalogApi::class.java)
}
