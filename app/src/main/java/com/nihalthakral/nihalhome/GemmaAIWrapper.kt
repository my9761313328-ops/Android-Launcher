package com.nihalthakral.nihalhome

import com.google.genai.kotlin.Client
import com.google.genai.kotlin.types.Content
import com.google.genai.kotlin.types.GenerateContentConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GemmaAIWrapper {

    private val client by lazy { Client(apiKey = API_KEY) }

    fun streamReply(systemInstruction: String, contents: List<Content>): Flow<String> {
        val config = GenerateContentConfig(
            systemInstruction = Content.fromText(systemInstruction)
        )

        return client.models.generateContentStream(
            model = MODEL_ID,
            contents = contents,
            config = config
        ).map { response -> response.text ?: "" }
    }

    companion object {
        private const val API_KEY = "AQ.Ab8RN6JbciEcV9xUFVUiJpNZiOZxBypWkeAD_l4GZZfTlGIktQ"
        private const val MODEL_ID = "gemma-4-31b-it"
    }
}
