package com.nihalthakral.nihalhome

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ChatHistoryStore {

    private const val IMAGES_DIR_NAME = "ask_expert_chat_images"

    fun loadMessages(context: Context): MutableList<ChatMessage> {
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(PreferenceKeys.KEY_ASK_EXPERT_CHAT_HISTORY, null) ?: return mutableListOf()

        val result = mutableListOf<ChatMessage>()
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val imagePaths = mutableListOf<String>()
            val imagesArray = obj.optJSONArray("imagePaths")
            if (imagesArray != null) {
                for (j in 0 until imagesArray.length()) {
                    imagePaths.add(imagesArray.getString(j))
                }
            }
            result.add(
                ChatMessage(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    role = obj.optString("role", ChatRole.USER),
                    text = obj.optString("text", ""),
                    imagePaths = imagePaths,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
        return result
    }

    fun saveMessages(context: Context, messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            val obj = JSONObject()
            obj.put("id", message.id)
            obj.put("role", message.role)
            obj.put("text", message.text)
            obj.put("timestamp", message.timestamp)
            val imagesArray = JSONArray()
            message.imagePaths.forEach { imagesArray.put(it) }
            obj.put("imagePaths", imagesArray)
            array.put(obj)
        }

        context.getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PreferenceKeys.KEY_ASK_EXPERT_CHAT_HISTORY, array.toString())
            .apply()
    }

    fun imagesDirectory(context: Context): File {
        val dir = File(context.filesDir, IMAGES_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PreferenceKeys.KEY_ASK_EXPERT_CHAT_HISTORY)
            .apply()

        val dir = imagesDirectory(context)
        dir.listFiles()?.forEach { it.delete() }
    }
}
