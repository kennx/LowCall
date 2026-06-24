package cc.niaoer.lowcall.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.niaoer.lowcall.MainActivity
import cc.niaoer.lowcall.R

object NotificationHelper {
    const val CHANNEL_ID = "call_blocking"
    private const val CHANNEL_NAME = "来电拦截"

    @RequiresApi(Build.VERSION_CODES.O)
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "拦截来电通知"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showBlockedCallNotification(
        context: Context,
        phoneNumber: String,
        location: String?,
        carrier: String?,
        ruleDescription: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(context)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val attributionPart = buildString {
            if (location != null) append(location)
            if (location != null && carrier != null) append(" · ")
            if (carrier != null) append(carrier)
        }

        val contentText = if (attributionPart.isNotEmpty()) {
            "号码: $phoneNumber · $attributionPart"
        } else {
            "号码: $phoneNumber"
        }

        val description = if (ruleDescription.isNotBlank()) {
            "规则: $ruleDescription"
        } else {
            "已拦截来电"
        }

        val bigText = "$contentText\n$description"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("拦截来电")
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context)
                    .notify(phoneNumber.hashCode(), notification)
            }
        } else {
            NotificationManagerCompat.from(context)
                .notify(phoneNumber.hashCode(), notification)
        }
    }
}
