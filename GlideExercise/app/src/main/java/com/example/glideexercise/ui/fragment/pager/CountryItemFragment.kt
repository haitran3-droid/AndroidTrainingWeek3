package com.example.glideexercise.ui.fragment.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.example.glideexercise.R
import com.example.glideexercise.app.CountryApp
import com.example.glideexercise.databinding.FragmentCountryPagerItemBinding
import com.example.glideexercise.ui.mapping.FlagMapping
import com.example.glideexercise.ui.viewmodel.CountryViewModel

class CountryItemFragment : Fragment() {
    private var binding: FragmentCountryPagerItemBinding? = null
    private val viewModel: CountryViewModel by viewModels {
        val repository = (requireActivity().application as CountryApp).repository
        CountryViewModel.Factory(repository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCountryPagerItemBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCountryFromArgs()
        observeCountry()
    }

    private fun loadCountryFromArgs() {
        val countryId = arguments?.getInt(BUNDLE_KEY_COUNTRY_ID)
        if (countryId != null) {
            viewModel.getCountryById(countryId)
        }
    }

    private fun observeCountry() {
        viewModel.country.observe(viewLifecycleOwner) { country ->
            if (country != null) {
                val flagResId = FlagMapping.getResIdByFileName(country.countryFlag)
                Glide.with(this)
                    .load(flagResId)
                    .into(binding?.imgFlagDetail!!)
                binding?.tvCountryName?.text = getString(R.string.text_nation, country.countryName)
                binding?.tvCapital?.text = getString(R.string.text_capital, country.countryCapital)
                binding?.tvPopulation?.text =
                    getString(R.string.text_population, country.countryPopulation)
                binding?.tvArea?.text = getString(R.string.text_area, country.countryArea)
                binding?.tvDensity?.text = getString(R.string.text_density, country.countryDensity)
                binding?.tvWorldShare?.text =
                    getString(R.string.text_world_share, country.countryWorldShare)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        const val BUNDLE_KEY_COUNTRY_ID = "pro.branium.country_id"
    }
}