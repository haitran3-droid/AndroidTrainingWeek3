package com.example.glideexercise.data.repository

import com.example.glideexercise.data.model.Country

interface CountryRepository {
    suspend fun getCountries(): List<Country>
    suspend fun getCountryById(id: Int): Country?
}