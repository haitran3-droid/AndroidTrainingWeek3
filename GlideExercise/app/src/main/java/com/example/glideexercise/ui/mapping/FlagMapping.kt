package com.example.glideexercise.ui.mapping

import com.example.glideexercise.R

object FlagMapping {
    private val flags = mapOf(
        "china.png" to R.drawable.china,
        "japan.png" to R.drawable.japan,
        "vietnam.png" to R.drawable.vietnam,
        "bangladesh.png" to R.drawable.bangladesh,
        "brazil.png" to R.drawable.brazil,
        "india.png" to R.drawable.india,
        "indonesia.png" to R.drawable.indonesia,
        "iran.png" to R.drawable.iran,
        "turkey.png" to R.drawable.turkey,
        "philippines.png" to R.drawable.philippines,
        "dr_congo.png" to R.drawable.dr_congo,
        "thailand.png" to R.drawable.thailand,
        "egypt.png" to R.drawable.egypt,
        "united_states.png" to R.drawable.united_states,
        "ethiopia.png" to R.drawable.ethiopia,
        "germany.png" to R.drawable.germany,
        "mexico.png" to R.drawable.mexico,
        "nigeria.png" to R.drawable.nigeria,
        "pakistan.png" to R.drawable.pakistan,
        "russia.png" to R.drawable.russia
    )
    fun getResIdByFileName(fileName: String): Int? {
        return flags[fileName] ?: R.drawable.vietnam
    }
}