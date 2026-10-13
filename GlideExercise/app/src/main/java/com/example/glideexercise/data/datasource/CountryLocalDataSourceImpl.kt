package com.example.glideexercise.data.datasource

import com.example.glideexercise.data.model.Country
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.FileNotFoundException

class CountryLocalDataSourceImpl (
    private val fileProvider: FileProvider,
    private val gson: Gson
): CountryLocalDataSource{
    override suspend fun getCountries(): List<Country> {
        return try{
            val inputStream = fileProvider.openFileToRead(COUNTRIES_FILE_NAME)
            inputStream.bufferedReader().use{reader ->
                gson.fromJson(
                    reader,
                    object : TypeToken<List<Country>>() {}.type
                )?: emptyList()
            }
        }catch (_: FileNotFoundException){emptyList()}
    }

    override suspend fun getCountryById(id: Int): Country? {
        val countries = getCountries()
        return countries.find{it.countryId == id}
    }

    companion object{
        const val COUNTRIES_FILE_NAME = "countries.json"
    }
}