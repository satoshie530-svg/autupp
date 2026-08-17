package com.privatestore.tvmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.privatestore.tvmanager.data.model.ManagerUpdateState

@Composable
fun TvAppManagerApp(viewModel: AppManagerViewModel = viewModel()) {
    val apps by viewModel.uiState.collectAsState()
    val hasInstallPermission by viewModel.hasInstallPermission.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val catalogError by viewModel.catalogError.collectAsState()
    val banner by viewModel.banner.collectAsState()
    val managerUpdateState by viewModel.managerUpdateState.collectAsState()

    MaterialTheme(colorScheme = TvAppManagerColorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(32.dp)) {
                Text(
                    text = "Administrador de Apps",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                if (!banner.isNullOrBlank()) {
                    InfoBanner(text = banner!!)
                }

                if (managerUpdateState != ManagerUpdateState.UpToDate) {
                    ManagerUpdateBanner(
                        state = managerUpdateState,
                        onInstall = viewModel::installManagerUpdate
                    )
                }

                if (!hasInstallPermission) {
                    PermissionBanner(onGrant = viewModel::openInstallPermissionSettings)
                }

                if (catalogError != null) {
                    Text(
                        text = "No se pudo comprobar actualizaciones ($catalogError). " +
                            "Mostrando el último estado conocido.",
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Row(modifier = Modifier.padding(vertical = 16.dp)) {
                    Button(onClick = viewModel::refreshCatalog) {
                        Text(text = if (isRefreshing) "Buscando…" else "Buscar actualizaciones")
                    }
                }

                if (apps.isEmpty()) {
                    Text(
                        text = if (isRefreshing) {
                            "Cargando catálogo…"
                        } else {
                            "No hay apps configuradas todavía."
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        apps.forEach { appState ->
                            AppCard(
                                state = appState,
                                onInstall = { viewModel.install(appState.packageName) },
                                onUpdate = { viewModel.install(appState.packageName) },
                                onOpen = { viewModel.openApp(appState.packageName) },
                                onUninstall = { viewModel.uninstall(appState.packageName) },
                                onCleanInstall = { viewModel.cleanInstall(appState.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TvAppManagerColors.SurfaceVariant)
            .padding(16.dp)
    ) {
        Text(text = text, color = TvAppManagerColors.OnSurfaceVariant)
    }
}

@Composable
private fun ManagerUpdateBanner(state: ManagerUpdateState, onInstall: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TvAppManagerColors.UpdateBannerBackground)
            .padding(16.dp)
    ) {
        when (state) {
            is ManagerUpdateState.UpdateAvailable -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Actualización del administrador disponible: v${state.versionName}\n${state.changelog}",
                        color = TvAppManagerColors.OnSurface,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Button(onClick = onInstall) {
                        Text(text = "Actualizar")
                    }
                }
            }

            is ManagerUpdateState.Downloading -> {
                Text(
                    text = "Descargando actualización del administrador… ${state.progress}%",
                    color = TvAppManagerColors.OnSurface
                )
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    color = TvAppManagerColors.Primary,
                    trackColor = TvAppManagerColors.SurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            ManagerUpdateState.Installing ->
                Text(text = "Esperando confirmación de instalación…", color = TvAppManagerColors.OnSurface)

            is ManagerUpdateState.Error ->
                Text(
                    text = "No se pudo actualizar el administrador: ${state.message}",
                    color = TvAppManagerColors.OnSurface
                )

            ManagerUpdateState.UpToDate -> Unit
        }
    }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Se necesita permiso para instalar apps de origen desconocido.")
        Button(onClick = onGrant) {
            Text(text = "Conceder permiso")
        }
    }
}
