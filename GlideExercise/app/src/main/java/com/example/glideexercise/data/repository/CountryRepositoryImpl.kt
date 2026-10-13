package com.example.glideexercise.data.repository

import com.example.glideexercise.data.datasource.CountryLocalDataSource
import com.example.glideexercise.data.model.Country

class CountryRepositoryImpl(
    private val dataSource: CountryLocalDataSource
) : CountryRepository {
    override suspend fun getCountries(): List<Country> {
        return dataSource.getCountries()
    }

    override suspend fun getCountryById(id: Int): Country? {
        return dataSource.getCountryById(id)
    }
}