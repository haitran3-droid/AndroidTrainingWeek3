package com.example.dogcattranslator.data.model

import androidx.annotation.RawRes

/**
 * Dữ liệu một thú cưng trong danh sách cuộc gọi giả lập.
 *
 * Luồng: Repository tạo Animal → Adapter hiển thị → màn gọi tra lại bằng id. data class tự sinh equals,
 * hashCode, toString và copy.
 *
 * @property id Mã để tra cứu và truyền giữa các màn hình.
 * @property name Tên hiển thị.
 * @property type Loài chó hoặc mèo.
 * @property posterKey Khóa ảnh trong assets/portraits.
 * @property videoResId ID video trong res/raw; null nghĩa là chưa có video.
 * RawRes giúp IDE/Lint cảnh báo sai loại ID; tham số vẫn là Int.
 */
data class Animal(
    val id: String,
    val name: String,
    val type: AnimalType,
    val posterKey: String,
    @param:RawRes val videoResId: Int? = null,
)
