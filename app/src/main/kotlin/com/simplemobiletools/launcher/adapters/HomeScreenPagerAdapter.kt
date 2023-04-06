package com.simplemobiletools.launcher.adapters

import android.util.SparseArray
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.simplemobiletools.launcher.fragments.HomeScreenFragment
import com.simplemobiletools.launcher.models.PageWithGridItems

class HomeScreenPagerAdapter(fragmentActivity: FragmentActivity, private val homePages: ArrayList<PageWithGridItems>) :
    FragmentStateAdapter(fragmentActivity) {
    private val registeredFragments = SparseArray<HomeScreenFragment>()

    override fun getItemId(position: Int): Long {
        return homePages[position].page?.id ?: -1
    }

    override fun containsItem(itemId: Long): Boolean {
        return homePages.any { it.page?.id == itemId }
    }

    override fun getItemCount(): Int {
        return homePages.size
    }

    override fun createFragment(position: Int): Fragment {
        val homePage = homePages[position]
        val homeFragment = HomeScreenFragment.newInstance(homePage)
        registeredFragments.put(position, homeFragment)
        return homeFragment
    }
    fun getFragmentAt(position: Int): HomeScreenFragment? {
        return registeredFragments.get(position)
    }
    fun updateItems(newItems: List<PageWithGridItems>) {
        homePages.clear()
        homePages.addAll(newItems)
        notifyDataSetChanged()
    }
}
