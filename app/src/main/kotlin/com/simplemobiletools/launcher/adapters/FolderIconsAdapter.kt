package com.simplemobiletools.launcher.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.view.iterator
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.extensions.handleGridItemPopupMenu
import com.simplemobiletools.launcher.interfaces.ItemMenuListenerAdapter
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.item_launcher_label.view.launcher_icon
import kotlinx.android.synthetic.main.item_launcher_label.view.launcher_label
import kotlinx.android.synthetic.main.item_launcher_label.view.popup_anchor

class FolderIconsAdapter(
    activity: BaseSimpleActivity, var items: MutableList<HomeScreenGridItem>, private val iconPadding: Int,
    recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    override fun getActionMenuId() = 0

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = itemCount

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.id?.toInt() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun prepareActionMode(menu: Menu) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(R.layout.item_launcher_label, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        setupView(holder.itemView, item)
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    private fun removeItem(item: HomeScreenGridItem) {
        val position = items.indexOfFirst { it.id == item.id }
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    private fun setupView(view: View, item: HomeScreenGridItem) {
        view.apply {
            launcher_label.text = item.title
            launcher_label.setTextColor(textColor)
            launcher_icon.setPadding(iconPadding, iconPadding, iconPadding, 0)
            launcher_icon.setImageDrawable(item.drawable)

            val mainListener = (activity as? MainActivity)?.menuListener

            setOnClickListener { itemClick(item) }
            setOnLongClickListener {
                popup_anchor.y = launcher_icon.y
                activity.handleGridItemPopupMenu(popup_anchor, item, false, object : ItemMenuListenerAdapter() {
                    override fun appInfo(gridItem: HomeScreenGridItem) {
                        mainListener?.appInfo(gridItem)
                    }

                    override fun remove(gridItem: HomeScreenGridItem) {
                        mainListener?.remove(gridItem)
                        removeItem(gridItem)
                    }

                    override fun uninstall(gridItem: HomeScreenGridItem) {
                        mainListener?.uninstall(gridItem)
                    }

                    override fun rename(gridItem: HomeScreenGridItem) {
                        mainListener?.rename(gridItem)
                    }

                    override fun beforeShow(menu: Menu) {
                        var visibleMenuItems = 0
                        for (menuItem in menu.iterator()) {
                            if (menuItem.isVisible) {
                                visibleMenuItems++
                            }
                        }
                        val yOffset = resources.getDimension(R.dimen.long_press_anchor_button_offset_y) * (visibleMenuItems - 1)
                        popup_anchor.y -= yOffset
                    }
                })
                true
            }
        }
    }

    fun updateItems(items: List<HomeScreenGridItem>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }
}
