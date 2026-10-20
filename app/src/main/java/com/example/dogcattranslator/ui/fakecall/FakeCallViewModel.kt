package com.example.dogcattranslator.ui.fakecall

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.dogcattranslator.data.repository.DemoMediaRepository

/**
 * Giữ thú cưng, vị trí video, trạng thái camera/mute và mốc giờ cuộc gọi.
 *
 * Luồng: InCall đọc state khi tạo player/camera, ghi lại thay đổi khi người dùng thao tác. ViewModel
 * giữ qua rotation; SavedStateHandle hỗ trợ khi hệ thống tạo lại process đủ điều kiện.
 *
 * Cần nhớ: elapsedRealtime tính từ lúc thiết bị khởi động, không phải giờ trên lịch. Timer tính cả
 * background trong cùng lần khởi động. Player/camera có thể gián đoạn khi tạo lại.
 */
class FakeCallViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val animal = DemoMediaRepository.animal(savedState["animalId"] ?: "husky")

    var videoPosition: Long = savedState["videoPosition"] ?: 0L
        set(value) {
            field = value
            savedState["videoPosition"] = value
        }

    var muted: Boolean = savedState["muted"] ?: false
        set(value) {
            field = value
            savedState["muted"] = value
        }

    var cameraEnabled: Boolean = savedState["cameraEnabled"] ?: true
        set(value) {
            field = value
            savedState["cameraEnabled"] = value
        }

    private var startedAt: Long? = savedState["startedAt"]

    /**
     * Đặt mốc khi InCall gọi trong onViewCreated sau điều hướng Accept, độc lập với video READY.
     * Nếu đã có mốc thì giữ nguyên; đây không phải timestamp lấy trực tiếp tại sự kiện click Accept.
     */
    fun startClock() {
        if (startedAt == null) {
            startedAt = SystemClock.elapsedRealtime()
            savedState["startedAt"] = startedAt
        }
    }

    /**
     * Tính toán số mili-giây cuộc gọi đã diễn ra để cập nhật lên đồng hồ hiển thị (00:05, 00:10...).
     */
    fun elapsedMs() = startedAt?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0) } ?: 0L
}
