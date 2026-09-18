package com.nihalthakral.nihalhome

import com.google.genai.kotlin.Client
import com.google.genai.kotlin.types.Content
import com.google.genai.kotlin.types.GenerateContentConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class GemmaAIWrapper {

    private val client by lazy { Client(apiKey = API_KEY) }

    fun streamReply(systemInstruction: String, contents: List<Content>): Flow<String> {
        val config = GenerateContentConfig(
            systemInstruction = Content.fromText(systemInstruction)
        )

        return flow {
            client.models.generateContentStream(
                model = MODEL_ID,
                contents = contents,
                config = config
            ).map { response -> response.text ?: "" }
                .collect { chunk -> emit(chunk) }
        }.flowOn(Dispatchers.IO)
    }

    companion object {
        private const val API_KEY = "AQ.Ab8RN6JbciEcV9xUFVUiJpNZiOZxBypWkeAD_l4GZZfTlGIktQ"
        private const val MODEL_ID = "gemma-4-31b-it"
    }
}
