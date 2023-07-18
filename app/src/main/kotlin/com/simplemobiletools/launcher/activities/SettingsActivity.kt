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
import com.simplemobiletools.launcher.helpers.MAX_COLUMN_COUNT
import com.simplemobiletools.launcher.helpers.MAX_ROW_COUNT
import com.simplemobiletools.launcher.helpers.MIN_COLUMN_COUNT
import com.simplemobiletools.launcher.helpers.MIN_ROW_COUNT
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.Locale
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
        setupDrawerColumnCount()
        setupHomeRowCount()
        setupHomeColumnCount()
        setupLanguage()
        setupManageHiddenIcons()
        updateTextColors(settings_holder)

        arrayOf(settings_color_customization_section_label, settings_general_settings_label, settings_drawer_settings_label, settings_home_screen_label).forEach {
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

    private fun setupDrawerColumnCount() {
        val currentColumnCount = config.drawerColumnCount
        settings_drawer_column_count.text = currentColumnCount.toString()
        settings_drawer_column_count_holder.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in 1..MAX_COLUMN_COUNT) {
                items.add(RadioItem(i, resources.getQuantityString(R.plurals.column_counts, i, i)))
            }

            RadioGroupDialog(this, items, currentColumnCount) {
                val newColumnCount = it as Int
                if (currentColumnCount != newColumnCount) {
                    config.drawerColumnCount = newColumnCount
                    setupDrawerColumnCount()
                }
            }
        }
    }

    private fun setupHomeRowCount() {
        val currentRowCount = config.homeRowCount
        settings_home_screen_row_count.text = currentRowCount.toString()
        settings_home_screen_row_count_holder.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in MIN_ROW_COUNT..MAX_ROW_COUNT) {
                items.add(RadioItem(i, resources.getQuantityString(R.plurals.row_counts, i, i)))
            }

            RadioGroupDialog(this, items, currentRowCount) {
                val newRowCount = it as Int
                if (currentRowCount != newRowCount) {
                    config.homeRowCount = newRowCount
                    setupHomeRowCount()
                }
            }
        }
    }

    private fun setupHomeColumnCount() {
        val currentColumnCount = config.homeColumnCount
        settings_home_screen_column_count.text = currentColumnCount.toString()
        settings_home_screen_column_count_holder.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in MIN_COLUMN_COUNT..MAX_COLUMN_COUNT) {
                items.add(RadioItem(i, resources.getQuantityString(R.plurals.column_counts, i, i)))
            }

            RadioGroupDialog(this, items, currentColumnCount) {
                val newColumnCount = it as Int
                if (currentColumnCount != newColumnCount) {
                    config.homeColumnCount = newColumnCount
                    setupHomeColumnCount()
                }
            }
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
