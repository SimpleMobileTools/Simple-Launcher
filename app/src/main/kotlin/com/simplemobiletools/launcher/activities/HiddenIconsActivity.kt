package com.simplemobiletools.launcher.activities

import android.os.Bundle
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.adapters.HiddenIconsAdapter
import com.simplemobiletools.launcher.extensions.getColumnCount
import com.simplemobiletools.launcher.extensions.hiddenIconsDB
import com.simplemobiletools.launcher.models.HiddenIcon
import kotlinx.android.synthetic.main.activity_hidden_icons.*

class HiddenIconsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_icons)
        updateIcons()

        val layoutManager = manage_hidden_icons_list.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = getColumnCount()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(manage_hidden_icons_toolbar, NavigationIcon.Arrow)
    }

    private fun updateIcons() {
        ensureBackgroundThread {
            val hiddenIcons = hiddenIconsDB.getHiddenIcons().toMutableList() as ArrayList<HiddenIcon>
            HiddenIconsAdapter(this, hiddenIcons) {

            }.apply {
                manage_hidden_icons_list.adapter = this
            }
        }
    }
}
