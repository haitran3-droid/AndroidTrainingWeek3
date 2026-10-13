package com.example.glideexercise.ui.fragment.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.glideexercise.app.CountryApp
import com.example.glideexercise.databinding.FragmentCountryPagerBinding
import com.example.glideexercise.ui.adapter.CountryPagerAdapter
import com.example.glideexercise.ui.viewmodel.CountryViewModel
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.getValue

class CountryPagerFragment : Fragment() {
    private var binding: FragmentCountryPagerBinding? = null
    private lateinit var adapter: CountryPagerAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private var isTabLayoutMediatorAttached = false
    private var selectedCountryId: Int = -1
    private val viewModel: CountryViewModel by activityViewModels {
        val repository = (requireActivity().application as CountryApp).repository
        CountryViewModel.Factory(repository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCountryPagerBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewPager()
        initViews()
        observeCountries()
    }

    private fun initViewPager() {
        adapter = CountryPagerAdapter(this)
        binding?.countryViewPager?.adapter = adapter
    }

    private fun initViews() {
        binding?.toolbar?.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun initTabLayoutMediator() {
        tabLayoutMediator = TabLayoutMediator(
            binding?.countryTabLayout!!,
            binding?.countryViewPager!!
        ) { tab, position ->
            val country = viewModel.getCountryAtPosition(position)
            country?.let {
                tab.text = it.countryName
            }
        }
        tabLayoutMediator.attach()
        isTabLayoutMediatorAttached = true
    }

    private fun observeCountries() {
        viewModel.countries.observe(viewLifecycleOwner) { countries ->
            adapter.submitList(countries)
            if (!isTabLayoutMediatorAttached) {
                initTabLayoutMediator()
            } else {
                for (i in 0 until adapter.itemCount) {
                    val tab = binding?.countryTabLayout?.getTabAt(i)
                    val country = viewModel.getCountryAtPosition(i)
                    country?.let {
                        tab?.text = it.countryName
                    }
                }
            }
            setSelectedCountry()
        }
    }
    private fun setSelectedCountry() {
        selectedCountryId = arguments?.getInt(BUNDLE_KEY_SELECTED_COUNTRY_ID) ?: -1
        if (selectedCountryId > -1) {
            val index = adapter.getIndexById(selectedCountryId)
            binding?.countryViewPager?.post {
                binding?.countryViewPager?.currentItem = index
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        if (isTabLayoutMediatorAttached) {
            tabLayoutMediator.detach()
        }
    }

    companion object {
        const val BUNDLE_KEY_SELECTED_COUNTRY_ID = "pro.branium.selected_country_id"
    }
}