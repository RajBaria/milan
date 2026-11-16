package com.example.data.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlin.random.Random

object NotificationHelper {
    const val CHANNEL_SOCIAL_ID = "milan_social_notifications"
    const val CHANNEL_REWARDS_ID = "milan_rewards_notifications"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val socialChannel = NotificationChannel(
                CHANNEL_SOCIAL_ID,
                "Milan Likes & Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new Likes and Chat Messages on Milan"
            }

            val rewardsChannel = NotificationChannel(
                CHANNEL_REWARDS_ID,
                "Milan Premium & Streak Rewards",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for 6-Day Streak Free Premium and UPI Subscription updates"
            }

            manager.createNotificationChannel(socialChannel)
            manager.createNotificationChannel(rewardsChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun sendSocialNotification(
        context: Context,
        title: String,
        body: String
    ) {
        sendNotificationInternal(context, CHANNEL_SOCIAL_ID, title, body)
    }

    fun sendRewardNotification(
        context: Context,
        title: String,
        body: String
    ) {
        sendNotificationInternal(context, CHANNEL_REWARDS_ID, title, body)
    }

    private fun sendNotificationInternal(
        context: Context,
        channelId: String,
        title: String,
        body: String
    ) {
        try {
            if (!hasNotificationPermission(context)) return
            createNotificationChannels(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(Random.nextInt(1000, 99999), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        } catch (e: Exception) {
            // Safe ignore in unit test environments
        }
    }
}
