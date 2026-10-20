package com.example.dogcattranslator.ui.sound

import android.content.Context
import com.example.dogcattranslator.R
import com.example.dogcattranslator.data.model.AnimalSound
import com.example.dogcattranslator.data.model.AnimalType

/**
 * Ghép tên loài và nhãn sound, ví dụ Dog - Happy, cho card và trình đọc màn hình.
 *
 * SoundFragment/SoundAdapter dùng chung hàm này để tên nhất quán.
 *
 * Hàm nằm ngoài class; internal giới hạn truy cập trong module Kotlin hiện tại.
 */
internal fun soundDisplayName(context: Context, sound: AnimalSound): String {
    val animalLabel = if (sound.animalType == AnimalType.DOG) R.string.dog else R.string.cat
    return context.getString(
        R.string.sound_item_label,
        context.getString(animalLabel),
        context.getString(sound.label),
    )
}
