package com.example.glideexercise.ui.adapter

import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.glideexercise.data.model.Country
import com.example.glideexercise.ui.fragment.pager.CountryItemFragment
import com.example.glideexercise.ui.fragment.pager.CountryItemFragment.Companion.BUNDLE_KEY_COUNTRY_ID

class CountryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    private val listAdapter =
        object : ListAdapter<Country, RecyclerView.ViewHolder>(DIFF_CALLBACK) {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
            ): RecyclerView.ViewHolder {
                throw UnsupportedOperationException("Not used")
            }

            override fun onBindViewHolder(
                holder: RecyclerView.ViewHolder,
                position: Int
            ) {
            }
        }

    override fun createFragment(position: Int): Fragment {
        val fragment = CountryItemFragment()
        val args = Bundle()
        val country = listAdapter.currentList[position]
        if (country != null) {
            args.putInt(BUNDLE_KEY_COUNTRY_ID, country.countryId)
        }
        fragment.arguments = args
        return fragment
    }

    override fun getItemCount(): Int {
        return listAdapter.currentList.size
    }

    override fun getItemId(position: Int): Long {
        val country = listAdapter.currentList[position]
        return country.countryId.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return listAdapter.currentList
            .map { it.countryId.toLong() }
            .any { it == itemId }
    }

    fun submitList(newList: List<Country>) {
        listAdapter.submitList(newList)
    }

    fun getIndexById(countryId: Int): Int {
        return listAdapter.currentList.indexOfFirst { it.countryId == countryId }
    }
    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Country>() {
            override fun areItemsTheSame(oldItem: Country, newItem: Country): Boolean {
                return oldItem.countryId == newItem.countryId
            }

            override fun areContentsTheSame(oldItem: Country, newItem: Country): Boolean {
                return oldItem == newItem
            }
        }
    }
}