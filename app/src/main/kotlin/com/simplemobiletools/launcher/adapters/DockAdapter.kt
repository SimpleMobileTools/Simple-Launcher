package com.simplemobiletools.launcher.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.item_dock.view.app_icon

class DockAdapter(
    private val context: Context,
    private val itemClick: (HomeScreenGridItem) -> Unit
) : RecyclerView.Adapter<DockAdapter.ViewHolder>() {

    private val items = mutableListOf<HomeScreenGridItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dock, parent, false)
        return ViewHolder(view, itemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<HomeScreenGridItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val movedItem = items.removeAt(fromPosition)
        items.add(toPosition, movedItem)
        notifyItemMoved(fromPosition, toPosition)
    }

    inner class ViewHolder(
        view: View,
        private val itemClick: (HomeScreenGridItem) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        fun bind(item: HomeScreenGridItem) {
            itemView.app_icon.setImageDrawable(context.getDrawableForPackageName(item.packageName))
            itemView.setOnClickListener { itemClick(item) }
            itemView.setOnLongClickListener {
                itemView.startDragAndDrop(null, View.DragShadowBuilder(itemView), item, 0)
                true
            }
        }
    }
}
