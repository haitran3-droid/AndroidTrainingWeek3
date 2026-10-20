package com.example.dogcattranslator.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.example.dogcattranslator.R
import kotlin.math.abs
import kotlin.math.sin

/**
 * Vẽ lịch sử peak bằng các cột để phản hồi trực quan lúc Home ghi âm.
 *
 * Luồng: Home nhận state → addPeak → invalidate yêu cầu vẽ lại → onDraw vẽ cột. Cột chưa có mẫu dùng
 * họa tiết trang trí, không phải waveform PCM hay FFT.
 *
 * Cần nhớ: chỉ giữ 64 peak gần nhất. @JvmOverloads tạo constructor phù hợp để nhúng View trong XML.
 */
class WaveformView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    android.view.View(context, attrs) {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.orange)
            alpha = 120 // Độ mờ trong suốt (alpha)
        }

    private var peaks = emptyList<Float>()

    /**
     * Thêm một giá trị biên độ mới từ MediaRecorder và yêu cầu vẽ lại.
     * @param amplitude Giá trị biên độ từ 0 đến 32767.
     */
    fun addPeak(amplitude: Int) {
        val normalizedPeak = (amplitude / 32767f).coerceIn(0.03f, 1f)
        // Giữ tối đa 64 cột; peak nhỏ vẫn có chiều cao tối thiểu để nhìn thấy.
        peaks = (peaks + normalizedPeak).takeLast(BAR_COUNT)
        invalidate() // Yêu cầu Android vẽ lại giao diện
    }

    /**
     * Đặt lại biểu đồ về trạng thái ban đầu khi bắt đầu phiên thu âm mới.
     */
    fun reset() {
        peaks = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val step = width.toFloat() / BAR_COUNT
        for (i in 0 until BAR_COUNT) {
            // Cột chưa có dữ liệu dùng họa tiết trang trí, không phải mẫu đo.
            val level = peaks.getOrNull(i) ?: decorativeLevel(i)
            val top = height - level * height * 0.9f
            canvas.drawRect(i * step, top, i * step + step * 0.35f, height.toFloat(), paint)
        }
    }

    /**
     * Tạo đường lượn sóng giả lập dạng sin khi chưa có âm thanh để giao diện trông sinh động theo
     * mockup.
     */
    private fun decorativeLevel(index: Int): Float {
        val heightScale = if (index % 7 == 0) 0.8f else 0.3f
        return 0.06f + abs(sin(index * 2.3)).toFloat() * heightScale
    }

    companion object {
        private const val BAR_COUNT = 64
    }
}
