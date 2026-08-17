package com.privatestore.tvmanager.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

sealed class DownloadEvent {
    data class Progress(val percent: Int) : DownloadEvent()
    data class Done(val file: File) : DownloadEvent()
    data class Failed(val message: String) : DownloadEvent()
}

/**
 * Descarga el APK a cacheDir/apks y lanza el instalador del sistema.
 * Nunca instala en segundo plano: siempre delega en el Intent del sistema,
 * que es quien muestra el diálogo de confirmación al usuario.
 */
class InstallManager(private val context: Context) {

    private val httpClient = OkHttpClient()

    fun downloadApk(downloadUrl: String, packageName: String): Flow<DownloadEvent> = flow {
        val destination = PackageUtils.cachedApkFile(context, packageName)
        destination.parentFile?.mkdirs()
        val request = Request.Builder().url(downloadUrl).build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadEvent.Failed("El servidor respondió con un error (código ${response.code})"))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(DownloadEvent.Failed("Respuesta vacía del servidor"))
                    return@flow
                }
                val totalBytes = body.contentLength()
                var readBytes = 0L
                var lastEmittedPercent = -1

                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            readBytes += bytes
                            if (totalBytes > 0) {
                                val percent = (readBytes * 100 / totalBytes).toInt()
                                if (percent != lastEmittedPercent) {
                                    lastEmittedPercent = percent
                                    emit(DownloadEvent.Progress(percent))
                                }
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }
            }
            emit(DownloadEvent.Done(destination))
        } catch (e: IOException) {
            destination.delete()
            emit(DownloadEvent.Failed(e.toUserMessage()))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Lanza el instalador oficial del sistema para [apkFile]. Esto SIEMPRE muestra
     * el diálogo de confirmación de Android; nunca instala silenciosamente.
     * Requiere que [PermissionUtils.canRequestPackageInstalls] sea true, si no,
     * el propio sistema redirige a Ajustes en lugar de mostrar el instalador.
     */
    fun requestInstall(apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun clearCachedApk(packageName: String) {
        PackageUtils.cachedApkFile(context, packageName).delete()
    }
}
