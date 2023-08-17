package com.simplemobiletools.launcher.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.normalizeString
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.launcher.adapters.HiddenIconsAdapter
import com.simplemobiletools.launcher.databinding.ActivityHiddenIconsBinding
import com.simplemobiletools.launcher.extensions.config
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
import com.simplemobiletools.launcher.extensions.hiddenIconsDB
import com.simplemobiletools.launcher.models.HiddenIcon

class HiddenIconsActivity : SimpleActivity(), RefreshRecyclerViewListener {
    private lateinit var binding: ActivityHiddenIconsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenIconsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateIcons()

        updateMaterialActivityViews(
            binding.manageHiddenIconsCoordinator,
            binding.manageHiddenIconsList,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(binding.manageHiddenIconsList, binding.manageHiddenIconsToolbar)

        val layoutManager = binding.manageHiddenIconsList.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = config.drawerColumnCount
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.manageHiddenIconsToolbar, NavigationIcon.Arrow)
    }

    private fun updateIcons() {
        ensureBackgroundThread {
            val hiddenIcons = hiddenIconsDB.getHiddenIcons().sortedWith(
                compareBy({
                    it.title.normalizeString().lowercase()
                }, {
                    it.packageName
                })
            ).toMutableList() as ArrayList<HiddenIcon>

            val hiddenIconsEmpty = hiddenIcons.isEmpty()
            runOnUiThread {
                binding.manageHiddenIconsPlaceholder.beVisibleIf(hiddenIconsEmpty)
            }

            if (hiddenIcons.isNotEmpty()) {
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

                hiddenIcons.firstOrNull { it.packageName == applicationContext.packageName }?.apply {
                    drawable = getDrawableForPackageName(packageName)
                }
            }

            val iconsToRemove = hiddenIcons.filter { it.drawable == null }
            if (iconsToRemove.isNotEmpty()) {
                hiddenIconsDB.removeHiddenIcons(iconsToRemove)
                hiddenIcons.removeAll(iconsToRemove)
            }

            runOnUiThread {
                HiddenIconsAdapter(this, hiddenIcons, this, binding.manageHiddenIconsList) {
                }.apply {
                    binding.manageHiddenIconsList.adapter = this
                }
            }
        }
    }

    override fun refreshItems() {
        updateIcons()
    }
}
