package com.simplemobiletools.launcher.activities

import android.os.Bundle
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.launcher.R
import kotlinx.android.synthetic.main.activity_hidden_icons.*

class HiddenIconsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_icons)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(manage_hidden_icons_toolbar, NavigationIcon.Arrow)
    }
}
