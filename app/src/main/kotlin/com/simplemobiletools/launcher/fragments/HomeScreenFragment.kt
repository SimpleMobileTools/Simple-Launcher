package com.simplemobiletools.launcher.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.homeScreenPagesDB
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import com.simplemobiletools.launcher.models.HomeScreenPage
import com.simplemobiletools.launcher.models.PageWithGridItems
import com.simplemobiletools.launcher.views.HomeScreenGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeScreenFragment : Fragment() {
    companion object {
        private const val ARG_PAGE = "page"

        fun newInstance(page: PageWithGridItems): HomeScreenFragment {
            val args = Bundle()
            args.putParcelable(ARG_PAGE, page)
            val fragment = HomeScreenFragment()
            fragment.arguments = args
            return fragment
        }
    }
    var homeScreenGrid: HomeScreenGrid? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val page = arguments?.get(ARG_PAGE) as PageWithGridItems
        Log.i("HOMEVP", "Creating ViewPager, ID:$page")

        return if (page.isAddNewPageIndicator) {
            // Inflate a layout with a button for adding a new page
            inflater.inflate(R.layout.add_new_page_layout, container, false)
        } else {
            inflater.inflate(R.layout.item_homepage, container, false)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val page = arguments?.get(ARG_PAGE) as PageWithGridItems
        view.findViewById<Button>(R.id.add_new_page_button)?.setOnClickListener {
            addNewPage(page.page?.position ?: return@setOnClickListener)
        }
        homeScreenGrid = view.findViewById(R.id.home_screen_grid)
        homeScreenGrid?.setHomeScreenPage(page)
        homeScreenGrid?.fetchGridItems()
//        homeScreenGrid?.setOnTouchListener { _, event ->
//            when (event.actionMasked) {
//                MotionEvent.ACTION_MOVE -> {
//                    val dx = event.x - mLastX
//                    val dy = event.y - mLastY
//                    if (abs(dx) > abs(dy)) {
//                        // Horizontal swipe, allow the ViewPager to intercept touch events
//                        Log.d("HSF", "Horizontal Swipe")
//                        return@setOnTouchListener false
//                    }
//                    Log.d("HSF", "Vertical Swipe")
//                    // Vertical swipe, pass touch events to MainActivity
//                    listener?.onFragmentTouchEvent(event)
//                    return@setOnTouchListener true
//                }
//                else -> {
//                    return@setOnTouchListener false
//                }
//            }
//        }
    }
    private fun addNewPage(position: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            requireContext().homeScreenPagesDB.insert(HomeScreenPage(position = position))
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
        if (context is OnUpdateAdapterListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnUpdateAdapterListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    fun storeAndShowGridItem(gridItem: HomeScreenGridItem) {
        homeScreenGrid?.storeAndShowGridItem(gridItem)
    }

    fun draggedItemMoved(x: Int, y: Int) {
        homeScreenGrid?.draggedItemMoved(x, y)
    }

    fun itemDraggingStarted(mLongPressedIcon: HomeScreenGridItem) {
        homeScreenGrid?.itemDraggingStarted(mLongPressedIcon)
    }

    fun itemDraggingStopped() {
        homeScreenGrid?.itemDraggingStopped()
    }

    private var listener: OnUpdateAdapterListener? = null
    interface OnUpdateAdapterListener {
        fun onUpdateAdapter()
        fun onFragmentTouchEvent(event: MotionEvent?)
    }
}
