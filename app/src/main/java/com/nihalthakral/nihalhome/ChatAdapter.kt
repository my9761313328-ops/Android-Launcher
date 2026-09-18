package com.nihalthakral.nihalhome

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val thumbnailCache = LinkedHashMap<String, Bitmap>()

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].role == ChatRole.USER) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            AiViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserViewHolder -> holder.bind(message)
            is AiViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        thumbnailCache.clear()
        notifyDataSetChanged()
    }

    fun appendMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessageText(text: String) {
        if (messages.isEmpty()) return
        messages[messages.size - 1].text = text
        notifyItemChanged(messages.size - 1)
    }

    private fun decodeThumbnail(path: String): Bitmap? {
        thumbnailCache[path]?.let { return it }
        val options = BitmapFactory.Options()
        options.inSampleSize = 4
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        thumbnailCache[path] = bitmap
        return bitmap
    }

    private val userImageSlotIds = intArrayOf(
        R.id.imageChatUser1,
        R.id.imageChatUser2,
        R.id.imageChatUser3,
        R.id.imageChatUser4,
        R.id.imageChatUser5
    )

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.textChatMessageUser)
        private val imageGrid: GridLayout = itemView.findViewById(R.id.gridChatImagesUser)
        private val imageViews: List<ImageView> = userImageSlotIds.map { itemView.findViewById(it) }

        fun bind(message: ChatMessage) {
            textView.text = message.text
            textView.visibility = if (message.text.isBlank()) View.GONE else View.VISIBLE

            if (message.imagePaths.isEmpty()) {
                imageGrid.visibility = View.GONE
            } else {
                imageGrid.visibility = View.VISIBLE
                imageViews.forEachIndexed { index, imageView ->
                    val path = message.imagePaths.getOrNull(index)
                    if (path != null) {
                        imageView.visibility = View.VISIBLE
                        imageView.setImageBitmap(decodeThumbnail(path))
                    } else {
                        imageView.visibility = View.GONE
                    }
                }
            }
        }
    }

    inner class AiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.textChatMessageAi)

        fun bind(message: ChatMessage) {
            textView.text = message.text
        }
    }

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
    }
}
