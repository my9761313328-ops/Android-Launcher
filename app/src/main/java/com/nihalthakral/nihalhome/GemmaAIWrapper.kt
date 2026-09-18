package com.nihalthakral.nihalhome

import android.util.Base64
import com.google.genai.kotlin.types.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GemmaAIWrapper {

    fun streamReply(systemInstruction: String, contents: List<Content>): Flow<String> {
        return flow {
            val connection = URL(WORKER_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("x-app-secret", APP_SECRET)

            val requestBody = buildRequestBody(systemInstruction, contents)

            connection.outputStream.use { output ->
                output.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            try {
                if (connection.responseCode !in 200..299) {
                    val errorText = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: "HTTP ${connection.responseCode}"
                    throw IOException(errorText)
                }

                val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                val buffer = CharArray(READ_BUFFER_SIZE)
                while (true) {
                    val readCount = reader.read(buffer)
                    if (readCount == -1) break
                    if (readCount > 0) emit(String(buffer, 0, readCount))
                }
                reader.close()
            } finally {
                connection.disconnect()
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun buildRequestBody(systemInstruction: String, contents: List<Content>): JSONObject {
        val contentsArray = JSONArray()

        contents.forEach { messageContent ->
            val partsArray = JSONArray()

            messageContent.parts?.forEach { part ->
                val partObject = JSONObject()

                part.text?.let { partText ->
                    partObject.put("text", partText)
                }

                part.inlineData?.let { blob ->
                    val inlineDataObject = JSONObject()
                    inlineDataObject.put("mimeType", blob.mimeType)
                    inlineDataObject.put("data", Base64.encodeToString(blob.data, Base64.NO_WRAP))
                    partObject.put("inlineData", inlineDataObject)
                }

                partsArray.put(partObject)
            }

            val contentObject = JSONObject()
            contentObject.put("role", messageContent.role)
            contentObject.put("parts", partsArray)
            contentsArray.put(contentObject)
        }

        val requestBody = JSONObject()
        requestBody.put("contents", contentsArray)
        requestBody.put("systemInstruction", systemInstruction)
        requestBody.put("temperature", TEMPERATURE)
        return requestBody
    }

    companion object {
        private const val WORKER_URL = "https://ask-expert-ai-proxy.nihalthakral-trader.workers.dev/"
        private const val APP_SECRET = "nihalhome_9x7k2mQp5rT8vL3w"
        private const val TEMPERATURE = 0.3
        private const val CONNECT_TIMEOUT_MS = 30000
        private const val READ_TIMEOUT_MS = 60000
        private const val READ_BUFFER_SIZE = 512
    }
}
