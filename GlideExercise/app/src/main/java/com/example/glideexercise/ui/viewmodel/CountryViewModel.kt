package com.example.glideexercise.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.glideexercise.data.model.Country
import com.example.glideexercise.data.repository.CountryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CountryViewModel(
    private val repository: CountryRepository
) : ViewModel() {
    private val _countries = MutableLiveData<List<Country>>()
    private val _country = MutableLiveData<Country?>()

    val countries: LiveData<List<Country>> = _countries
    val country: LiveData<Country?> = _country

    init {
        loadCountries()
    }

    private fun loadCountries() {
        viewModelScope.launch(Dispatchers.IO){
            val countries = repository.getCountries()
            _countries.postValue(countries)
        }
    }

    fun getCountryById(id: Int){
        viewModelScope.launch(Dispatchers.IO){
            val country = repository.getCountryById(id)
            _country.postValue(country)
        }
    }
    fun getCountryAtPosition(position: Int):Country?{
        return countries.value?.get(position)
    }
    class Factory(private val repository: CountryRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CountryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CountryViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}