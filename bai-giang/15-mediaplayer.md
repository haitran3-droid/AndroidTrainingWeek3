# 15 — Phát âm thanh bằng MediaPlayer

## Mục tiêu

Phát file/URI âm thanh, hiểu state machine, dùng prepareAsync và release an toàn. Cần lifecycle bài 00; audio focus được triển khai ở bài 18.

## 1. Chọn MediaPlayer khi nào?

MediaPlayer là API nền tảng để phát media đã mã hóa như MP3/AAC từ nguồn hỗ trợ. AudioTrack ở bài 16 phát PCM do app cung cấp; MediaRecorder ở bài 17 thu âm. Với app media nhiều tính năng, Media3 thường là lựa chọn thuận tiện hơn, nhưng MediaPlayer vẫn cần hiểu khi đọc code hiện có. Xem [About MediaPlayer](https://developer.android.com/media/platform/mediaplayer).

MediaPlayer có sẵn trong Android, không cần thêm dependency riêng. Phát audio không cần RECORD_AUDIO; quyền đó dành cho thu âm. Nguồn URL cần INTERNET, nguồn content URI cần quyền đọc còn hiệu lực.

## 2. State machine: gọi đúng lúc

```text
new MediaPlayer → Idle
setDataSource   → Initialized
prepareAsync    → Preparing → Prepared
start           → Started
pause           → Paused → start để tiếp tục
stop            → Stopped → phải prepare lại trước start
release         → End, không dùng instance đó nữa
```

Đây là các trạng thái chính để học; có thêm Completed/Error và các quy tắc chi tiết. `MediaPlayer.create(context, R.raw.sound)` thường trả instance đã Prepared, khác `MediaPlayer()` mới. `start()` trong Idle/Preparing là sai. Xem [Manage state and resources](https://developer.android.com/media/platform/mediaplayer/state-resources).

## 3. Demo điều khiển có focus

Ví dụ dưới đặt trong Activity, dùng class `DemoAudioFocus` và interface `PlaybackPort` ở [bài 18](18-audio-focus.md). Class focus chỉ cho phát khi hệ thống cấp focus. Layout cần TextView `result`; nút Play gọi `playAudio()`, Pause gọi `pauseAudio()`.

```kotlin
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import java.io.IOException

private var mediaPlayer: MediaPlayer? = null
private var prepared = false
private var pendingPlay = false

private val focus by lazy {
    DemoAudioFocus(this, object : PlaybackPort {
        override val isPlaying: Boolean
            get() = prepared && mediaPlayer?.isPlaying == true
        override fun play() {
            if (prepared) mediaPlayer?.start()
        }
        override fun pause() {
            if (isPlaying) mediaPlayer?.pause()
        }
        override fun setVolume(value: Float) {
            mediaPlayer?.setVolume(value, value)
        }
    })
}

private fun loadAudio(uri: Uri) {
    releaseAudio()
    val mp = MediaPlayer()
    mediaPlayer = mp
    mp.setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build())
    mp.setOnPreparedListener {
        if (mediaPlayer === mp) {
            prepared = true
            binding.result.text = "Sẵn sàng"
            if (pendingPlay) {
                pendingPlay = false
                focus.playByUser()
            }
        }
    }
    mp.setOnCompletionListener {
        if (mediaPlayer === mp) {
            focus.pauseByUser()
            binding.result.text = "Đã phát hết"
        }
    }
    mp.setOnErrorListener { _, what, extra ->
        if (mediaPlayer === mp) {
            prepared = false // Không hỏi isPlaying trên player đã Error.
            pendingPlay = false
            focus.pauseByUser()
            mp.release()
            mediaPlayer = null
            binding.result.text = "Lỗi phát: $what / $extra"
        }
        true // App đã xử lý, không dùng thông báo mặc định.
    }
    try {
        mp.setDataSource(this, uri)
        mp.prepareAsync()
        binding.result.text = "Đang chuẩn bị"
    } catch (error: IOException) {
        releaseAudio()
        binding.result.text = "Không mở được dữ liệu âm thanh"
    } catch (error: SecurityException) {
        releaseAudio()
        binding.result.text = "Không có quyền đọc URI"
    }
}

private fun playAudio() {
    if (mediaPlayer == null) return
    if (prepared) focus.playByUser()
    else pendingPlay = true
}

private fun pauseAudio() {
    pendingPlay = false // Ngăn onPrepared phát sau khi người dùng đã Pause.
    focus.pauseByUser()
}

private fun releaseAudio() {
    pendingPlay = false
    focus.pauseByUser()
    mediaPlayer?.release()
    mediaPlayer = null
    prepared = false
}
```

Tạo MediaPlayer và điều khiển trên cùng main thread trong demo này. URI có thể từ trình chọn tài liệu `OpenDocument` với MIME `audio/*`, file ghi âm bài 17 hoặc tài nguyên app. Photo Picker bài 10 chọn ảnh/video, không phải picker cho file audio.

## 4. Tại sao cần pendingPlay?

Người dùng bấm Play trong lúc Preparing: không được gọi start ngay. Ta ghi ý định `pendingPlay` rồi chờ onPrepared. Nếu người dùng đổi ý bấm Pause, phải xóa ý định đó. Callback bất đồng bộ cần xem **ý định hiện tại**, không chỉ nhớ thao tác trước đó.

Kiểm tra `mediaPlayer === mp` tránh callback từ instance cũ ảnh hưởng instance mới. Đây là lý do không tạo một player mới mỗi lần Play mà quên release player trước.

## 5. Seek, stop và lifecycle

Khi prepared, có thể dùng `mediaPlayer?.seekTo(30_000)` để tới giây 30. Thời gian này tính bằng milliseconds. UI thanh tiến độ có thể đọc `currentPosition` theo chu kỳ khi player còn sống; dừng callback cập nhật khi rời màn hình.

Pause giữ trạng thái để tiếp tục. Stop đưa player sang Stopped và yêu cầu prepare lại. Đừng dùng stop thay pause rồi gọi start ngay. Với demo đơn giản, nút Stop có thể gọi `releaseAudio()` và lần phát sau `loadAudio(uri)` lại.

```kotlin
override fun onStop() {
    releaseAudio() // Demo dừng khi màn hình không hiển thị.
    super.onStop()
}
```

Nếu dùng playback nền, cần owner/service và notification phù hợp. Không bỏ release tùy tiện để giữ tiếng chạy sau Home.

## Lỗi thường gặp

| Lỗi | Nguyên nhân cần kiểm tra |
|---|---|
| IllegalStateException | Gọi method khi state không cho phép |
| Không nghe tiếng | Chưa start, mất focus, âm lượng/route, dữ liệu lỗi |
| Phát hai bài cùng lúc | Nhiều instance chưa được dọn |
| Pause nhưng sau đó tự phát | pendingPlay hoặc resumeOnGain chưa bị xóa |
| URL chuẩn bị lâu | Dùng prepare đồng bộ; nguồn mạng chậm |

## Bài tập

Phát file ghi âm/MP3 thử nghiệm. Bấm Play trong lúc Preparing rồi bấm Pause trước khi Prepared. Thử seek, phát hết, rời màn hình và dùng URI không đọc được. Giải thích từng trạng thái player thay vì chỉ nhìn nút bấm.

## Tự kiểm tra

1. Stop xong start ngay được không? — Không, cần prepare lại.
2. Phát nhạc cần quyền microphone? — Không.
3. Vì sao chuẩn bị xong vẫn không tự phát? — Người dùng có thể đã Pause hoặc chưa được focus.

## Tiêu chí hoàn thành

- [ ] Phát/pause/seek đúng trạng thái và dùng prepareAsync.
- [ ] Xử lý error, callback trễ và ý định Pause.
- [ ] Release tài nguyên và quản lý focus theo bài 18.

[Mục lục](README.md) · [Bài tiếp: AudioTrack](16-audiotrack-static-stream.md)

