package com.simplemobiletools.launcher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.SimpleActivity
import com.simplemobiletools.launcher.helpers.WIDGET_LIST_ITEMS_HOLDER
import com.simplemobiletools.launcher.helpers.WIDGET_LIST_SECTION
import com.simplemobiletools.launcher.models.WidgetsListItem
import com.simplemobiletools.launcher.models.WidgetsListItemsHolder
import com.simplemobiletools.launcher.models.WidgetsListSection
import kotlinx.android.synthetic.main.item_widget_list_items_holder.view.*
import kotlinx.android.synthetic.main.item_widget_list_section.view.*
import kotlinx.android.synthetic.main.item_widget_preview.view.*

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

    private fun setupListItemsHolder(view: View, listItem: WidgetsListItemsHolder) {
        view.widget_list_items_holder.removeAllViews()
        view.widget_list_items_scroll_view.scrollX = 0
        listItem.widgets.forEachIndexed { index, widget ->
            val imageSize = activity.resources.getDimension(R.dimen.widget_preview_size).toInt()
            val widgetPreview = LayoutInflater.from(activity).inflate(R.layout.item_widget_preview, null)
            view.widget_list_items_holder.addView(widgetPreview)

            val endMargin = if (index == listItem.widgets.size - 1) {
                activity.resources.getDimension(R.dimen.medium_margin).toInt()
            } else {
                0
            }

            widgetPreview.widget_title.apply {
                text = widget.widgetTitle
                setTextColor(textColor)
            }

            widgetPreview.widget_size.apply {
                text = if (widget.isShortcut) {
                    activity.getString(R.string.shortcut)
                } else {
                    "${widget.widthTiles} x ${widget.heightTiles}"
                }
                setTextColor(textColor)
            }

            (widgetPreview.widget_image.layoutParams as RelativeLayout.LayoutParams).apply {
                marginStart = activity.resources.getDimension(R.dimen.activity_margin).toInt()
                marginEnd = endMargin
                width = imageSize
                height = imageSize
            }

            Glide.with(activity)
                .load(widget.widgetPreviewImage)
                .into(widgetPreview.widget_image)

            widgetPreview.setOnClickListener {
                activity.toast(R.string.touch_hold_widget)
            }
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(widgetListItem: WidgetsListItem, callback: (itemView: View, adapterPosition: Int) -> Unit) {
            itemView.apply {
                callback(this, adapterPosition)
            }
        }
    }
}
