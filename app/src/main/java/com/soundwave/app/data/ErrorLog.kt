package com.soundwave.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small rolling log of errors/crashes, kept on-device so problems are
 * visible from inside the app itself. There's no Android Studio/Logcat
 * available for this project, so this is the debugging tool: every caught
 * exception and every crash gets appended here (capped to the most recent
 * 30 entries), viewable from the Diagnostics screen and copyable to the
 * clipboard to paste elsewhere for help.
 */
object ErrorLog {
    private const val PREFS = "soundwave_errorlog"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 30

    data class Entry(val time: String, val tag: String, val message: String)

    fun log(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existing = JSONArray(prefs.getString(KEY, "[]"))
            val entries = ArrayList<JSONObject>()
            for (i in 0 until existing.length()) entries.add(existing.getJSONObject(i))

            val fullMessage = if (throwable != null) "$message: ${throwable}" else message
            val entry = JSONObject().apply {
                put("time", SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date()))
                put("tag", tag)
                put("message", fullMessage)
            }
            entries.add(entry)
            // Keep only the most recent MAX_ENTRIES
            val trimmed = if (entries.size > MAX_ENTRIES) entries.subList(entries.size - MAX_ENTRIES, entries.size) else entries

            val newArray = JSONArray()
            trimmed.forEach { newArray.put(it) }
            prefs.edit().putString(KEY, newArray.toString()).apply()
        } catch (e: Exception) {
            // Logging must never itself crash the app
        }
    }

    fun getAll(context: Context): List<Entry> {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("time"), o.getString("tag"), o.getString("message"))
            }.reversed() // newest first
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun asPlainText(context: Context): String {
        return getAll(context).joinToString("\n\n") { "[${it.time}] ${it.tag}\n${it.message}" }
    }
}
