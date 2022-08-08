package com.simplemobiletools.launcher.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.adapters.LaunchersAdapter
import com.simplemobiletools.launcher.models.AppLauncher
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        getLaunchers()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(main_toolbar)
        updateTextColors(main_coordinator)
        launchers_fastscroller.updateColors(getProperPrimaryColor())
    }

    private fun setupOptionsMenu() {
        main_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    @SuppressLint("WrongConstant")
    private fun getLaunchers() {
        val allApps = ArrayList<AppLauncher>()
        val allPackageNames = ArrayList<String>()
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val list = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
        for (info in list) {
            val componentInfo = info.activityInfo.applicationInfo
            val label = componentInfo.loadLabel(packageManager).toString()
            val packageName = componentInfo.packageName

            var drawable: Drawable? = null
            try {
                // try getting the properly colored launcher icons
                val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val activityList = launcher.getActivityList(packageName, android.os.Process.myUserHandle())[0]
                drawable = activityList.getBadgedIcon(0)
            } catch (e: Exception) {
            } catch (e: Error) {
            }

            if (drawable == null) {
                drawable = try {
                    packageManager.getApplicationIcon(packageName)
                } catch (ignored: Exception) {
                    continue
                }
            }

            allPackageNames.add(packageName)
            allApps.add(AppLauncher(0, label, packageName, 0, drawable))
        }

        val launchers = allApps.distinctBy { it.packageName } as ArrayList<AppLauncher>
        launchers.sortBy { it.title.toLowerCase() }

        val layoutManager = launchers_grid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = if (portrait) {
            resources.getInteger(R.integer.portrait_column_count)
        } else {
            resources.getInteger(R.integer.landscape_column_count)
        }

        setupAdapter(launchers)
    }

    private fun setupAdapter(launchers: ArrayList<AppLauncher>) {
        LaunchersAdapter(this, launchers) {

        }.apply {
            launchers_grid.adapter = this
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
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
