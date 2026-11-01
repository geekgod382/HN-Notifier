package com.example.hnnotifier.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.example.hnnotifier.data.HNPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

class HNNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            val prefs = HNPreferences(applicationContext)
            val currentLastSeen = prefs.lastSeenStoryId

            val jsonText = URL("https://hacker-news.firebaseio.com/v0/newstories.json").readText()
            val jsonArray = JSONArray(jsonText)

            if (jsonArray.length() == 0) {
                return@withContext ListenableWorker.Result.success()
            }

            val latestId = jsonArray.getLong(0)

            if (currentLastSeen == 0L) {
                // First time running: initialize lastSeenStoryId to latest ID without spamming old posts
                prefs.lastSeenStoryId = latestId
                return@withContext ListenableWorker.Result.success()
            }

            if (latestId <= currentLastSeen) {
                // No new stories
                return@withContext ListenableWorker.Result.success()
            }

            // Collect all story IDs newer than currentLastSeen
            val newStoryIds = mutableListOf<Long>()
            for (i in 0 until jsonArray.length()) {
                val id = jsonArray.getLong(i)
                if (id > currentLastSeen) {
                    newStoryIds.add(id)
                } else {
                    break
                }
            }

            // Process oldest new stories first so notifications appear in chronological order
            val sortedNewIds = newStoryIds.reversed()
            var maxProcessedId = currentLastSeen

            createNotificationChannel()

            for (storyId in sortedNewIds) {
                try {
                    val storyJsonText = URL("https://hacker-news.firebaseio.com/v0/item/$storyId.json").readText()
                    val storyObj = JSONObject(storyJsonText)
                    val type = storyObj.optString("type", "")
                    if (type == "story") {
                        val title = storyObj.optString("title", "")
                        val postUrl = storyObj.optString("url", "").ifEmpty { "https://news.ycombinator.com/item?id=$storyId" }
                        if (title.isNotEmpty() && prefs.matchesFilter(title)) {
                            showNotification(storyId.toInt(), title, postUrl)
                        }
                    }
                    if (storyId > maxProcessedId) {
                        maxProcessedId = storyId
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            prefs.lastSeenStoryId = maxProcessedId
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            ListenableWorker.Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Hacker News Posts"
            val descriptionText = "Notifications for new Hacker News posts matching filters"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(notificationId: Int, title: String, urlString: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("New HN Post")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    companion object {
        const val CHANNEL_ID = "hn_posts_channel"
        const val WORK_NAME = "HNNotificationPeriodicWork"
    }
}
