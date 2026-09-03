package com.nihalthakral.nihalhome

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView

class FrequentAppsAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<FrequentAppsAdapter.ViewHolder>() {

    private var items: List<AppInfo> = emptyList()

    fun submitList(newItems: List<AppInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = items[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener {
            onLongClick(app, it)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}

class CoreAppsAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<CoreAppsAdapter.ViewHolder>() {

    private var items: List<AppInfo> = emptyList()

    fun submitList(newItems: List<AppInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_core_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = items[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener {
            onLongClick(app, it)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}

sealed class AppRow {
    data class Header(val letter: String) : AppRow()
    data class Item(val app: AppInfo) : AppRow()
}

class AllAppsAdapter(
    private val favoritesStore: FavoritesStore,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit,
    private val onFavoriteToggled: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<AppRow> = emptyList()

    fun submit(apps: List<AppInfo>, grouped: Boolean) {
        rows = if (grouped) {
            buildGroupedRows(apps)
        } else {
            apps.map { AppRow.Item(it) }
        }
        notifyDataSetChanged()
    }

    private fun buildGroupedRows(apps: List<AppInfo>): List<AppRow> {
        val result = mutableListOf<AppRow>()

        val favoritePackages = favoritesStore.getFavoritePackages()
        if (favoritePackages.isNotEmpty()) {
            val favoriteApps = apps
                .filter { it.packageName in favoritePackages }
                .sortedBy { it.label.lowercase() }
            if (favoriteApps.isNotEmpty()) {
                result.add(AppRow.Header(FAV_HEADER_LABEL))
                favoriteApps.forEach { result.add(AppRow.Item(it)) }
            }
        }

        var lastLetter: String? = null
        for (app in apps) {
            val letter = app.label.take(1).uppercase()
            if (letter != lastLetter) {
                result.add(AppRow.Header(letter))
                lastLetter = letter
            }
            result.add(AppRow.Item(app))
        }
        return result
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is AppRow.Header -> TYPE_HEADER
        is AppRow.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_letter_header, parent, false))
        } else {
            ItemViewHolder(inflater.inflate(R.layout.item_app_list, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is AppRow.Header -> (holder as HeaderViewHolder).letter.text = row.letter
            is AppRow.Item -> {
                val h = holder as ItemViewHolder
                val app = row.app
                h.icon.setImageDrawable(app.icon)
                h.name.text = app.label
                h.itemView.setOnClickListener { onClick(app) }
                h.itemView.setOnLongClickListener {
                    onLongClick(app, it)
                    true
                }
                bindFavoriteStar(h.favStar, app)
            }
        }
    }

    private fun bindFavoriteStar(star: ImageView, app: AppInfo) {
        val isFavorite = favoritesStore.isFavorite(app.packageName)
        applyFavoriteStarState(star, isFavorite)
        star.setOnClickListener {
            val nowFavorite = favoritesStore.toggleFavorite(app.packageName)
            applyFavoriteStarState(star, nowFavorite)
            onFavoriteToggled(app, nowFavorite)
        }
    }

    private fun applyFavoriteStarState(star: ImageView, isFavorite: Boolean) {
        if (isFavorite) {
            star.setBackgroundResource(R.drawable.bg_fav_star_on)
            ImageViewCompat.setImageTintList(
                star,
                ColorStateList.valueOf(ContextCompat.getColor(star.context, R.color.fav_star_icon_on))
            )
        } else {
            star.setBackgroundResource(R.drawable.bg_fav_star_off)
            ImageViewCompat.setImageTintList(
                star,
                ColorStateList.valueOf(ContextCompat.getColor(star.context, R.color.fav_star_icon_off))
            )
        }
    }

    override fun getItemCount(): Int = rows.size

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val letter: TextView = view.findViewById(R.id.letterText)
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val favStar: ImageView = view.findViewById(R.id.favStar)
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
        const val FAV_HEADER_LABEL = "Fav. Apps"
    }
}
