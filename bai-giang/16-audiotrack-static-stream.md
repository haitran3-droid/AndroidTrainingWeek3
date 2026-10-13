# 16 — AudioTrack: AudioPlayback ở Static Mode và Stream Mode

## Mục tiêu

Hiểu PCM, sample rate/channel/bit depth, phát một tone ngắn ở static và dữ liệu liên tục ở stream. Cần main/background thread bài 00 và audio focus bài 18.

## 1. AudioTrack nhận dữ liệu gì?

**PCM** là dãy giá trị biên độ âm thanh theo thời gian. Sample rate là số mẫu mỗi giây; channel count là số kênh; bit depth là số bit một mẫu mỗi kênh. Ví dụ PCM 16-bit, mono, 48 kHz:

```text
48.000 mẫu/giây × 1 kênh × 2 byte = 96.000 byte/giây
```

AudioTrack trong bài nhận PCM, không giải mã file MP3 tự động. Đổ byte MP3 hoặc cả WAV có header vào chế độ PCM sẽ gây tiếng sai. File nén cần decoder/MediaPlayer/Media3; WAV cần đọc format và tách payload đúng.

Trong kế hoạch học, “AudioPlayback Static/Stream” được học bằng AudioTrack vì API này có hai transfer mode đó. Đây không phải `AudioPlaybackCapture` để thu tiếng của app khác.

## 2. Static và Stream

| | MODE_STATIC | MODE_STREAM |
|---|---|---|
| Cách cấp dữ liệu | Nạp clip trước khi phát | Cấp thêm dữ liệu trong lúc phát |
| Phù hợp | Âm ngắn, lặp một clip | PCM dài, dữ liệu tạo/đọc liên tục |
| Bộ nhớ | Đủ cho toàn clip | Buffer hữu hạn |
| Việc app cần quản lý | Nạp dữ liệu, phát lại/dừng | Tốc độ cấp dữ liệu, lỗi write, underrun |

Hai mode được định nghĩa trong [AudioTrack source của Android](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/AudioTrack.java). Không đồng nhất Stream Mode với “tự tải stream internet”: nguồn PCM vẫn do app cung cấp.

## 3. Tạo tone để không cần file âm thanh

Hàm Kotlin thuần tạo sóng sin 440 Hz, biên độ thấp để demo:

```kotlin
import kotlin.math.PI
import kotlin.math.sin

fun makeTone(sampleRate: Int, frequency: Double, seconds: Double): ShortArray {
    require(sampleRate > 0 && frequency > 0 && frequency < sampleRate / 2.0)
    require(seconds > 0)
    return ShortArray((sampleRate * seconds).toInt()) { index ->
        (sin(2 * PI * frequency * index / sampleRate) * 4_000).toInt().toShort()
    }
}
```

`Short` chứa mẫu signed 16-bit. Frequency quyết định cao độ; sample rate là tần suất lấy mẫu, không phải cao độ. Kiểm tra âm lượng thiết bị trước khi nghe tone.

## 4. Static Mode

```kotlin
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

fun createStaticTone(): AudioTrack {
    val rate = 48_000
    val samples = makeTone(rate, 440.0, 0.3)
    val format = AudioFormat.Builder()
        .setSampleRate(rate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    val track = AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(format)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(samples.size * 2)
        .build()
    try {
        val written = track.write(samples, 0, samples.size)
        check(written == samples.size) { "Không nạp đủ mẫu: $written" }
        check(track.state == AudioTrack.STATE_INITIALIZED)
        return track // Caller xin focus rồi mới track.play().
    } catch (error: Exception) {
        track.release()
        throw error
    }
}
```

`bufferSizeInBytes` dùng byte; overload `write(ShortArray...)` trả **số short**, không phải byte. Sau khi nạp, kiểm tra state đã INITIALIZED trước khi sử dụng. Ownership thuộc caller: giữ track, request focus ở bài 18 rồi play; khi dừng hẳn phải release. Để phát lại từ đầu, pause/stop phù hợp rồi dùng `reloadStaticData()` hoặc đặt lại playback head khi API cho phép.

