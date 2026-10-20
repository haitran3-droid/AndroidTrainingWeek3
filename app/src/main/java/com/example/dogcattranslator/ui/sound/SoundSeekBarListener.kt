package com.example.dogcattranslator.ui.sound

import android.widget.SeekBar

/**
 * Chuyển thao tác tua trên SeekBar thành vị trí cần phát, đơn vị ms.
 *
 * Luồng chạm: bắt đầu kéo → isDragging=true → thả tay mới gọi onSeek. Phím bấm/trợ năng có
 * fromUser=true nhưng không có lượt chạm nên tua ngay.
 *
 * Cần nhớ: Fragment không ghi đè tiến độ khi đang kéo. Cập nhật từ player có fromUser=false, không được
 * biến thành yêu cầu tua mới.
 */
class SoundSeekBarListener(private val onSeek: (Int) -> Unit) : SeekBar.OnSeekBarChangeListener {
    var isDragging = false
        private set

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        isDragging = true
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        // Phím bấm/trợ năng không có lượt chạm-kéo-thả; tua ngay với thay đổi do người dùng.
        if (fromUser && !isDragging) onSeek(progress)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        isDragging = false
        onSeek(seekBar.progress)
    }

    /**
     * Hủy trạng thái kéo khi màn hình bị dừng (onStop).
     */
    fun cancelDrag() {
        isDragging = false
    }
}
