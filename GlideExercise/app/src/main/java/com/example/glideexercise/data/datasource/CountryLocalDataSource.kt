package com.example.glideexercise.data.datasource

import com.example.glideexercise.data.model.Country

interface CountryLocalDataSource {
    suspend fun getCountries(): List<Country>
    suspend fun getCountryById(id: Int): Country?
}