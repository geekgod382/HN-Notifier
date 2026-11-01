package com.example.hnnotifier.data

import android.content.Context
import android.content.SharedPreferences

class HNPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hn_notifier_prefs", Context.MODE_PRIVATE)

    var keywords: String
        get() = prefs.getString(KEY_KEYWORDS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_KEYWORDS, value).apply()

    var lastSeenStoryId: Long
        get() = prefs.getLong(KEY_LAST_SEEN_ID, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SEEN_ID, value).apply()

    fun matchesFilter(title: String): Boolean {
        val currentKeywords = keywords.trim()
        if (currentKeywords.isEmpty()) {
            return true // No filter set, notify for all new stories
        }
        val keywordList = currentKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (keywordList.isEmpty()) {
            return true
        }
        val lowerTitle = title.lowercase()
        return keywordList.any { kw -> lowerTitle.contains(kw) }
    }

    companion object {
        private const val KEY_KEYWORDS = "key_keywords"
        private const val KEY_LAST_SEEN_ID = "key_last_seen_id"
    }
}
