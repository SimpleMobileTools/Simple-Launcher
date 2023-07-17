package com.simplemobiletools.launcher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.realScreenSize
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.SimpleActivity
import com.simplemobiletools.launcher.extensions.config
import com.simplemobiletools.launcher.interfaces.AllAppsListener
import com.simplemobiletools.launcher.models.AppLauncher
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.item_launcher_label.view.*

class LaunchersAdapter(
    val activity: SimpleActivity,
    launchers: ArrayList<AppLauncher>,
    val allAppsListener: AllAppsListener,
    val itemClick: (Any) -> Unit
) : RecyclerView.Adapter<LaunchersAdapter.ViewHolder>(), RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textColor = activity.getProperTextColor()
    private var iconPadding = 0
    private var wereFreshIconsLoaded = false
    private var filterQuery: String? = null
    private var filteredLaunchers: List<AppLauncher> = launchers

    var launchers: ArrayList<AppLauncher> = launchers
        set(value) {
            field = value
            updateFilter()
        }

    init {
        calculateIconWidth()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_launcher_label, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(filteredLaunchers[position])
    }

    override fun getItemCount() = filteredLaunchers.size

    private fun calculateIconWidth() {
        val currentColumnCount = activity.config.drawerColumnCount

        val iconWidth = activity.realScreenSize.x / currentColumnCount
        iconPadding = (iconWidth * 0.1f).toInt()
    }

    fun hideIcon(item: HomeScreenGridItem) {
        val itemToRemove = launchers.firstOrNull { it.getLauncherIdentifier() == item.getItemIdentifier() }
        if (itemToRemove != null) {
            val position = launchers.indexOfFirst { it.getLauncherIdentifier() == item.getItemIdentifier() }
            val filteredPosition = filteredLaunchers.indexOfFirst { it.getLauncherIdentifier() == item.getItemIdentifier() }
            launchers.removeAt(position)
            updateFilter()
            notifyItemRemoved(filteredPosition)
        }
    }

    fun updateItems(newItems: ArrayList<AppLauncher>) {
        val oldSum = launchers.sumOf { it.getHashToCompare() }
        val newSum = newItems.sumOf { it.getHashToCompare() }
        if (oldSum != newSum || !wereFreshIconsLoaded) {
            launchers = newItems
            notifyDataSetChanged()
            wereFreshIconsLoaded = true
        }
    }

    fun updateSearchQuery(newQuery: String?) {
        if (filterQuery != newQuery) {
            filterQuery = newQuery
            updateFilter()
            notifyDataSetChanged()
        }
    }

    fun updateTextColor(newTextColor: Int) {
        if (newTextColor != textColor) {
            textColor = newTextColor
            notifyDataSetChanged()
        }
    }

    private fun updateFilter() {
        filteredLaunchers = launchers.filter { filterQuery == null || it.title.contains(filterQuery!!, ignoreCase = true) }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(launcher: AppLauncher): View {
            itemView.apply {
                launcher_label.text = launcher.title
                launcher_label.setTextColor(textColor)
                launcher_icon.setPadding(iconPadding, iconPadding, iconPadding, 0)

                val factory = DrawableCrossFadeFactory.Builder(150).setCrossFadeEnabled(true).build()
                val placeholderDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.placeholder_drawable, launcher.thumbnailColor)

                Glide.with(activity)
                    .load(launcher.drawable)
                    .placeholder(placeholderDrawable)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .transition(DrawableTransitionOptions.withCrossFade(factory))
                    .into(launcher_icon)

                setOnClickListener { itemClick(launcher) }
                setOnLongClickListener { view ->
                    allAppsListener.onAppLauncherLongPressed(view.x + width / 2, view.y, launcher)
                    true
                }
            }

            return itemView
        }
    }

    override fun onChange(position: Int) = filteredLaunchers.getOrNull(position)?.getBubbleText() ?: ""
}
