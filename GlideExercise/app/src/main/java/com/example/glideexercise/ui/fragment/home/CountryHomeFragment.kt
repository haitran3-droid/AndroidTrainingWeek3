package com.example.glideexercise.ui.fragment.home;


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.example.glideexercise.R
import com.example.glideexercise.app.CountryApp
import com.example.glideexercise.data.model.Country
import com.example.glideexercise.databinding.FragmentCountryHomeBinding
import com.example.glideexercise.ui.adapter.CountryListAdapter
import com.example.glideexercise.ui.fragment.pager.CountryPagerFragment
import com.example.glideexercise.ui.fragment.pager.CountryPagerFragment.Companion.BUNDLE_KEY_SELECTED_COUNTRY_ID
import com.example.glideexercise.ui.viewmodel.CountryViewModel
import kotlin.getValue

class CountryHomeFragment : Fragment() {
    private var binding: FragmentCountryHomeBinding? = null
    private lateinit var countryListAdapter: CountryListAdapter
    private val viewModel: CountryViewModel by activityViewModels {
        val repository = (requireActivity().application as CountryApp).repository
        CountryViewModel.Factory(repository)
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
            binding = FragmentCountryHomeBinding.inflate(inflater, container, false)
    return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()
        observeCountries()
    }

    private fun initRecyclerView() {
        countryListAdapter =
                CountryListAdapter(object : CountryListAdapter.OnCountryItemClickListener {
            override fun onCountryItemClick(country: Country) {
                val bundle = Bundle().apply {
                    putInt(BUNDLE_KEY_SELECTED_COUNTRY_ID, country.countryId)
                }
                requireActivity().supportFragmentManager
                        .beginTransaction()
                        .setCustomAnimations(
                                R.anim.fade_in,
                                R.anim.fade_out,
                                R.anim.fade_in,
                                R.anim.fade_out
                        )
                        .replace(
                                R.id.fcv_country,
                                CountryPagerFragment()::class.java,
                        bundle
                        )
                        .addToBackStack(null)
                        .commit()
            }
        })
        binding?.rvCountry?.adapter = countryListAdapter
        // bổ sung đường kẻ ngang phân tách các phần tử trong recyclerview
        val divider = DividerItemDecoration(requireContext(), RecyclerView.VERTICAL)
        val drawable = ResourcesCompat.getDrawable(resources, R.drawable.divider, null)
        drawable?.let {
            divider.setDrawable(it)
        }
        binding?.rvCountry?.addItemDecoration(divider)
    }

    private fun observeCountries() {
        viewModel.countries.observe(viewLifecycleOwner) { countries ->
                countryListAdapter.submitList(countries)
        }
    }
}
