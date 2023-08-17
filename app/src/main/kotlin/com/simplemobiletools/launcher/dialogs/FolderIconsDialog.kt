package com.simplemobiletools.launcher.dialogs

import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.AutoGridLayoutManager
import com.simplemobiletools.launcher.adapters.FolderIconsAdapter
import com.simplemobiletools.launcher.databinding.DialogFolderIconsBinding
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
import com.simplemobiletools.launcher.extensions.homeScreenGridItemsDB
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_ICON
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_SHORTCUT
import com.simplemobiletools.launcher.models.HomeScreenGridItem

class FolderIconsDialog(
    val activity: BaseSimpleActivity,
    private val folder: HomeScreenGridItem,
    private val iconWidth: Int,
    private val iconPadding: Int,
    private val dismissListener: () -> Unit,
    private val itemClick: (HomeScreenGridItem) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val binding = DialogFolderIconsBinding.inflate(activity.layoutInflater)

    init {

        binding.dialogFolderIconsGrid.layoutManager = AutoGridLayoutManager(activity, iconWidth)

        ensureBackgroundThread {
            val items = activity.homeScreenGridItemsDB.getFolderItems(folder.id!!)
            items.forEach { item ->
                if (item.type == ITEM_TYPE_ICON) {
                    item.drawable = activity.getDrawableForPackageName(item.packageName)
                } else if (item.type == ITEM_TYPE_SHORTCUT) {
                    item.drawable = BitmapDrawable(item.icon)
                }
            }
            activity.runOnUiThread {
                initDialog(items, binding)
            }
        }
    }

    private fun initDialog(items: List<HomeScreenGridItem>, binding: DialogFolderIconsBinding) {
        binding.dialogFolderIconsGrid.adapter = FolderIconsAdapter(activity, items.toMutableList(), iconPadding, binding.dialogFolderIconsGrid) {
            it as HomeScreenGridItem
            itemClick(it)
            dialog?.dismiss()
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .setOnDismissListener { dismissListener() }
            .apply {
                activity.setupDialogStuff(binding.root, this, 0, folder.title) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    fun fetchItems() {
        ensureBackgroundThread {
            val items = activity.homeScreenGridItemsDB.getFolderItems(folder.id!!)
            items.forEach { item ->
                if (item.type == ITEM_TYPE_ICON) {
                    item.drawable = activity.getDrawableForPackageName(item.packageName)
                } else if (item.type == ITEM_TYPE_SHORTCUT) {
                    item.drawable = BitmapDrawable(item.icon)
                }
            }
            activity.runOnUiThread {
                (binding.dialogFolderIconsGrid.adapter as FolderIconsAdapter).updateItems(items)
            }
        }
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}
