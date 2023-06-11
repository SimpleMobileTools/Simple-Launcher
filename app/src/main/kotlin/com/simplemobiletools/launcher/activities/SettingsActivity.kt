package com.simplemobiletools.launcher.activities

import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.isTiramisuPlus
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.config
import com.simplemobiletools.launcher.helpers.*
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        updateMaterialActivityViews(settings_coordinator, settings_holder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(settings_nested_scrollview, settings_toolbar)
        setupOptionsMenu()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(settings_toolbar, NavigationIcon.Arrow)
        refreshMenuItems()

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupUseEnglish()
        setupLanguage()
        setupManageHiddenIcons()
        setupGridSize()
        updateTextColors(settings_holder)

        arrayOf(settings_color_customization_section_label, settings_general_settings_label).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupOptionsMenu() {
        settings_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.about -> launchAbout()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun refreshMenuItems() {
        settings_toolbar.menu.apply {
            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(R.bool.hide_google_relations)
        }
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(isOrWasThankYouInstalled())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settings_color_customization_label.text = getCustomizeColorsString()
        settings_color_customization_holder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        settings_language.text = Locale.getDefault().displayLanguage
        settings_language_holder.beVisibleIf(isTiramisuPlus())
        settings_language_holder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupManageHiddenIcons() {
        settings_manage_hidden_icons_holder.setOnClickListener {
            startActivity(Intent(this, HiddenIconsActivity::class.java))
        }
    }

    private fun setupGridSize() {
        settings_grid_size.text = getHomeGridText()
        settings_grid_size_holder.setOnClickListener {

            val items = arrayListOf(
                RadioItem(GRID_SIZE_4x4, getString(R.string.home_grid_4x4)),
                RadioItem(GRID_SIZE_5x5, getString(R.string.home_grid_5x5)),
                RadioItem(GRID_SIZE_6x4, getString(R.string.home_grid_6x4)),
                RadioItem(GRID_SIZE_6x5, getString(R.string.home_grid_6x5)),
                RadioItem(GRID_SIZE_6x6, getString(R.string.home_grid_6x6))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.homeGrid) {
                config.homeGrid = it as Int
                setHomeGrid()
                settings_grid_size.text = getHomeGridText()
            }
        }
    }
    private fun setHomeGrid(){
        when(config.homeGrid){
            GRID_SIZE_4x4 -> {
                config.rowCount = 4
                config.columnCount = 4
            }
            GRID_SIZE_5x5 -> {
                config.rowCount = 5
                config.columnCount = 5
            }
            GRID_SIZE_6x4 -> {
                config.rowCount = 6
                config.columnCount = 4
            }
            GRID_SIZE_6x5 -> {
                config.rowCount = 6
                config.columnCount = 5
            }
            GRID_SIZE_6x6 -> {
                config.rowCount = 6
                config.columnCount = 6
            }
            else -> {
                config.rowCount = 5
                config.columnCount = 5
            }
        }
    }
    private fun getHomeGridText(): String{
        return when(config.homeGrid){
            GRID_SIZE_4x4 -> getString(R.string.home_grid_4x4)
            GRID_SIZE_5x5 -> getString(R.string.home_grid_5x5)
            GRID_SIZE_6x4 -> getString(R.string.home_grid_6x4)
            GRID_SIZE_6x5 -> getString(R.string.home_grid_6x5)
            GRID_SIZE_6x6 -> getString(R.string.home_grid_6x6)
            else -> getString(R.string.home_grid_5x5)
        }
    }
    private fun launchAbout() {
        val licenses = 0L
        val faqItems = ArrayList<FAQItem>()

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }
}
