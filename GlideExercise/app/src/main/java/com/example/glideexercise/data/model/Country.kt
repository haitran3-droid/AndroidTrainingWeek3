package com.example.glideexercise.data.model

import com.google.gson.annotations.SerializedName

data class Country(
    @SerializedName("id")
    val countryId: Int,
    @SerializedName("name")
    val countryName: String,
    @SerializedName("flag")
    val countryFlag: String,
    @SerializedName("capital")
    val countryCapital: String,
    @SerializedName("population")
    val countryPopulation: String,
    @SerializedName("area")
    val countryArea: String,
    @SerializedName("density")
    val countryDensity: String,
    @SerializedName("world_share")
    val countryWorldShare: String
){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Country

        return countryId == other.countryId
    }

    override fun hashCode(): Int {
        return countryId
    }
}
