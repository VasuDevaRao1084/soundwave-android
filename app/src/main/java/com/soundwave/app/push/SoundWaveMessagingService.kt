package com.soundwave.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.soundwave.app.MainActivity
import com.soundwave.app.R
import com.soundwave.app.data.ErrorLog
import com.soundwave.app.data.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val FRIEND_ACTIVITY_CHANNEL_ID = "friend_activity"
private const val NOTIF_ID_FRIEND_REQUEST = 2001

/**
 * Receives friend-request/accept push notifications, sent from a Supabase
 * Edge Function (see push-notifications setup notes for the deploy steps —
 * that server-side piece has to be set up once, separately, with your own
 * Firebase + Supabase credentials).
 *
 * This class only runs at all once google-services.json is present and a
 * real FirebaseApp exists; until then FCM simply never calls it, and nothing
 * else in the app depends on it, so its absence never breaks anything else.
 */
class SoundWaveMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Upload immediately if we already know who's signed in (read from the
        // same local cache AppViewModel uses for its own cold-start restore).
        // If no one's signed in yet, MainActivity also pushes the current
        // token right after a successful sign-in, so it's never lost either way.
        val uid = getCurrentUserIdOrNull()
        if (uid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try { SupabaseClient.updateFcmToken(uid, token) } catch (_: Exception) { }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        try {
            val title = message.notification?.title ?: message.data["title"] ?: "SoundWave"
            val body = message.notification?.body ?: message.data["body"] ?: return
            showNotification(title, body)
        } catch (e: Exception) {
            ErrorLog.log(applicationContext, "PUSH", "Failed to show notification: ${e.message}")
        }
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FRIEND_ACTIVITY_CHANNEL_ID,
                "Friends",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Friend requests and friend activity" }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_friends", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, FRIEND_ACTIVITY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIF_ID_FRIEND_REQUEST, notification)
    }

    // Reads the same "soundwave_auth" cache AppViewModel restores from on
    // cold start — this service has no ViewModel of its own, so this local
    // read is the cheapest reliable way to know who's currently signed in.
    private fun getCurrentUserIdOrNull(): String? =
        applicationContext.getSharedPreferences("soundwave_auth", Context.MODE_PRIVATE)
            .getString("user_id", null)
}
