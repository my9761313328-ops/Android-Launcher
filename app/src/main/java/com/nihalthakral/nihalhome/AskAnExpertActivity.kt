package com.nihalthakral.nihalhome

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.genai.kotlin.types.Blob
import com.google.genai.kotlin.types.Content
import com.google.genai.kotlin.types.Part
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AskAnExpertActivity : ComponentActivity() {

    private lateinit var recyclerChatMessages: RecyclerView
    private lateinit var editChatMessage: EditText
    private lateinit var buttonSendMessage: ImageButton
    private lateinit var buttonAttachImage: ImageButton
    private lateinit var buttonNewChat: TextView
    private lateinit var scrollImagePreview: View
    private lateinit var containerImagePreview: LinearLayout

    private lateinit var adapter: ChatAdapter
    private val persistedMessages: MutableList<ChatMessage> = mutableListOf()
    private val pendingImagePaths: MutableList<String> = mutableListOf()

    private val aiWrapper = GemmaAIWrapper()
    private var isSending = false

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)
    ) { uris -> handleImagesPicked(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ask_an_expert)

        recyclerChatMessages = findViewById(R.id.recyclerChatMessages)
        editChatMessage = findViewById(R.id.editChatMessage)
        buttonSendMessage = findViewById(R.id.buttonSendMessage)
        buttonAttachImage = findViewById(R.id.buttonAttachImage)
        buttonNewChat = findViewById(R.id.buttonNewChat)
        scrollImagePreview = findViewById(R.id.scrollImagePreview)
        containerImagePreview = findViewById(R.id.containerImagePreview)

        persistedMessages.addAll(ChatHistoryStore.loadMessages(this))

        val initialDisplayList: MutableList<ChatMessage> = if (persistedMessages.isEmpty()) {
            mutableListOf(buildWelcomeMessage())
        } else {
            persistedMessages.toMutableList()
        }

        adapter = ChatAdapter(initialDisplayList)
        recyclerChatMessages.layoutManager = LinearLayoutManager(this)
        recyclerChatMessages.adapter = adapter
        scrollToBottom()

        buttonAttachImage.setOnClickListener { onAttachImageClicked() }
        buttonSendMessage.setOnClickListener { onSendClicked() }
        buttonNewChat.setOnClickListener { onNewChatClicked() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToMainScreen()
            }
        })
    }

    private fun onAttachImageClicked() {
        if (pendingImagePaths.size >= MAX_IMAGES) {
            showMaxImagesNotice()
            return
        }
        pickImagesLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun handleImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return

        var trimmed = false
        for (uri in uris) {
            if (pendingImagePaths.size >= MAX_IMAGES) {
                trimmed = true
                break
            }
            val path = copyUriToAppStorage(uri)
            if (path != null) {
                pendingImagePaths.add(path)
            }
        }

        if (trimmed) {
            showMaxImagesNotice()
        }

        rebuildImagePreview()
    }

    private fun showMaxImagesNotice() {
        val isHindi = LocalizationHelper.isHindiSelected(this)
        val message = if (isHindi)
            getString(R.string.ask_expert_max_images_notice_hi)
        else
            getString(R.string.ask_expert_max_images_notice)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun copyUriToAppStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val outputFile = File(ChatHistoryStore.imagesDirectory(this), "${UUID.randomUUID()}.jpg")
            val outputStream = FileOutputStream(outputFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            outputFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun rebuildImagePreview() {
        containerImagePreview.removeAllViews()

        if (pendingImagePaths.isEmpty()) {
            scrollImagePreview.visibility = View.GONE
            return
        }

        scrollImagePreview.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)

        pendingImagePaths.forEach { path ->
            val chip = inflater.inflate(R.layout.item_chat_image_preview, containerImagePreview, false)
            val thumb = chip.findViewById<ImageView>(R.id.imagePreviewThumb)
            thumb.setImageBitmap(android.graphics.BitmapFactory.decodeFile(path))

            val removeButton = chip.findViewById<ImageButton>(R.id.buttonRemovePreviewImage)
            removeButton.setOnClickListener {
                pendingImagePaths.remove(path)
                rebuildImagePreview()
            }

            containerImagePreview.addView(chip)
        }
    }

    private fun onSendClicked() {
        if (isSending) return

        val text = editChatMessage.text.toString().trim()
        if (text.isEmpty() && pendingImagePaths.isEmpty()) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            text = text,
            imagePaths = pendingImagePaths.toList()
        )

        persistedMessages.add(userMessage)
        adapter.appendMessage(userMessage)
        ChatHistoryStore.saveMessages(this, persistedMessages)

        editChatMessage.setText("")
        pendingImagePaths.clear()
        rebuildImagePreview()
        scrollToBottom()

        streamAiReply(userMessage)
    }

    private fun streamAiReply(userMessage: ChatMessage) {
        val contents = buildApiContents(userMessage)
        val systemInstruction = buildSystemInstruction()

        val placeholder = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.MODEL,
            text = "",
            isStreaming = true
        )
        adapter.appendMessage(placeholder)
        scrollToBottom()

        setSendingEnabled(false)

        lifecycleScope.launch {
            val builder = StringBuilder()
            try {
                aiWrapper.streamReply(systemInstruction, contents).collect { chunk ->
                    builder.append(chunk)
                    adapter.updateLastMessageText(builder.toString())
                    scrollToBottom()
                }

                val finalMessage = ChatMessage(
                    id = placeholder.id,
                    role = ChatRole.MODEL,
                    text = builder.toString()
                )
                persistedMessages.add(finalMessage)
                ChatHistoryStore.saveMessages(this@AskAnExpertActivity, persistedMessages)
            } catch (e: Throwable) {
                android.util.Log.e("AskAnExpert", "AI reply failed", e)
                val isHindi = LocalizationHelper.isHindiSelected(this@AskAnExpertActivity)
                val baseError = if (isHindi)
                    getString(R.string.ask_expert_error_reply_hi)
                else
                    getString(R.string.ask_expert_error_reply)
                val debugDetail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                val errorText = "$baseError\n\n[$debugDetail]"
                adapter.updateLastMessageText(errorText)
            } finally {
                setSendingEnabled(true)
            }
        }
    }

    private fun buildApiContents(newUserMessage: ChatMessage): List<Content> {
        val previousContext = persistedMessages.dropLast(1).takeLast(MAX_CONTEXT_MESSAGES)
        val contents = mutableListOf<Content>()
        previousContext.forEach { contents.add(toApiContent(it)) }
        contents.add(toApiContent(newUserMessage))
        return contents
    }

    private fun toApiContent(message: ChatMessage): Content {
        val parts = mutableListOf<Part>()
        message.imagePaths.forEach { path ->
            val bytes = File(path).readBytes()
            parts.add(Part(inlineData = Blob(mimeType = "image/jpeg", data = bytes)))
        }
        if (message.text.isNotBlank()) {
            parts.add(Part(text = message.text))
        }
        val role = if (message.role == ChatRole.USER) "user" else "model"
        return Content(role = role, parts = parts)
    }

    private fun buildSystemInstruction(): String {
        val isHindi = LocalizationHelper.isHindiSelected(this)
        val languagePreferenceLine = if (isHindi)
            "The user has chosen Hindi as their preferred app language, so prefer replying in Hindi, but if the user writes in a different language, reply in that language instead."
        else
            "The user has chosen English as their preferred app language, so prefer replying in English, but if the user writes in a different language, reply in that language instead."

        return "You are a warm, patient Digital Safety Expert inside a mobile app used mostly by non-technical users in India. " +
            "Your job is to help people who suspect a scam call, phishing message, hacking attempt, fraudulent app, or any other online or social engineering threat. " +
            "Explain things in very simple, non-technical language, stay reassuring but honest, and give clear, practical, step by step safety advice. " +
            "Never ask the user for OTPs, passwords, PINs or bank details, and always warn them never to share these with anyone. " +
            languagePreferenceLine
    }

    private fun buildWelcomeMessage(): ChatMessage {
        val isHindi = LocalizationHelper.isHindiSelected(this)
        val text = if (isHindi)
            getString(R.string.welcome_message_expert_hi)
        else
            getString(R.string.welcome_message_expert_en)

        return ChatMessage(
            id = "welcome",
            role = ChatRole.MODEL,
            text = text
        )
    }

    private fun onNewChatClicked() {
        val isHindi = LocalizationHelper.isHindiSelected(this)

        AlertDialog.Builder(this)
            .setTitle(if (isHindi) R.string.ask_expert_new_chat_confirm_title_hi else R.string.ask_expert_new_chat_confirm_title)
            .setMessage(if (isHindi) R.string.ask_expert_new_chat_confirm_message_hi else R.string.ask_expert_new_chat_confirm_message)
            .setPositiveButton(if (isHindi) R.string.action_yes_hi else R.string.action_yes) { dialog, _ ->
                startNewChat()
                dialog.dismiss()
            }
            .setNegativeButton(if (isHindi) R.string.action_cancel_hi else R.string.action_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun startNewChat() {
        ChatHistoryStore.clearAll(this)
        persistedMessages.clear()
        pendingImagePaths.clear()
        rebuildImagePreview()
        adapter.setMessages(mutableListOf(buildWelcomeMessage()))
        editChatMessage.setText("")
    }

    private fun setSendingEnabled(enabled: Boolean) {
        isSending = !enabled
        buttonSendMessage.isEnabled = enabled
        buttonAttachImage.isEnabled = enabled
        buttonSendMessage.alpha = if (enabled) 1f else 0.5f
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            recyclerChatMessages.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun goToMainScreen() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val MAX_IMAGES = 5
        private const val MAX_CONTEXT_MESSAGES = 15
    }
}
