package com.simplemobiletools.launcher.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.viewpager.widget.PagerAdapter
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.models.PageWithGridItems
import com.simplemobiletools.launcher.views.HomeScreenGrid

class HomeScreenPagerAdapter(
    private val context: Context,
    private val addNewPage: (Int) -> Unit = {}
) : PagerAdapter() {
    private var _pages: ArrayList<PageWithGridItems> = arrayListOf()

    fun setPages(pages: ArrayList<PageWithGridItems>) {
        _pages.clear()
        _pages.addAll(pages + PageWithGridItems(null, emptyList(), isAddNewPageIndicator = true))
        notifyDataSetChanged()
    }
    override fun getCount(): Int = _pages.size

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        if (_pages.isEmpty()) return Any()
        val page = _pages[position]

        return if (page.isAddNewPageIndicator) {
            Log.i("SM-LAUNCHER", "pos: $position, pages: $count")
            // Inflate a layout with a button for adding a new page
            val addNewPageView = LayoutInflater.from(context).inflate(R.layout.add_new_page_layout, container, false)
            addNewPageView.findViewById<Button>(R.id.add_new_page_button).setOnClickListener {
                addNewPage(position)
            }
            container.addView(addNewPageView)
            addNewPageView // Return the view object
        } else {
            val homeScreenGrid: HomeScreenGrid = HomeScreenGrid(context).apply {
                setHomeScreenPage(page)
                fetchGridItems()
            }
            container.addView(homeScreenGrid)
            homeScreenGrid // Return the view object
        }
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }
}
