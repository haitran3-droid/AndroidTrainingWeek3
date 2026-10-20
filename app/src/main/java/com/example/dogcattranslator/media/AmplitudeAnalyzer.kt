package com.example.dogcattranslator.media

import com.example.dogcattranslator.data.model.AmplitudeLevel

/**
 * Ba ngưỡng cấu hình để phân loại trung bình các peak.
 *
 * Mặc định: dưới 500 là SILENT; 500..<5000 là LOW; 5000..<10000 là MEDIUM; còn lại HIGH. Ngưỡng áp dụng
 * cho trung bình, không loại từng mẫu nhỏ.
 *
 * Cần nhớ: ngưỡng demo cần thử máy thật. require kiểm tra thứ tự ngưỡng khi tạo object.
 */
data class AmplitudeThresholds(
    val silence: Int = 500,
    val medium: Int = 5000,
    val high: Int = 10000,
) {
    init {
        require(silence > 0 && medium > silence && high > medium)
    }
}

/**
 * Kết quả recorder trả về để HomeViewModel chọn âm thanh phản hồi.
 *
 * @property averagePeak Trung bình cộng các peak hợp lệ; không phải Hz, dB hay RMS.
 * @property sampleCount Số mẫu đưa vào phép tính, không gồm lần đọc đầu bị bỏ qua.
 * @property level Mức phân loại sau khi so trung bình với ngưỡng.
 */
data class AmplitudeAnalysis(
    val averagePeak: Double,
    val sampleCount: Int,
    val level: AmplitudeLevel,
)

/**
 * Cộng dồn peak và tính trung bình để phân loại mức âm thanh.
 *
 * Luồng: recorder gọi addSample nhiều lần → gọi result khi lượt ghi kết thúc. Trung bình = tổng peak /
 * số mẫu hợp lệ; không có mẫu thì trả 0 và SILENT.
 *
 * Cần nhớ: tổng dùng Long để giảm nguy cơ tràn Int; chỉ bỏ mẫu âm. Đây không phải phân tích tần số hay
 * dịch tiếng động vật thật.
 */
class AmplitudeAnalyzer(private val thresholds: AmplitudeThresholds = AmplitudeThresholds()) {
    // Dùng Long vì cộng nhiều peak lớn có thể vượt giới hạn Int.
    private var totalPeakAmplitude = 0L
    private var sampleCount = 0

    /**
     * Thêm một mẫu biên độ vào bộ tính toán.
     * @param peak Mẫu peak không âm. Bỏ qua giá trị âm; hàm không chặn giá trị lớn hơn 32767.
     */
    fun addSample(peak: Int) {
        if (peak >= 0) {
            totalPeakAmplitude += peak
            sampleCount++
        }
    }

    /**
     * Trả về kết quả phân tích:
     * - Tính trung bình cộng biên độ: totalPeakAmplitude / sampleCount (tránh chia cho 0 nếu chưa có
     * mẫu nào).
     * - Dùng cấu trúc `when` so sánh với các ngưỡng thresholds để xác định [AmplitudeLevel].
     */
    fun result(): AmplitudeAnalysis {
        // Đổi tổng sang Double trước khi chia để giữ phần lẻ; không có mẫu thì tránh chia cho 0.
        val average = if (sampleCount == 0) 0.0 else totalPeakAmplitude.toDouble() / sampleCount
        val level =
            when {
                average < thresholds.silence -> AmplitudeLevel.SILENT
                average < thresholds.medium -> AmplitudeLevel.LOW
                average < thresholds.high -> AmplitudeLevel.MEDIUM
                else -> AmplitudeLevel.HIGH
            }
        return AmplitudeAnalysis(average, sampleCount, level)
    }
}