Không release ngay dòng sau `play()` vì âm chưa phát xong. Có thể dùng marker/callback theo số frame hoặc nút Stop để xác định thời điểm dọn.

## 5. Stream Mode: ghi từng khối

Hàm demo sau giả định **đã được cấp focus**, chạy ở một worker thread riêng; tín hiệu dừng do UI/focus điều khiển:

```kotlin
import java.util.concurrent.atomic.AtomicBoolean

fun runToneStream(running: AtomicBoolean) {
    val rate = 48_000
    val minBytes = AudioTrack.getMinBufferSize(
        rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    require(minBytes > 0) { "Thiết bị không hỗ trợ cấu hình" }
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder()
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(minBytes * 2)
        .build()
    var sampleIndex = 0L
    val chunk = ShortArray(960) // 20 ms mono ở 48 kHz.
    try {
        check(track.state == AudioTrack.STATE_INITIALIZED)
        track.play()
        while (running.get()) {
            for (index in chunk.indices) {
                chunk[index] = (sin(2 * PI * 440 * sampleIndex / rate)
                    * 4_000).toInt().toShort()
                sampleIndex++ // Giữ pha giữa các chunk, tránh khớp nối sai.
            }
            var offset = 0
            while (offset < chunk.size && running.get()) {
                val count = track.write(
                    chunk, offset, chunk.size - offset, AudioTrack.WRITE_BLOCKING
                )
                check(count > 0) { "AudioTrack.write lỗi/không tiến triển: $count" }
                offset += count // write có thể chỉ ghi một phần.
            }
        }
    } finally {
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                track.flush() // Demo Stop: bỏ dữ liệu stream còn trong buffer.
            }
        } finally {
            track.release()
        }
    }
}
```

Blocking write có thể chờ buffer; không chạy hàm này trên main thread. AtomicBoolean giúp worker thấy lệnh stop từ UI. Trong demo, khi mất focus đặt running = false; để worker tự pause/flush/release. Không release track từ UI khi worker vẫn write. Chờ worker cũ kết thúc trước khi bắt đầu worker mới và abandon focus sau khi dừng. Đây là demo stop/restart, chưa triển khai pause/resume stream giữ nguyên vị trí.

## 6. Underrun, buffer và độ trễ

Underrun xảy ra khi thiết bị cần mẫu nhưng app chưa cung cấp. Nghe có thể lách tách/đứt đoạn. Buffer lớn giúp chống giật nhưng tăng độ trễ; getMinBufferSize là điểm bắt đầu, không bảo đảm độ trễ tối ưu cho mọi đường âm thanh.

Tạo mẫu chậm, đọc mạng trực tiếp trong vòng cấp PCM hoặc tạo quá nhiều object đều có thể làm hụt dữ liệu. Với stream thực, thường tách producer (đọc/giải mã) và consumer (write), dùng hàng đợi có giới hạn.

## Bài tập

Phát tone static 0,3 giây rồi tone stream có nút Stop. Đổi frequency 440 → 880 và giải thích cao độ. Tính số byte của một giây stereo 16-bit 48 kHz: **192.000 byte**. Thử mất focus, đảm bảo worker dừng và không khởi chạy hai worker.

## Tự kiểm tra

1. Stream Mode tự tải URL được không? — Không.
2. write ShortArray trả byte hay mẫu short? — Số short.
3. Tại sao giữ sampleIndex giữa chunk? — Giữ liên tục pha âm thanh.

## Tiêu chí hoàn thành

- [ ] Giải thích PCM và tính đúng dung lượng.
- [ ] Phát được static/stream, xử lý partial write và lỗi.
- [ ] Dừng worker, release và xử lý focus theo ownership rõ ràng.

[Mục lục](README.md) · [Bài tiếp: MediaRecorder](17-mediarecorder.md)
