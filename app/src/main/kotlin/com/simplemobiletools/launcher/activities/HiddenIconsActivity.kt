package com.simplemobiletools.launcher.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.adapters.HiddenIconsAdapter
import com.simplemobiletools.launcher.extensions.getColumnCount
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
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
            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)

            val list = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
            for (info in list) {
                val componentInfo = info.activityInfo.applicationInfo
                val packageName = componentInfo.packageName
                val activityName = info.activityInfo.name
                hiddenIcons.firstOrNull { it.getIconIdentifier() == "$packageName/$activityName" }?.apply {
                    drawable = info.loadIcon(packageManager) ?: getDrawableForPackageName(packageName)
                }
            }

            runOnUiThread {
                HiddenIconsAdapter(this, hiddenIcons) {

                }.apply {
                    manage_hidden_icons_list.adapter = this
                }
            }
        }
    }
}
