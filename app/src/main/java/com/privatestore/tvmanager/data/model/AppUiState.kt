package com.privatestore.tvmanager.data.model

data class AppUiState(
    val packageName: String,
    val displayName: String,
    val iconUrl: String?,
    val catalogItem: AppCatalogItem?,
    val installedInfo: InstalledAppInfo?,
    val status: AppStatus
)
