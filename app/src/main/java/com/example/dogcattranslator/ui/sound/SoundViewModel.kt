package com.example.dogcattranslator.ui.sound

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.dogcattranslator.data.repository.DemoMediaRepository
import com.example.dogcattranslator.media.PlaybackState
import com.example.dogcattranslator.media.PlaybackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Giữ sound được chọn và trạng thái phát để SoundFragment hiển thị.
 *
 * Luồng: Fragment gọi select khi bấm card; player gửi PlaybackState qua update. SavedStateHandle lưu
 * soundId/vị trí (ms); ViewModel giữ qua thay đổi cấu hình.
 *
 * Cần nhớ: Fragment tạo player mới và seek khi hoạt động lại, không tự resume. Khôi phục dữ liệu không
 * có nghĩa phát liền mạch.
 */
class SoundViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val sounds = DemoMediaRepository.sounds()

    private val selection =
        MutableStateFlow(
            // Khôi phục ID đã lưu; chưa có thì chọn phần tử thứ hai của dữ liệu demo (Dog - Sad).
            sounds.firstOrNull { it.id == savedState.get<String>("soundId") } ?: sounds[1],
        )
    val selected = selection.asStateFlow()

    private val playback = MutableStateFlow(PlaybackState())
    val state = playback.asStateFlow()

    var position: Int
        get() = savedState["position"] ?: 0
        set(value) {
            savedState["position"] = value
        }

    /**
     * Người dùng bấm chọn một âm thanh khác trong danh sách lưới.
     */
    fun select(id: String) {
        selection.value = sounds.first { it.id == id }
        savedState["soundId"] = id
        position = 0 // Khi đổi sang âm thanh mới, tua lại từ đầu (0ms)
    }

    /**
     * Cập nhật trạng thái phát âm thanh nhận được từ SoundPlayerManager.
     */
    fun update(value: PlaybackState) {
        playback.value = value
        // Lưu 0 khi đã nghe hết để lần mở lại có thể phát từ đầu.
        position = if (value.status == PlaybackStatus.COMPLETED) 0 else value.position
    }
}
