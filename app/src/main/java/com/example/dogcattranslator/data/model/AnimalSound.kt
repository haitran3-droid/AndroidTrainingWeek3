package com.example.dogcattranslator.data.model

import androidx.annotation.RawRes
import androidx.annotation.StringRes

/**
 * Dữ liệu một tiếng thú cưng, dùng chung cho Sound và kết quả Home.
 *
 * Luồng: Repository cung cấp sound → Sound cho chọn nghe, hoặc Home chọn theo loài và mức peak.
 *
 * @property id Mã âm thanh, ví dụ dog_happy.
 * @property animalType Loài phát ra âm thanh.
 * @property label ID chuỗi tên trong strings.xml.
 * @property posterKey Khóa ảnh riêng hoặc ô trong tấm ảnh ghép.
 * @property audioResId ID audio trong res/raw; null nghĩa là thiếu audio và UI cần báo lỗi.
 * @property amplitudeLevel Mức ánh xạ kết quả Home; null nếu sound chỉ để nghe ở Sound.
 * StringRes/RawRes hỗ trợ IDE/Lint, không tạo kiểu Int riêng cho compiler.
 */
data class AnimalSound(
    val id: String,
    val animalType: AnimalType,
    @param:StringRes val label: Int,
    val posterKey: String,
    @param:RawRes val audioResId: Int? = null,
    val amplitudeLevel: AmplitudeLevel? = null,
)
