package com.simplemobiletools.launcher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.portrait
import com.simplemobiletools.commons.extensions.realScreenSize
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.SimpleActivity
import com.simplemobiletools.launcher.models.HiddenIcon
import kotlinx.android.synthetic.main.item_hidden_icon.view.*

class HiddenIconsAdapter(
    val activity: SimpleActivity,
    var hiddenIcons: ArrayList<HiddenIcon>,
    val itemClick: (Any) -> Unit
) : RecyclerView.Adapter<HiddenIconsAdapter.ViewHolder>() {

    private var textColor = activity.getProperTextColor()
    private var iconPadding = 0

    init {
        calculateIconWidth()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hidden_icon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(hiddenIcons[position])
    }

    override fun getItemCount() = hiddenIcons.size

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

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(icon: HiddenIcon): View {
            itemView.apply {
                hidden_icon_label.text = icon.title
                hidden_icon_label.setTextColor(textColor)
                hidden_icon.setPadding(iconPadding, iconPadding, iconPadding, 0)

                val factory = DrawableCrossFadeFactory.Builder(150).setCrossFadeEnabled(true).build()

                Glide.with(activity)
                    .load(icon.drawable)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .transition(DrawableTransitionOptions.withCrossFade(factory))
                    .into(hidden_icon)

                setOnClickListener {
                    itemClick(icon)
                }
            }

            return itemView
        }
    }
}
