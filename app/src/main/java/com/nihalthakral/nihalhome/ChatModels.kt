package com.nihalthakral.nihalhome

object ChatRole {
    const val USER = "user"
    const val MODEL = "model"
}

data class ChatMessage(
    val id: String,
    val role: String,
    var text: String,
    val imagePaths: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    var isStreaming: Boolean = false
)
