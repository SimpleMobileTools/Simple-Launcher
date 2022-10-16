package com.simplemobiletools.launcher.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.view.ViewGroup
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.mydebug
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.homeScreenGridItemsDB
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.dialog_rename_item.view.*

class RenameItemDialog(val activity: Activity, val item: HomeScreenGridItem, val callback: () -> Unit) {

    init {
        val view = (activity.layoutInflater.inflate(R.layout.dialog_rename_item, null) as ViewGroup)
        view.rename_item_edittext.setText(item.title)

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.rename) { alertDialog ->
                    alertDialog.showKeyboard(view.rename_item_edittext)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = view.rename_item_edittext.value
                        if (!newTitle.isEmpty()) {
                            ensureBackgroundThread {
                                val result = activity.homeScreenGridItemsDB.updateItemTitle(newTitle, item.id!!)
                                if (result == 1) {
                                    callback()
                                    alertDialog.dismiss()
                                } else {
                                    activity.toast(R.string.unknown_error_occurred)
                                }
                            }
                        } else {
                            activity.toast(R.string.value_cannot_be_empty)
                        }
                    }
                }
            }
    }
}
