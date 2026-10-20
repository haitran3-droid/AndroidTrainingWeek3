package com.example.dogcattranslator.data.model

/**
 * Hai loài được hỗ trợ: DOG và CAT.
 *
 * Home và Repository dùng giá trị này để chọn âm thanh đúng loài.
 *
 * Dùng enum thay chuỗi tự nhập giúp compiler phát hiện tên giá trị không hợp lệ.
 */
enum class AnimalType {
    DOG,
    CAT,
}
