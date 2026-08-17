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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.privatestore.tvmanager.data.model.AppStatus
import com.privatestore.tvmanager.data.model.AppUiState

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

    // Antes esto era un androidx.tv.material3.Card con su propio onClick: al ser
    // ÉL MISMO un objetivo de foco/click independiente de los botones que
    // contiene adentro, el D-pad nunca entraba a los botones internos (todo el
    // centro del control disparaba la acción de la card) — "Desinstalar" quedaba
    // inalcanzable. focusGroup() en un contenedor no-clickeable deja que cada
    // botón sea su propio destino de navegación, como corresponde en TV.
    // El borde es fijo (no depende del foco) para que las tarjetas se lean como
    // piezas separadas incluso cuando ninguna tiene el foco todavía.
    Column(
        modifier = modifier
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TvAppManagerColors.Surface)
            .border(1.dp, TvAppManagerColors.CardBorder, RoundedCornerShape(12.dp))
            .focusGroup()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(iconUrl = state.iconUrl, displayName = state.displayName, modifier = Modifier.size(48.dp))
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        StatusLabel(status)

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (status) {
                    is AppStatus.NotInstalled -> ActionButton("Instalar", onInstall)
                    is AppStatus.UpdateAvailable -> ActionButton("Actualizar", onUpdate)
                    // Corto a propósito ("Instalar" alcanza, el detalle ya está en
                    // StatusLabel arriba): con "Instalar actualización" completo,
                    // "Desinstalar" no entraba en la fila y se cortaba a "Desinst".
                    is AppStatus.ReadyToInstall -> ActionButton("Instalar", onUpdate)
                    is AppStatus.UpToDate -> ActionButton("Abrir", onOpen)
                    is AppStatus.Error -> ActionButton("Reintentar", onInstall)
                    else -> Unit
                }

                if (state.installedInfo != null) {
                    ActionButton("Desinstalar", onUninstall)
                }
            }

            // Siempre disponible mientras esté instalada, no solo tras un error:
            // es la forma de forzar un reinicio limpio ante cualquier problema.
            if (state.installedInfo != null) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    ActionButton("Instalación limpia", onCleanInstall)
                }
            }
        }
    }
}

@Composable
private fun AppIcon(iconUrl: String?, displayName: String, modifier: Modifier = Modifier) {
    // La letra de respaldo queda dibujada siempre, debajo; si iconUrl carga bien,
    // AsyncImage la tapa por completo. Si falla o sigue cargando, AsyncImage no
    // dibuja nada (comportamiento por defecto de Coil sin placeholder/error), así
    // que la letra sigue visible. Evita depender de un drawable de fallback propio.
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp))) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(TvAppManagerColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(text = displayName.take(1).uppercase(), color = TvAppManagerColors.Primary)
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
private fun ActionButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.height(40.dp)) {
        Text(text = text)
    }
}

@Composable
private fun StatusLabel(status: AppStatus) {
    val text = when (status) {
        is AppStatus.NotInstalled -> "No instalada"
        is AppStatus.UpToDate -> "Instalada · v${status.installedVersionName}"
        is AppStatus.UpdateAvailable ->
            "Actualización disponible: v${status.installedVersionName} → v${status.remoteVersionName}"
        is AppStatus.AutoDownloading ->
            "Descargando v${status.remoteVersionName} en 2do plano… ${status.progress}%"
        is AppStatus.ReadyToInstall ->
            "Actualización v${status.remoteVersionName} lista para instalar"
        is AppStatus.CheckingUpdate -> "Buscando actualizaciones…"
        is AppStatus.UninstallPending -> "Esperando confirmación de desinstalación…"
        is AppStatus.Downloading ->
            if (status.isCleanReinstall) {
                "Reinstalando (instalación limpia)… ${status.progress}%"
            } else {
                "Descargando… ${status.progress}%"
            }
        is AppStatus.Installing ->
            if (status.isCleanReinstall) {
                "Descarga lista, confirma la reinstalación limpia"
            } else {
                "Esperando confirmación de instalación"
            }
        is AppStatus.Error -> "Error: ${status.message}"
    }
    Text(text = text, modifier = Modifier.padding(top = 4.dp))
}
