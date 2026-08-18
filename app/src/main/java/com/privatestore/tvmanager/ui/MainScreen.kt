package com.privatestore.tvmanager.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.IconButton
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.privatestore.tvmanager.data.model.FeaturedItem
import com.privatestore.tvmanager.data.model.ManagerUpdateState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TvAppManagerApp(viewModel: AppManagerViewModel = viewModel()) {
    val apps by viewModel.uiState.collectAsState()
    val hasInstallPermission by viewModel.hasInstallPermission.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val catalogError by viewModel.catalogError.collectAsState()
    val banner by viewModel.banner.collectAsState()
    val whatsappNumber by viewModel.whatsappNumber.collectAsState()
    val featured by viewModel.featured.collectAsState()
    val managerUpdateState by viewModel.managerUpdateState.collectAsState()

    MaterialTheme(colorScheme = TvAppManagerColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvAppManagerColors.BackgroundGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 6.dp)
            ) {
                AppHeader(isRefreshing = isRefreshing, onRefresh = viewModel::refreshCatalog)
                Spacer(Modifier.height(6.dp))

                if (!banner.isNullOrBlank() || !whatsappNumber.isNullOrBlank()) {
                    WelcomeBanner(message = banner, whatsappNumber = whatsappNumber)
                    Spacer(Modifier.height(6.dp))
                }

                if (managerUpdateState != ManagerUpdateState.UpToDate) {
                    ManagerUpdateBanner(state = managerUpdateState, onInstall = viewModel::installManagerUpdate)
                    Spacer(Modifier.height(10.dp))
                }

                if (!hasInstallPermission) {
                    PermissionBanner(onGrant = viewModel::openInstallPermissionSettings)
                    Spacer(Modifier.height(10.dp))
                }

                if (catalogError != null) {
                    Text(
                        text = "No se pudo comprobar actualizaciones ($catalogError). " +
                            "Mostrando el último estado conocido.",
                        color = TvAppManagerColors.OnSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                SectionHeader(title = "Apps instaladas", subtitle = "Gestiona tus aplicaciones en un solo lugar.")
                Spacer(Modifier.height(8.dp))

                if (apps.isEmpty()) {
                    Text(
                        text = if (isRefreshing) {
                            "Cargando catálogo…"
                        } else {
                            "No hay apps configuradas todavía."
                        },
                        color = TvAppManagerColors.OnSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
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

                if (featured.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader(
                        title = "Información destacada para vos",
                        subtitle = "No te pierdas lo mejor del deporte y el entretenimiento."
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        featured.forEach { item -> FeaturedCard(item) }
                    }
                }

                Spacer(Modifier.height(4.dp))
                VersionFooter()
            }
        }
    }
}

@Composable
private fun AppHeader(isRefreshing: Boolean, onRefresh: () -> Unit) {
    var now by remember { mutableStateOf(currentTimeLabel()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = currentTimeLabel()
            delay(30_000)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AppLogo(modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(text = "TV DIGITAL UPDATES", style = MaterialTheme.typography.titleMedium, color = TvAppManagerColors.OnSurface)
            Text(
                text = "Administrador de Apps",
                fontSize = 12.sp,
                color = TvAppManagerColors.OnSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = now,
            style = MaterialTheme.typography.titleMedium,
            color = TvAppManagerColors.OnSurface,
            modifier = Modifier.padding(end = 12.dp)
        )
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(36.dp),
            colors = ButtonDefaults.colors(
                containerColor = TvAppManagerColors.SurfaceVariant,
                contentColor = TvAppManagerColors.OnSurface,
                focusedContainerColor = TvAppManagerColors.Primary,
                focusedContentColor = TvAppManagerColors.OnPrimary
            )
        ) {
            if (isRefreshing) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = TvAppManagerColors.Primary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Buscar actualizaciones",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Logo circular (insignia + triángulo de play) dibujado a mano, sin depender de un asset externo. */
@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    val accent = TvAppManagerColors.Primary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = minOf(w, h) / 2f
        drawCircle(color = accent, radius = radius, center = center)

        val triHalf = radius * 0.42f
        val cx = center.x + radius * 0.06f
        val cy = center.y
        val path = Path().apply {
            moveTo(cx - triHalf * 0.85f, cy - triHalf)
            lineTo(cx - triHalf * 0.85f, cy + triHalf)
            lineTo(cx + triHalf * 0.95f, cy)
            close()
        }
        drawPath(path, color = Color.White)
    }
}

private fun currentTimeLabel(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = TvAppManagerColors.OnSurface)
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = TvAppManagerColors.OnSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@Composable
private fun WelcomeBanner(message: String?, whatsappNumber: String?) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TvAppManagerColors.Surface)
            .border(1.dp, TvAppManagerColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(TvAppManagerColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "👋", fontSize = 16.sp)
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text = "¡Bienvenido!", style = MaterialTheme.typography.titleSmall, color = TvAppManagerColors.OnSurface)
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = TvAppManagerColors.OnSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        if (!whatsappNumber.isNullOrBlank()) {
            Surface(
                onClick = { openWhatsapp(context, whatsappNumber) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = TvAppManagerColors.OnSurface,
                    focusedContainerColor = TvAppManagerColors.Whatsapp.copy(alpha = 0.14f),
                    focusedContentColor = TvAppManagerColors.OnSurface
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(TvAppManagerColors.Whatsapp.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        ChatGlyph(tint = TvAppManagerColors.Whatsapp, modifier = Modifier.size(14.dp))
                    }
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = whatsappNumber, style = MaterialTheme.typography.labelLarge, color = TvAppManagerColors.OnSurface)
                        Text(
                            text = "Escribinos por WhatsApp",
                            fontSize = 11.sp,
                            color = TvAppManagerColors.OnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun openWhatsapp(context: android.content.Context, number: String) {
    val digits = number.filter { it.isDigit() }
    if (digits.isEmpty()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun ManagerUpdateBanner(state: ManagerUpdateState, onInstall: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TvAppManagerColors.UpdateBannerBackground)
            .padding(12.dp)
    ) {
        when (state) {
            is ManagerUpdateState.UpdateAvailable -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Actualización del administrador disponible: v${state.versionName}\n${state.changelog}",
                        color = TvAppManagerColors.OnSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 12.dp).weight(1f)
                    )
                    Button(onClick = onInstall) {
                        Text(text = "Actualizar")
                    }
                }
            }

            is ManagerUpdateState.Downloading -> {
                Text(
                    text = "Descargando actualización del administrador… ${state.progress}%",
                    color = TvAppManagerColors.OnSurface,
                    fontSize = 12.sp
                )
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    color = TvAppManagerColors.Primary,
                    trackColor = TvAppManagerColors.SurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }

            ManagerUpdateState.Installing ->
                Text(text = "Esperando confirmación de instalación…", color = TvAppManagerColors.OnSurface, fontSize = 12.sp)

            is ManagerUpdateState.Error ->
                Text(
                    text = "No se pudo actualizar el administrador: ${state.message}",
                    color = TvAppManagerColors.OnSurface,
                    fontSize = 12.sp
                )

            ManagerUpdateState.UpToDate -> Unit
        }
    }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TvAppManagerColors.Surface)
            .border(1.dp, TvAppManagerColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Se necesita permiso para instalar apps de origen desconocido.",
            color = TvAppManagerColors.OnSurface,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 12.dp).weight(1f)
        )
        Button(onClick = onGrant) {
            Text(text = "Conceder permiso")
        }
    }
}

@Composable
private fun FeaturedCard(item: FeaturedItem) {
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TvAppManagerColors.SurfaceVariant)
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        if (!item.dateLabel.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item.dateLabel.split("\n").forEach { line ->
                    Text(text = line, fontSize = 9.sp, color = TvAppManagerColors.OnSurface)
                }
            }
        }

        if (!item.tag.isNullOrBlank()) {
            Text(
                text = item.tag,
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 2
            )
            if (!item.timeLabel.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    ClockGlyph(tint = Color.White, modifier = Modifier.size(11.dp))
                    Text(
                        text = item.timeLabel,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionFooter() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
    if (!versionName.isNullOrBlank()) {
        Text(
            text = "Versión $versionName",
            fontSize = 11.sp,
            color = TvAppManagerColors.OnSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
