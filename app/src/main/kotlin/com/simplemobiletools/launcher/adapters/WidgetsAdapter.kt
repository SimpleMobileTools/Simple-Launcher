package com.simplemobiletools.launcher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.SimpleActivity
import com.simplemobiletools.launcher.helpers.WIDGET_LIST_ITEMS_HOLDER
import com.simplemobiletools.launcher.helpers.WIDGET_LIST_SECTION
import com.simplemobiletools.launcher.models.WidgetsListItem
import com.simplemobiletools.launcher.models.WidgetsListItemsHolder
import com.simplemobiletools.launcher.models.WidgetsListSection
import kotlinx.android.synthetic.main.item_widget_list_section.view.*

class WidgetsAdapter(
    val activity: SimpleActivity,
    val widgetListItems: ArrayList<WidgetsListItem>
) : RecyclerView.Adapter<WidgetsAdapter.ViewHolder>() {

    private var textColor = activity.getProperTextColor()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = when (viewType) {
            WIDGET_LIST_SECTION -> R.layout.item_widget_list_section
            else -> R.layout.item_widget_list_items_holder
        }

        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val widgetListItem = widgetListItems[position]
        holder.bindView(widgetListItems[position]) { itemView, layoutPosition ->
            when (widgetListItem) {
                is WidgetsListSection -> setupListSection(itemView, widgetListItem)
                is WidgetsListItemsHolder -> setupListItemsHolder(itemView, widgetListItem)
            }
        }
    }

    override fun getItemCount() = widgetListItems.size

    override fun getItemViewType(position: Int) = when {
        widgetListItems[position] is WidgetsListSection -> WIDGET_LIST_SECTION
        else -> WIDGET_LIST_ITEMS_HOLDER
    }

    private fun setupListSection(view: View, section: WidgetsListSection) {
        view.apply {
            widget_app_title.text = section.appTitle
            widget_app_title.setTextColor(textColor)

            widget_app_icon.setImageDrawable(section.appIcon)
        }
    }

    private fun setupListItemsHolder(view: View, listItem: WidgetsListItem) {}

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(widgetListItem: WidgetsListItem, callback: (itemView: View, adapterPosition: Int) -> Unit) {
            itemView.apply {
                callback(this, adapterPosition)
            }
        }
    }
}
