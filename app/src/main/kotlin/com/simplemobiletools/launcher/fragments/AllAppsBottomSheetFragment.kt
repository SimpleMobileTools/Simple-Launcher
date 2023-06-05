package com.simplemobiletools.launcher.fragments

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.PopupMenu
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.adapters.LaunchersAdapter
import com.simplemobiletools.launcher.extensions.*
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_ICON
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_WIDGET
import com.simplemobiletools.launcher.interfaces.AllAppsListener
import com.simplemobiletools.launcher.models.AppLauncher
import com.simplemobiletools.launcher.models.HiddenIcon
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.all_apps_fragment.all_apps_grid
import kotlinx.android.synthetic.main.all_apps_fragment.all_apps_popup_menu_anchor
import kotlinx.android.synthetic.main.all_apps_fragment.view.all_apps_fastscroller
import kotlinx.android.synthetic.main.all_apps_fragment.view.all_apps_grid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AllAppsBottomSheetFragment : Fragment(){
    private var touchDownY = -1
    private var ignoreTouches = false
    private var hasTopPadding = false
    private var cachedLaunchers = arrayListOf<AppLauncher>()
    private var mOpenPopupMenu: PopupMenu? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.all_apps_fragment, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()

        view.all_apps_grid.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }
        view.setOnDragListener { _, event ->
            if(event.action == DragEvent.ACTION_DRAG_STARTED
                || event.action == DragEvent.ACTION_DRAG_ENTERED){
                (requireActivity() as? MainActivity)?.closeAllAppsBottomSheet()
                mOpenPopupMenu?.dismiss()
            }
            return@setOnDragListener true
        }
        loadAndCheckLaunchers()
    }

    override fun onStart() {
        super.onStart()
    }

    fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppLauncher) {
        val gridItem = HomeScreenGridItem(
            null,
            -1,
            -1,
            -1,
            -1,
            -1,
            appLauncher.packageName,
            appLauncher.activityName,
            appLauncher.title,
            ITEM_TYPE_ICON,
            "",
            -1,
            "",
            "",
            null,
            appLauncher.drawable,
        )

        showHomeIconMenu(x, y, gridItem)
        ignoreTouches = true
    }
    private fun setupLayoutManager() {
        val layoutManager = all_apps_grid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = requireContext().getColumnCount()
    }

    private fun setupViews(addTopPadding: Boolean = hasTopPadding) {
        if (activity == null) {
            return
        }

        view?.all_apps_fastscroller?.updateColors(requireContext().getProperPrimaryColor())

        var bottomListPadding = 0
        var leftListPadding = 0
        var rightListPadding = 0

        if (requireActivity().navigationBarOnBottom) {
            bottomListPadding = requireActivity().navigationBarHeight
            leftListPadding = 0
            rightListPadding = 0
        }
        else if (requireActivity().navigationBarOnSide) {
            bottomListPadding = 0

            val display = if (isRPlus()) {
                view?.display!!
            } else {
                (requireActivity().getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }

            if (display.rotation == Surface.ROTATION_90) {
                rightListPadding = requireActivity().navigationBarWidth
            } else if (display.rotation == Surface.ROTATION_270) {
                leftListPadding = requireActivity().navigationBarWidth
            }
        }

        view?.all_apps_grid?.setPadding(0, 0, resources.getDimension(R.dimen.medium_margin).toInt(), bottomListPadding)
        view?.all_apps_fastscroller?.setPadding(leftListPadding, 0, rightListPadding, 0)

        hasTopPadding = addTopPadding
        val topPadding = if (addTopPadding) requireActivity().statusBarHeight else 0
        view?.setPadding(0, topPadding, 0, 0)
        view?.background = ColorDrawable(requireContext().getProperBackgroundColor())
        context?.let { (all_apps_grid?.adapter as? LaunchersAdapter)?.updateTextColor(it.getProperTextColor()) }
    }

    fun onConfigurationChanged() {
        if (view?.all_apps_grid == null) {
            return
        }

        view?.all_apps_grid?.scrollToPosition(0)
        view?.all_apps_fastscroller?.resetManualScrolling()
        setupViews()
        setupLayoutManager()

        val launchers = (all_apps_grid.adapter as LaunchersAdapter).launchers
        setupAdapter(launchers)
    }

    private fun gotLaunchers(appLaunchers: ArrayList<AppLauncher>) {
        Log.i("AABSF", "Got Launchers -> $appLaunchers")
        val sorted = appLaunchers.sortedWith(
            compareBy({
                it.title.normalizeString().lowercase()
            }, {
                it.packageName
            }),
        ).toMutableList() as ArrayList<AppLauncher>

        setupAdapter(sorted)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAdapter(launchers: ArrayList<AppLauncher>) {
        Log.i("AABSF", "Setup Adapter -> $activity")
        activity?.runOnUiThread {
            setupLayoutManager()

            val currAdapter = all_apps_grid.adapter
            if (currAdapter == null) {
                val adapter =  LaunchersAdapter(requireActivity() as MainActivity, launchers,
                    onItemLongPressed = {v, launcher ->
                        onAppLauncherLongPressed(v.x + v.width / 2, v.y, launcher)
                        var longPressFlag = true
                        v.setOnTouchListener { v1, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    // Start a runnable with delay of long press timeout
                                    v1.postDelayed({
                                        longPressFlag = true
                                    }, ViewConfiguration.getLongPressTimeout().toLong())
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    if (longPressFlag) {
                                        // Long press detected and the user moved the view, start drag
                                            startDragging(v, launcher)
                                        // Remove the long press flag
                                        longPressFlag = false
                                    }
                                    true
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    // Remove the long press flag
                                    longPressFlag = false
                                    true
                                }
                                else -> false
                            }
                        }
                    }, itemClick = {
                        activity?.launchApp((it as AppLauncher).packageName, it.activityName)
                        ignoreTouches = false
                        touchDownY = -1
                    })
                all_apps_grid.adapter = adapter
            } else {
                Log.i("AABSF", "Update Items-> $launchers")
                (currAdapter as LaunchersAdapter).updateItems(launchers)
            }
        }
    }
    private fun startDragging(v: View, launcher: AppLauncher){
        val gridItem = HomeScreenGridItem(
            null,
            -1,
            -1,
            -1,
            -1,
            -1,
            launcher.packageName,
            launcher.activityName,
            launcher.title,
            ITEM_TYPE_ICON,
            "",
            -1,
            "",
            "",
            null,
            launcher.drawable,
        )
        val data = ClipData.newPlainText(gridItem.title, gridItem.toString())
        val shadowBuilder = View.DragShadowBuilder(v)
        v.startDragAndDrop(data, shadowBuilder, gridItem, 0)
    }

    fun hideIcon(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            val hiddenIcon = HiddenIcon(null, item.packageName, item.activityName, item.title, null)
            requireContext().hiddenIconsDB.insert(hiddenIcon)
            requireActivity().runOnUiThread{
                (all_apps_grid.adapter as? LaunchersAdapter)?.hideIcon(item)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadAndCheckLaunchers()
    }

    private fun loadLaunchers() {
        lifecycleScope.launch(Dispatchers.IO) {
            val hiddenIcons = requireContext().hiddenIconsDB.getHiddenIcons().map {
                it.getIconIdentifier()
            }

            requireContext().launchersDB.getAppLaunchers().collectLatest {
                cachedLaunchers = it.filter {
                    val showIcon = !hiddenIcons.contains(it.getLauncherIdentifier())
                    if (!showIcon) {
                        try {
                            requireContext().launchersDB.deleteById(it.id!!)
                        } catch (ignored: Exception) {
                        }
                    }
                    showIcon
                }.toMutableList().map {
                    val icon = requireContext().getDrawableForPackageName(it.packageName)
                    it.copy(drawable = icon)
                }
                    as ArrayList<AppLauncher>
            }
        }
    }

    private fun loadAndCheckLaunchers() {
        ensureBackgroundThread {
            loadLaunchers()
            // Fetching the current app launchers
            val currentLaunchers = requireContext().launcherHelper.getAllAppLaunchers()

            var hasDeletedAnything = false
            cachedLaunchers.map { it.packageName }.forEach { packageName ->
                if (!currentLaunchers.map { it.packageName }.contains(packageName)) {
                    hasDeletedAnything = true
                    requireContext().launchersDB.deleteApp(packageName)
                    requireContext().homeScreenGridItemsDB.deleteByPackageName(packageName)
                }
            }

            // If there are deletions, update the UI
            if (hasDeletedAnything) {
                lifecycleScope.launch {
                    setupAdapter(currentLaunchers)
                }
            }

            // Update the cachedLaunchers with the current app launchers
            cachedLaunchers = currentLaunchers
            gotLaunchers(currentLaunchers)
        }
    }

    private fun handleGridItemPopupMenu(anchorView: View, gridItem: HomeScreenGridItem): PopupMenu {
        val visibleMenuButtons = 4

        val yOffset = resources.getDimension(R.dimen.long_press_anchor_button_offset_y) * (visibleMenuButtons - 1)
        anchorView.y -= yOffset

        val contextTheme = ContextThemeWrapper(requireContext(), requireContext().getPopupMenuTheme())
        return PopupMenu(contextTheme, anchorView, Gravity.TOP or Gravity.END).apply {
            if (isQPlus()) {
                setForceShowIcon(true)
            }

            inflate(R.menu.menu_app_icon)
            menu.findItem(R.id.hide_icon).isVisible = true
            menu.findItem(R.id.app_info).isVisible = true
            menu.findItem(R.id.uninstall).isVisible = true
            menu.findItem(R.id.remove).isVisible = false
            menu.findItem(R.id.rename).isVisible = false
            menu.findItem(R.id.resize).isVisible = false



            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.hide_icon -> hideIcon(gridItem)
                    R.id.app_info -> requireActivity().launchAppInfo(gridItem.packageName)
                    R.id.uninstall -> requireActivity().uninstallApp(gridItem.packageName)
                }
                true
            }

            setOnDismissListener {
                mOpenPopupMenu = null
            }

            show()
        }
    }

    private fun showHomeIconMenu(x: Float, y: Float, gridItem: HomeScreenGridItem) {
        all_apps_popup_menu_anchor.x = x
        all_apps_popup_menu_anchor.y = y

        if (mOpenPopupMenu == null) {
            mOpenPopupMenu = handleGridItemPopupMenu(all_apps_popup_menu_anchor, gridItem)
        }
    }
}
