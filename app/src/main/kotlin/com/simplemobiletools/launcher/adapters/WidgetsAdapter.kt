package com.simplemobiletools.launcher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.SimpleActivity
import com.simplemobiletools.launcher.models.AppWidget
import kotlinx.android.synthetic.main.item_widget_app.view.*

class WidgetsAdapter(
    val activity: SimpleActivity,
    val appWidgets: ArrayList<AppWidget>,
    val itemClick: (Any) -> Unit
) : RecyclerView.Adapter<WidgetsAdapter.ViewHolder>() {

    private var textColor = activity.getProperTextColor()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_widget_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(appWidgets[position])
    }

    override fun getItemCount() = appWidgets.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(widget: AppWidget): View {
            itemView.apply {
                widget_app_title.text = widget.appTitle
                widget_app_title.setTextColor(textColor)
            }

            return itemView
        }
    }
}
