package com.simplemobiletools.launcher.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherApps
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.PopupMenu
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.simplemobiletools.commons.extensions.getPopupMenuTheme
import com.simplemobiletools.commons.extensions.performHapticFeedback
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.dialogs.RenameItemDialog
import com.simplemobiletools.launcher.extensions.*
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_ICON
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_SHORTCUT
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_WIDGET
import com.simplemobiletools.launcher.interfaces.FlingListener
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import com.simplemobiletools.launcher.models.HomeScreenPage
import com.simplemobiletools.launcher.models.PageWithGridItems
import com.simplemobiletools.launcher.views.HomeScreenGrid
import kotlinx.android.synthetic.main.item_homepage.home_screen_popup_menu_anchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class HomeScreenFragment : Fragment(), FlingListener {
    companion object {
        private const val ARG_PAGE = "page"

        fun newInstance(page: PageWithGridItems): HomeScreenFragment {
            val args = Bundle()
            args.putParcelable(ARG_PAGE, page)
            val fragment = HomeScreenFragment()
            fragment.arguments = args
            return fragment
        }
        private var mLastUpEvent = 0L
    }

    var homeScreenGrid: HomeScreenGrid? = null
    private var mLongPressedIcon: HomeScreenGridItem? = null
    private var mOpenPopupMenu: PopupMenu? = null

    private lateinit var mDetector: GestureDetectorCompat
    private var mTouchDownX = -1
    private var mTouchDownY = -1

    private var mMoveGestureThreshold = 0
    private var isIndicatorPage = false
    private var currentPage: PageWithGridItems? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val page = arguments?.get(ARG_PAGE) as PageWithGridItems
        Log.i("HOMEVP", "Creating ViewPager, ID:$page")

        return if (page.isAddNewPageIndicator) {
            isIndicatorPage = true
            // Inflate a layout with a button for adding a new page
            inflater.inflate(R.layout.add_new_page_layout, container, false)
        } else {
            inflater.inflate(R.layout.item_homepage, container, false)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentPage = arguments?.get(ARG_PAGE) as PageWithGridItems

        view.findViewById<Button>(R.id.add_new_page_button)?.setOnClickListener {
            addNewPage(currentPage?.page?.position ?: return@setOnClickListener)
        }
        if(!isIndicatorPage) {
            homeScreenGrid = view.findViewById(R.id.home_screen_grid)
            homeScreenGrid?.setHomeScreenPage(currentPage!!)
            homeScreenGrid?.fetchGridItems()
            homeScreenGrid?.setWidgetResizeListener(object : HomeScreenGrid.OnWidgetResizeListener{
                override fun resizeStarted() {
                    listener?.onWidgetResizeStarted()
                }

                override fun resizeEnded() {
                   listener?.onWidgetResizeStopped()
                }

            })
            view.setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        Log.i("HOMEVP", "Drag Entered On Page:$currentPage")
                        val draggedData = event.localState
                        if (draggedData is HomeScreenGridItem) {
                            homeScreenGrid?.itemDraggingStarted(draggedData.copy(pageId = currentPage?.page?.id ?: -1))
                            listener?.onDragStarted()
                        }
                    }

                    DragEvent.ACTION_DRAG_ENDED -> {
                        Log.i("HOMEVP", "Drag Ended On Page:$currentPage")
                        homeScreenGrid?.itemDraggingStopped()
                        listener?.onDragStopped()
                    }

                    DragEvent.ACTION_DROP -> {
                        Log.i("HOMEVP", "Drag Dropped On Page:$currentPage")
                    }

                    DragEvent.ACTION_DRAG_LOCATION -> {
                        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
                        val edgeThreshold = 50  // You can adjust this value as needed

                        // If the drag event reaches the edge of the screen on the right side
                        if (event.x > screenWidth - edgeThreshold) {
                            // Go to next page if exists
                            listener?.gotoNextPage(currentPage?.page?.position ?: 0)
                        }
                        // If the drag event reaches the edge of the screen on the left side
                        else if (event.x < edgeThreshold) {
                            // Go to previous page if exists
                            listener?.gotoPrevPage(currentPage?.page?.position ?: 0)
                        }

                        homeScreenGrid?.draggedItemMoved(event.x.toInt(), event.y.toInt(), false)
                    }
                }
                return@setOnDragListener true
            }

            mMoveGestureThreshold = resources.getDimension(R.dimen.move_gesture_threshold).toInt()
            mDetector = GestureDetectorCompat(requireContext(), MyGestureListener(this))
            view.setOnTouchListener { _, event ->
                if (mLongPressedIcon != null
                    && event.actionMasked == MotionEvent.ACTION_UP
                    || event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    mLastUpEvent = System.currentTimeMillis()
                }

                runCatching {
                    mDetector.onTouchEvent(event)
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        mTouchDownX = event.x.toInt()
                        mTouchDownY = event.y.toInt()
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // if the initial gesture was handled by some other view, fix the Down values
                        val hasFingerMoved = if (mTouchDownX == -1 || mTouchDownY == -1) {
                            mTouchDownX = event.x.toInt()
                            mTouchDownY = event.y.toInt()
                            false
                        } else {
                            hasFingerMoved(event)
                        }

                        if (mLongPressedIcon != null && mOpenPopupMenu != null && hasFingerMoved) {
                            mOpenPopupMenu?.dismiss()
                            mOpenPopupMenu = null
                            homeScreenGrid?.itemDraggingStarted(mLongPressedIcon!!)
                            listener?.onDragStarted()
                        }

                        if (mLongPressedIcon != null && hasFingerMoved) {
                            homeScreenGrid?.draggedItemMoved(event.x.toInt(), event.y.toInt())
                        }
                    }

                    MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_UP,
                    -> {
                        mTouchDownX = -1
                        mTouchDownY = -1
                        mLongPressedIcon = null
                        homeScreenGrid?.itemDraggingStopped()
                        listener?.onDragStopped()
                    }
                }
                true
            }
        }
    }
    //     some devices ACTION_MOVE keeps triggering for the whole long press duration, but we are interested in real moves only, when coords change
    private fun hasFingerMoved(event: MotionEvent) = mTouchDownX != -1 && mTouchDownY != -1 &&
        (abs(mTouchDownX - event.x) > mMoveGestureThreshold) || (abs(mTouchDownY - event.y) > mMoveGestureThreshold)

    private fun addNewPage(position: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            requireContext().homeScreenPagesDB.insert(HomeScreenPage(position = position))
            withContext(Dispatchers.Main) {
                listener?.onUpdateAdapter()
            }
        }
    }
    private fun removePage(pageId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            requireContext().homeScreenPagesDB.delete(pageId)
            withContext(Dispatchers.Main) {
                listener?.onUpdateAdapter()
            }
        }
    }
    override fun onResume() {
        super.onResume()
        view?.findViewById<HomeScreenGrid>(R.id.home_screen_grid)?.appWidgetHost?.startListening()
    }

    override fun onPause() {
        super.onPause()
        view?.findViewById<HomeScreenGrid>(R.id.home_screen_grid)?.appWidgetHost?.stopListening()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is HomeScreenActionsListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnUpdateAdapterListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
    fun homeScreenLongPressed(x: Float, y: Float) {
        val clickedGridItem = homeScreenGrid?.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            if (clickedGridItem.type == ITEM_TYPE_ICON || clickedGridItem.type == ITEM_TYPE_SHORTCUT) {
                view?.performHapticFeedback()
            }

            val anchorY = homeScreenGrid!!.sideMargins.top + (clickedGridItem.top * homeScreenGrid!!.cellHeight.toFloat())
            showHomeIconMenu(x, anchorY, clickedGridItem)
            return
        }

        view?.performHapticFeedback()
        showMainLongPressMenu(x, y)
    }
    fun homeScreenClicked(x: Float, y: Float) {
        homeScreenGrid?.hideResizeLines()
        val clickedGridItem = homeScreenGrid?.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            if (clickedGridItem.type == ITEM_TYPE_ICON) {
                requireActivity().launchApp(clickedGridItem.packageName, clickedGridItem.activityName)
            } else if (clickedGridItem.type == ITEM_TYPE_SHORTCUT) {
                if (clickedGridItem.intent.isNotEmpty()) {
                    requireActivity().launchShortcutIntent(clickedGridItem)
                } else {
                    // launch pinned shortcuts
                    val id = clickedGridItem.shortcutId
                    val packageName = clickedGridItem.packageName
                    val userHandle = android.os.Process.myUserHandle()
                    val shortcutBounds = homeScreenGrid?.getClickableRect(clickedGridItem)
                    val launcherApps = requireContext().applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    launcherApps.startShortcut(packageName, id, shortcutBounds, null, userHandle)
                }
            }
        }
    }
    fun showHomeIconMenu(x: Float, y: Float, gridItem: HomeScreenGridItem) {
        mLongPressedIcon = gridItem
        homeScreenGrid?.hideResizeLines()
        val anchorY = if (gridItem.type == ITEM_TYPE_WIDGET) {
            y
        } else if (gridItem.top == requireContext().config.rowCount - 1) {
            homeScreenGrid!!.sideMargins.top + (gridItem.top * homeScreenGrid!!.cellHeight.toFloat())
        } else {
            (gridItem.top * homeScreenGrid!!.cellHeight.toFloat())
        }

        home_screen_popup_menu_anchor.x = x
        home_screen_popup_menu_anchor.y = anchorY

        if (mOpenPopupMenu == null) {
            mOpenPopupMenu = handleGridItemPopupMenu(home_screen_popup_menu_anchor, gridItem)
        }
    }
    fun showMainLongPressMenu(x: Float, y: Float) {
        homeScreenGrid?.hideResizeLines()
        home_screen_popup_menu_anchor.x = x
        home_screen_popup_menu_anchor.y = y - resources.getDimension(R.dimen.long_press_anchor_button_offset_y) * 2
        val contextTheme = ContextThemeWrapper(requireContext(), requireContext().getPopupMenuTheme())
        PopupMenu(contextTheme, home_screen_popup_menu_anchor, Gravity.TOP or Gravity.END).apply {
            inflate(R.menu.menu_home_screen)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.widgets -> listener?.showWidgetsFragment()
                    R.id.wallpapers -> listener?.launchWallpapersIntent()
                    R.id.remove -> removePage(currentPage?.page?.id!!)
                }
                true
            }
            show()
        }
    }
    private fun handleGridItemPopupMenu(anchorView: View, gridItem: HomeScreenGridItem): PopupMenu {
        var visibleMenuButtons = 6
        visibleMenuButtons -= when (gridItem.type) {
            ITEM_TYPE_ICON -> 1
            ITEM_TYPE_WIDGET -> 3
            else -> 4
        }

        if (gridItem.type != ITEM_TYPE_WIDGET) {
            visibleMenuButtons--
        }

        val yOffset = resources.getDimension(R.dimen.long_press_anchor_button_offset_y) * (visibleMenuButtons - 1)
        anchorView.y -= yOffset

        val contextTheme = ContextThemeWrapper(requireContext(), requireContext().getPopupMenuTheme())
        return PopupMenu(contextTheme, anchorView, Gravity.TOP or Gravity.END).apply {
            if (isQPlus()) {
                setForceShowIcon(true)
            }

            inflate(R.menu.menu_app_icon)
            menu.findItem(R.id.rename).isVisible = gridItem.type == ITEM_TYPE_ICON
            menu.findItem(R.id.hide_icon).isVisible = false
            menu.findItem(R.id.resize).isVisible = gridItem.type == ITEM_TYPE_WIDGET
            menu.findItem(R.id.app_info).isVisible = gridItem.type == ITEM_TYPE_ICON
            menu.findItem(R.id.uninstall).isVisible = gridItem.type == ITEM_TYPE_ICON
            menu.findItem(R.id.remove).isVisible = true
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.rename -> renameItem(gridItem)
                    R.id.resize -> homeScreenGrid?.widgetLongPressed(gridItem)
                    R.id.app_info -> requireActivity().launchAppInfo(gridItem.packageName)
                    R.id.remove -> homeScreenGrid?.removeAppIcon(gridItem)
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
    private fun renameItem(homeScreenGridItem: HomeScreenGridItem) {
        RenameItemDialog(requireActivity(), homeScreenGridItem) {
            homeScreenGrid?.fetchGridItems()
        }
    }

    fun storeAndShowGridItem(gridItem: HomeScreenGridItem) {
        homeScreenGrid?.storeAndShowGridItem(gridItem)
    }

    private var listener: HomeScreenActionsListener? = null

    interface HomeScreenActionsListener {
        fun onUpdateAdapter()
        fun onFlingDown()
        fun onFlingUp()
        fun showWidgetsFragment()
        fun launchWallpapersIntent()
        fun gotoNextPage(i: Int)
        fun gotoPrevPage(i: Int)
        fun onDragStarted()
        fun onDragStopped()
        fun onWidgetResizeStarted()
        fun onWidgetResizeStopped()
    }
    inner class MyGestureListener(private val flingListener: FlingListener) : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            homeScreenClicked(event.x, event.y)
            return super.onSingleTapUp(event)
        }

        override fun onFling(event1: MotionEvent, event2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            // ignore fling events just after releasing an icon from dragging
            if (System.currentTimeMillis() - mLastUpEvent < 500L) {
                return true
            }
            if (abs(velocityY) > abs(velocityX)) {
                if (velocityY > 0) {
                    flingListener.onFlingDown()
                } else {
                    flingListener.onFlingUp()
                }
            }
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            homeScreenLongPressed(event.x, event.y)
        }
    }

    override fun onFlingUp() {
        listener?.onFlingUp()
    }

    override fun onFlingDown() {
        listener?.onFlingDown()
    }
}
