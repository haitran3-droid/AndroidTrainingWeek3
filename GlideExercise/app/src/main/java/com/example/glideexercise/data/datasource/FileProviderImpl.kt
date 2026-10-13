package com.example.glideexercise.data.datasource

import android.content.Context
import java.io.InputStream

class FileProviderImpl(
    private val context: Context
) : FileProvider {
    override fun openFileToRead(fileName: String): InputStream {
        return context.assets.open(fileName)
    }
}