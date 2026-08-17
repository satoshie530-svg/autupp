package com.privatestore.tvmanager.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.privatestore.tvmanager.MainActivity
import com.privatestore.tvmanager.R

/**
 * Aviso del sistema cuando una descarga en 2do plano termina y queda lista
 * para instalar. El cambio de estado en la propia tarjeta ya lo refleja, pero
 * si el usuario está mirando otra cosa en el TV no lo va a ver ahí.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "updates_ready"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Actualizaciones listas para instalar",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun notifyReadyToInstall(context: Context, packageName: String, appName: String, versionName: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$appName · actualización lista")
            .setContentText("Se descargó la v$versionName. Abrí el administrador para instalarla.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // NotificationManagerCompat.notify() no hace nada (sin excepción) si
        // POST_NOTIFICATIONS no está concedido en Android 13+: no hace falta
        // chequear el permiso a mano antes de llamar.
        NotificationManagerCompat.from(context).notify(packageName.hashCode(), notification)
    }

    fun cancel(context: Context, packageName: String) {
        NotificationManagerCompat.from(context).cancel(packageName.hashCode())
    }
}
