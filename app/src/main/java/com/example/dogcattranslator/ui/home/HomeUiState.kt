package com.example.dogcattranslator.ui.home

import com.example.dogcattranslator.data.model.AnimalSound
import com.example.dogcattranslator.data.model.AnimalType
import com.example.dogcattranslator.media.AmplitudeAnalysis

/**
 * Các bước của Home: IDLE → PREPARING → RECORDING → STOPPING → kết quả.
 *
 * Kết quả là RESULT, SILENT hoặc ERROR. RESULT vẫn có thể thiếu audio và kèm thông báo.
 *
 * Khi tự dừng, recorder hoàn tất stop/cleanup trước khi ViewModel đặt STOPPING rồi xử lý kết quả.
 */
enum class HomeStatus {
    IDLE,
    PREPARING,
    RECORDING,
    STOPPING,
    RESULT,
    SILENT,
    ERROR,
}

/**
 * Dữ liệu để HomeFragment vẽ UI; HomeViewModel tạo state mới khi lượt ghi thay đổi.
 *
 * Luồng: thao tác → ViewModel cập nhật state → Fragment quan sát và vẽ lại. Fragment vẫn xin quyền và
 * phát audio kết quả.
 *
 * @property status Bước hiện tại.
 * @property animalType Loài chọn, mặc định DOG.
 * @property elapsedMs Thời gian ghi, đơn vị ms.
 * @property lastAmplitude Peak gần nhất để vẽ cột.
 * @property analysis Kết quả phân tích, null nếu chưa có.
 * @property selectedSound Sound phản hồi, có thể null khi thiếu dữ liệu.
 * @property message ID chuỗi hướng dẫn/lỗi, null nếu không có.
 */
data class HomeUiState(
    val status: HomeStatus = HomeStatus.IDLE,
    val animalType: AnimalType = AnimalType.DOG,
    val elapsedMs: Long = 0,
    val lastAmplitude: Int = 0,
    val analysis: AmplitudeAnalysis? = null,
    val selectedSound: AnimalSound? = null,
    val message: Int? = null,
)
