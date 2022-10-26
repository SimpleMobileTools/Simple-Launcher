package com.simplemobiletools.launcher.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.portrait
import com.simplemobiletools.commons.extensions.realScreenSize
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.hiddenIconsDB
import com.simplemobiletools.launcher.models.HiddenIcon
import kotlinx.android.synthetic.main.item_hidden_icon.view.*

class HiddenIconsAdapter(
    activity: BaseSimpleActivity,
    var hiddenIcons: ArrayList<HiddenIcon>,
    val listener: RefreshRecyclerViewListener,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private var iconPadding = 0

    init {
        calculateIconWidth()
    }

    override fun getActionMenuId() = R.menu.cab_hidden_icons

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_unhide_icon -> unHideSelection()
        }
    }

    override fun getSelectableItemCount() = hiddenIcons.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = hiddenIcons.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = hiddenIcons.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_hidden_icon, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = hiddenIcons[position]
        holder.bindView(folder, true, true) { itemView, adapterPosition ->
            setupView(itemView, folder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = hiddenIcons.size

    private fun getSelectedItems() = hiddenIcons.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<HiddenIcon>

    private fun unHideSelection() {
        val positions = getSelectedItemPositions()

        ensureBackgroundThread {
            val selectedItems = getSelectedItems()
            activity.hiddenIconsDB.removeHiddenIcons(selectedItems)
            hiddenIcons.removeAll(selectedItems)

            activity.runOnUiThread {
                removeSelectedItems(positions)
                if (hiddenIcons.isEmpty()) {
                    listener.refreshItems()
                }
            }
        }
    }

    private fun calculateIconWidth() {
        val currentColumnCount = activity.resources.getInteger(
            if (activity.portrait) {
                R.integer.portrait_column_count
            } else {
                R.integer.landscape_column_count
            }
        )

        val iconWidth = activity.realScreenSize.x / currentColumnCount
        iconPadding = (iconWidth * 0.1f).toInt()
    }

    private fun setupView(view: View, icon: HiddenIcon) {
        view.apply {
            hidden_icon_holder?.isSelected = selectedKeys.contains(icon.hashCode())
            hidden_icon_label.text = icon.title
            hidden_icon_label.setTextColor(textColor)
            hidden_icon.setPadding(iconPadding, iconPadding, iconPadding, 0)

            val factory = DrawableCrossFadeFactory.Builder(150).setCrossFadeEnabled(true).build()

            Glide.with(activity)
                .load(icon.drawable)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .transition(DrawableTransitionOptions.withCrossFade(factory))
                .into(hidden_icon)
        }
    }
}
