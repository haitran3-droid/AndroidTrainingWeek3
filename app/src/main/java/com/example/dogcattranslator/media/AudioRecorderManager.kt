package com.example.dogcattranslator.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Ghi âm một lượt và lấy mẫu biên độ đỉnh khoảng 100 ms/lần.
 *
 * Luồng: HomeViewModel gọi record → tạo recorder/file cache → lấy mẫu → trả AmplitudeAnalysis.
 * requestStop dừng sớm giữ kết quả; hủy coroutine đi qua cleanup.
 *
 * Cần nhớ: prepare/stop chạy ngoài Main để tránh đứng UI; finally thử release và xóa file. Bên gọi phải
 * ngăn ghi chồng. limitedParallelism(1) chỉ tuần tự hóa các đoạn giữa điểm tạm nhường thực thi
 * (suspend), không khóa cả lượt ghi hay giữ một thread cố định.
 */
class AudioRecorderManager(context: Context) {
    private val appContext = context.applicationContext

    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    // AtomicBoolean cho phép luồng khác báo dừng; recorder đọc cờ rồi tự dừng trên luồng ghi.
    private val stopRequested = AtomicBoolean(false)

    /**
     * Yêu cầu dừng phiên thu âm hiện tại một cách an toàn.
     */
    fun requestStop() {
        stopRequested.set(true)
    }

    /**
     * Bắt đầu thu âm và định kỳ trả về mẫu biên độ sóng âm.
     * @param onSample Callback (hàm gọi lại) nhận vào (thời gian đã trôi qua tính bằng ms, biên độ đỉnh
     * peak).
     * @return [AmplitudeAnalysis] Chứa kết quả thống kê biên độ âm thanh sau khi thu âm xong.
     */
    @Suppress("DEPRECATION")
    suspend fun record(onSample: suspend (Long, Int) -> Unit): AmplitudeAnalysis =
        withContext(dispatcher) {
            val file = File.createTempFile("voice_", ".m4a", appContext.cacheDir)
            var recorder: MediaRecorder? = null
            var running = false
            try {
                val active =
                    // API 31+ dùng constructor nhận Context; constructor cũ vẫn có nhưng đã deprecated.
                    if (Build.VERSION.SDK_INT >= 31) MediaRecorder(appContext) else MediaRecorder()
                recorder = active

                active.setAudioSource(MediaRecorder.AudioSource.MIC) //lấy âm thành từ mic
                active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // định dạng chứa dữ liệu ghi âm
                active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC) // bộ mã hóa âm thanh
                active.setAudioSamplingRate(16000) // tần số lấy mẫu âm thanh: 16.000 mẫu/giây
                active.setAudioEncodingBitRate(64000) //Bitrate mã hóa: 64.000 bit/giây
                active.setOutputFile(file.absolutePath) //Đường dẫn file ghi âm
                active.prepare()

                currentCoroutineContext().ensureActive()
                active.start()
                running = true

                // Đo thời lượng bằng mốc từ lúc khởi động máy, không lệch khi người dùng chỉnh giờ trên lịch.
                val startedAt = SystemClock.elapsedRealtime()
                val analyzer = AmplitudeAnalyzer()

                // Lần đầu API trả 0 nên đọc bỏ; các lần sau lấy peak kể từ lần đọc trước.
                active.maxAmplitude
                onSample(0, 0)

                while (currentCoroutineContext().isActive && !stopRequested.get()) {
                    val remaining = DURATION_MS - (SystemClock.elapsedRealtime() - startedAt)
                    if (remaining <= 0) break
                    delay(minOf(INTERVAL_MS, remaining))
                    if (stopRequested.get()) break

                    // delay không bảo đảm đúng 100 ms; peak thuộc khoảng thực tế giữa hai lần đọc.
                    val peak = active.maxAmplitude
                    analyzer.addSample(peak)
                    onSample(
                        (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(DURATION_MS),
                        peak,
                    )
                }

                currentCoroutineContext().ensureActive()
                running = false
                try {
                    active.stop()
                } catch (error: RuntimeException) {
                    // stop có thể lỗi khi chưa có dữ liệu hợp lệ
                    // thường do dừng quá sớm; không có ngưỡng ms cố định.
                    throw IOException("Recording too short or unavailable", error)
                }
                analyzer.result()
            } finally {
                // Cleanup tránh hủy coroutine, không tránh process bị kill; release/delete vẫn có thể thất bại.
                withContext(NonCancellable + dispatcher) {
                    if (running) runCatching { recorder?.stop() }
                    runCatching { recorder?.release() }
                    file.delete()
                }
            }
        }

    companion object {
        const val DURATION_MS = 15000L
        const val INTERVAL_MS = 100L
    }
}
