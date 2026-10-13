package com.example.glideexercise.data.datasource

import java.io.InputStream

interface FileProvider {
    fun openFileToRead(fileName: String): InputStream
}