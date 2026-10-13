package com.example.glideexercise.app

import android.app.Application
import com.example.glideexercise.data.datasource.CountryLocalDataSourceImpl
import com.google.gson.Gson
import com.example.glideexercise.data.datasource.FileProviderImpl
import com.example.glideexercise.data.repository.CountryRepository
import com.example.glideexercise.data.repository.CountryRepositoryImpl

class CountryApp: Application() {
    internal lateinit var repository: CountryRepository
    override fun onCreate() {
        super.onCreate()
        val fileProvider = FileProviderImpl(this)
        val gson = Gson()
        val dataSource = CountryLocalDataSourceImpl(fileProvider, gson)
        repository = CountryRepositoryImpl(dataSource)
    }
}