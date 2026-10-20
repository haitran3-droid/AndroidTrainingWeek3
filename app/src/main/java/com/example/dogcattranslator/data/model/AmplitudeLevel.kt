package com.example.dogcattranslator.data.model

/**
 * Mức phân loại trung bình các peak (biên độ đỉnh) để chọn tiếng thú cưng giả lập.
 *
 * AmplitudeAnalyzer so trung bình với silence, medium và high. SILENT ở dưới silence; LOW, MEDIUM, HIGH
 * lần lượt ở các khoảng còn lại.
 *
 * Cần nhớ: phải thử ngưỡng trên máy thật. Các mức không đo Hz/dB hay nhận diện cảm xúc.
 */
enum class AmplitudeLevel {
    SILENT,
    LOW,
    MEDIUM,
    HIGH,
}
