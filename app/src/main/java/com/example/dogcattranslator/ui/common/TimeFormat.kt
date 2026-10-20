package com.example.dogcattranslator.ui.common

import java.util.Locale

/**
 * Đổi mili-giây thành phút:giây cho timer Home và InCall.
 *
 * Ví dụ: 5000 → 00:05; 65000 → 01:05; 3600000 → 60:00. Chặn số âm về 0, bỏ phần lẻ dưới giây; %02d thêm
 * 0 để có ít nhất hai chữ số.
 *
 * Locale.ROOT giữ định dạng nhất quán; số phút vẫn tăng khi quá một giờ.
 */
fun formatElapsed(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
