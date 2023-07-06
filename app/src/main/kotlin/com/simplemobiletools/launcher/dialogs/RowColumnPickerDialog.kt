package com.simplemobiletools.launcher.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.view.ViewGroup
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.config
import com.simplemobiletools.launcher.helpers.MAX_COLUMN_COUNT
import com.simplemobiletools.launcher.helpers.MAX_ROW_COUNT
import com.simplemobiletools.launcher.helpers.MIN_COLUMN_COUNT
import com.simplemobiletools.launcher.helpers.MIN_ROW_COUNT
import kotlinx.android.synthetic.main.dialog_row_column.view.column_number_picker
import kotlinx.android.synthetic.main.dialog_row_column.view.row_number_picker

class RowColumnPickerDialog(private val activity: Activity, private val callback: () -> Unit) {

    init {
        val view = (activity.layoutInflater.inflate(R.layout.dialog_row_column, null) as ViewGroup)
        view.apply {
            row_number_picker?.apply {
                maxValue = MAX_ROW_COUNT
                minValue = MIN_ROW_COUNT
                wrapSelectorWheel = false
                value = activity.config.rowCount
            }

            column_number_picker?.apply {
                maxValue = MAX_COLUMN_COUNT
                minValue = MIN_COLUMN_COUNT
                wrapSelectorWheel = false
                value = activity.config.columnCount
            }
        }
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.save, null)
            .apply {
                activity.setupDialogStuff(view, this, titleText = activity.getString(R.string.set_drawer_grid_text)) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val rows = view.row_number_picker.value
                        val column = view.column_number_picker.value

                        activity.config.rowCount = rows
                        activity.config.columnCount = column
                        callback()
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
