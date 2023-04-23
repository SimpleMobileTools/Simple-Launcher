package com.simplemobiletools.launcher.adapters

import android.util.SparseArray
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.simplemobiletools.launcher.fragments.HomeScreenFragment
import com.simplemobiletools.launcher.models.PageWithGridItems

class HomeScreenPagerAdapter(fm: FragmentManager) :
    FragmentStatePagerAdapter(fm) {
    private var homePages: ArrayList<PageWithGridItems> = arrayListOf()
    private val registeredFragments = SparseArray<HomeScreenFragment>()

    fun setHomePages(pages: ArrayList<PageWithGridItems> ){
        homePages = pages
    }

    fun getFragmentAt(position: Int): HomeScreenFragment? {
        return registeredFragments.get(position)
    }
    fun updateItems(newItems: List<PageWithGridItems>) {
        homePages.clear()
        homePages.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return homePages.size
    }

    override fun getItem(position: Int): Fragment {
        val homePage = homePages[position]
        val homeFragment = HomeScreenFragment.newInstance(homePage)
        registeredFragments.put(position, homeFragment)
        return homeFragment
    }
}
