package com.example.glideexercise.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.glideexercise.data.model.Country
import com.example.glideexercise.databinding.ItemCountryBinding
import com.example.glideexercise.ui.mapping.FlagMapping

class CountryListAdapter(
    private val listener: OnCountryItemClickListener
) : RecyclerView.Adapter<CountryListAdapter.CountryViewHolder>() {
    private val countries = mutableListOf<Country>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CountryViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ItemCountryBinding.inflate(layoutInflater, parent, false)
        return CountryViewHolder(binding, listener)
    }

    override fun onBindViewHolder(
        holder: CountryViewHolder,
        position: Int
    ) {
        holder.bind(countries[position])
    }

    override fun getItemCount(): Int {
        return countries.size
    }

    fun submitList(newCountries: List<Country>) {
        val diffCallback = CountryDiffCallback(countries, newCountries)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        countries.clear()
        countries.addAll(newCountries)
        diffResult.dispatchUpdatesTo(this)
    }

    class CountryViewHolder(
        private val binding: ItemCountryBinding,
        private val listener: OnCountryItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(country: Country) {
            binding.tvNameItem.text = country.countryName
            binding.tvCapitalItem.text = country.countryCapital
            val flagResId = FlagMapping.getResIdByFileName(country.countryFlag)
            Glide.with(binding.root)
                .load(flagResId)
                .into(binding.ivFlagItem)
            binding.root.setOnClickListener {
                listener.onCountryItemClick(country)
            }
        }
    }

    class CountryDiffCallback(
        private val oldList: List<Country>,
        private val newList: List<Country>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].countryId == newList[newItemPosition].countryId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    interface OnCountryItemClickListener {
        fun onCountryItemClick(country: Country)
    }
}