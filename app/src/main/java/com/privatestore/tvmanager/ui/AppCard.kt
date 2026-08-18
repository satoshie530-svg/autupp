package com.privatestore.tvmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.privatestore.tvmanager.data.model.AppStatus
import com.privatestore.tvmanager.data.model.AppUiState
import kotlin.math.abs

/** Ancho fijo de tarjeta pensado para que entren 4 por fila en un TV 1080p
 *  (960dp lógicos de ancho) sin necesitar scroll horizontal. */
val AppCardWidth = 205.dp

@Composable
fun AppCard(
    state: AppUiState,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onCleanInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = state.status
    // Mientras hay una operación en curso que sí requiere esperar al usuario o al
    // instalador (no la descarga silenciosa en 2do plano, esa no bloquea nada) se
    // ocultan las acciones: evita dobles taps y que "Desinstalar" aparezca en medio
    // de una instalación limpia ya en marcha, cuando installedInfo aún es el previo.
    val isBusy = status is AppStatus.UninstallPending ||
        status is AppStatus.Downloading ||
        status is AppStatus.Installing ||
        status is AppStatus.CheckingUpdate

    // Igual que antes: Column + focusGroup() sin onClick propio, para que el D-pad
    // navegue directo a los botones internos en vez de que la tarjeta entera
    // compita por el foco (ver historial: eso dejaba "Desinstalar" inalcanzable).
    // onFocusChanged detecta cuando el foco entra a CUALQUIER hijo (hasFocus
    // también es true para el contenedor cuando un descendiente lo tiene) para
    // dibujar el borde de foco alrededor de toda la tarjeta, no de un botón suelto.
    var hasFocusWithin by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(AppCardWidth)
            .clip(RoundedCornerShape(16.dp))
            .background(TvAppManagerColors.Surface)
            .border(
                width = if (hasFocusWithin) 2.dp else 1.dp,
                color = if (hasFocusWithin) TvAppManagerColors.Primary else TvAppManagerColors.CardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .focusGroup()
            .onFocusChanged { hasFocusWithin = it.hasFocus }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(
                iconUrl = state.iconUrl,
                displayName = state.displayName,
                packageName = state.packageName,
                modifier = Modifier.size(38.dp)
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = state.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TvAppManagerColors.OnSurface,
                    maxLines = 1
                )
                StatusLabel(status)
            }
        }

        when (status) {
            is AppStatus.Downloading -> LinearProgressIndicator(
                progress = { status.progress / 100f },
                color = TvAppManagerColors.Primary,
                trackColor = TvAppManagerColors.SurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            // Descarga silenciosa de una actualización: mismo estilo de barra,
            // pero no cuenta como "ocupado" (isBusy no la incluye).
            is AppStatus.AutoDownloading -> LinearProgressIndicator(
                progress = { status.progress / 100f },
                color = TvAppManagerColors.Primary,
                trackColor = TvAppManagerColors.SurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            // Sin porcentaje disponible (esperando al usuario o al instalador
            // del sistema): barra indeterminada en vez de dejar la tarjeta muda.
            is AppStatus.UninstallPending, is AppStatus.Installing -> LinearProgressIndicator(
                color = TvAppManagerColors.Primary,
                trackColor = TvAppManagerColors.SurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            else -> Unit
        }

        if (!isBusy) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                when (status) {
                    is AppStatus.NotInstalled -> PrimaryActionButton("Instalar", Icons.Filled.PlayArrow, onInstall, useDownloadGlyph = true)
                    is AppStatus.UpdateAvailable -> PrimaryActionButton("Actualizar", Icons.Filled.PlayArrow, onUpdate, useDownloadGlyph = true)
                    // Corto a propósito ("Instalar" alcanza, el detalle ya está en
                    // StatusLabel arriba).
                    is AppStatus.ReadyToInstall -> PrimaryActionButton("Instalar", Icons.Filled.PlayArrow, onUpdate, useDownloadGlyph = true)
                    is AppStatus.UpToDate -> PrimaryActionButton("Abrir", Icons.Filled.PlayArrow, onOpen)
                    is AppStatus.Error -> PrimaryActionButton("Reintentar", Icons.Filled.Refresh, onInstall)
                    else -> Unit
                }

                if (state.installedInfo != null) {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        SecondaryActionButton("Desinstalar", Icons.Filled.Delete, onUninstall)
                        // Siempre disponible mientras esté instalada, no solo tras un
                        // error: es la forma de forzar un reinicio limpio ante
                        // cualquier problema.
                        Box(modifier = Modifier.padding(top = 6.dp)) {
                            CleanInstallRow(onCleanInstall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(iconUrl: String?, displayName: String, packageName: String, modifier: Modifier = Modifier) {
    val accent = TvAppManagerColors.IconAccents[abs(packageName.hashCode()) % TvAppManagerColors.IconAccents.size]
    // La letra de respaldo queda dibujada siempre, debajo; si iconUrl carga bien,
    // AsyncImage la tapa por completo. Si falla o sigue cargando, AsyncImage no
    // dibuja nada (comportamiento por defecto de Coil sin placeholder/error), así
    // que la letra sigue visible. Evita depender de un drawable de fallback propio.
    Box(modifier = modifier.clip(RoundedCornerShape(11.dp))) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(text = displayName.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        if (iconUrl != null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    useDownloadGlyph: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(34.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.colors(
            containerColor = TvAppManagerColors.Primary,
            contentColor = TvAppManagerColors.OnPrimary,
            focusedContainerColor = TvAppManagerColors.Primary,
            focusedContentColor = TvAppManagerColors.OnPrimary
        )
    ) {
        if (useDownloadGlyph) {
            DownloadGlyph(tint = TvAppManagerColors.OnPrimary, modifier = Modifier.size(13.dp))
        } else {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun SecondaryActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(34.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.colors(
            containerColor = TvAppManagerColors.SurfaceVariant,
            contentColor = TvAppManagerColors.OnSurface,
            focusedContainerColor = TvAppManagerColors.OnSurfaceVariant,
            focusedContentColor = Color.White
        )
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun CleanInstallRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvAppManagerColors.SurfaceVariant,
            contentColor = TvAppManagerColors.OnSurface,
            focusedContainerColor = TvAppManagerColors.Primary,
            focusedContentColor = TvAppManagerColors.OnPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(
                text = "Instalación limpia",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            ChevronRightGlyph(tint = TvAppManagerColors.OnSurface, modifier = Modifier.size(14.dp))
        }
    }
}

private data class StatusLabelInfo(val label: String, val versionPrefix: String?, val dotColor: Color)

@Composable
private fun StatusLabel(status: AppStatus) {
    val info = when (status) {
        is AppStatus.NotInstalled -> StatusLabelInfo("No instalada", null, TvAppManagerColors.OnSurfaceVariant)
        is AppStatus.UpToDate -> StatusLabelInfo("Instalada", "v${status.installedVersionName}", TvAppManagerColors.Success)
        is AppStatus.UpdateAvailable -> StatusLabelInfo(
            "Actualización disponible",
            "v${status.installedVersionName} → v${status.remoteVersionName}",
            Color(0xFFF59E0B)
        )
        is AppStatus.AutoDownloading -> StatusLabelInfo(
            "Descargando ${status.progress}%",
            "v${status.remoteVersionName}",
            Color(0xFFF59E0B)
        )
        is AppStatus.ReadyToInstall -> StatusLabelInfo("Lista para instalar", "v${status.remoteVersionName}", Color(0xFFF59E0B))
        is AppStatus.CheckingUpdate -> StatusLabelInfo("Buscando actualizaciones…", null, TvAppManagerColors.OnSurfaceVariant)
        is AppStatus.UninstallPending -> StatusLabelInfo("Esperando confirmación…", null, TvAppManagerColors.OnSurfaceVariant)
        is AppStatus.Downloading -> StatusLabelInfo(
            if (status.isCleanReinstall) "Reinstalando ${status.progress}%" else "Descargando ${status.progress}%",
            null,
            TvAppManagerColors.Primary
        )
        is AppStatus.Installing -> StatusLabelInfo(
            if (status.isCleanReinstall) "Confirma la reinstalación" else "Esperando confirmación",
            null,
            TvAppManagerColors.Primary
        )
        is AppStatus.Error -> StatusLabelInfo("Error: ${status.message}", null, TvAppManagerColors.Error)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        if (info.versionPrefix != null) {
            Text(text = info.versionPrefix, color = TvAppManagerColors.OnSurfaceVariant, fontSize = 11.sp)
            Box(modifier = Modifier.padding(horizontal = 5.dp).size(5.dp).clip(CircleShape).background(info.dotColor))
        } else {
            Box(modifier = Modifier.padding(end = 5.dp).size(5.dp).clip(CircleShape).background(info.dotColor))
        }
        Text(text = info.label, color = TvAppManagerColors.OnSurfaceVariant, fontSize = 11.sp, maxLines = 1)
    }
}
