# 14 — Phát video và audio bằng Media3 ExoPlayer

## Mục tiêu

Phát media từ URI/URL, dùng PlayerView, quan sát buffering/error và release đúng lúc. Cần bài 00; URI video lấy từ bài 10.

## 1. ExoPlayer làm gì?

Player cần đọc dữ liệu, giải mã và đưa hình/âm thanh đến thiết bị. ExoPlayer cung cấp player có nhiều khả năng tùy chỉnh; AndroidX Media3 có các module player, UI và session. Bài dùng namespace `androidx.media3`, không trộn class `com.google.android.exoplayer2` của thư viện cũ. Xem [Getting started](https://developer.android.com/media/media3/exoplayer/hello-world).

Khác với phát một file hoàn chỉnh, streaming như HLS/DASH chia dữ liệu và có thể đổi chất lượng theo mạng. Codec/container/protocol là các lớp khác nhau: đuôi `.mp4` chưa bảo đảm máy giải mã được mọi codec trong đó.

## 2. Thêm dependency và View

```kotlin
val media3Version = "1.5.1"
implementation("androidx.media3:media3-exoplayer:$media3Version")
implementation("androidx.media3:media3-ui:$media3Version")
// Nếu bài thử cần HLS:
implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
```

Đây là mốc ổn định đã phát hành trong [Media3 release notes](https://developer.android.com/jetpack/androidx/releases/media3), không phải bản mới nhất. Giữ mọi module Media3 cùng version; bỏ module HLS nếu không dùng. URL mạng cần INTERNET; URI private cần grant còn hiệu lực.

Trong XML của màn hình media:

```xml
<androidx.media3.ui.PlayerView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/playerView"
    android:layout_width="match_parent"
    android:layout_height="240dp"
    android:keepScreenOn="true" />
```

Đây là phần View, ghép vào layout có TextView `result` nếu dùng status trong ví dụ. Áp dụng insets và tỉ lệ vùng video theo design; đừng dùng chiều cao cố định cho mọi sản phẩm.

## 3. Tạo player và chuẩn bị media

Trong Activity:

```kotlin
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

private var player: ExoPlayer? = null
private var resumePosition = 0L

private fun openMedia(uri: Uri) {
    if (player != null) return
    val attributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    val newPlayer = ExoPlayer.Builder(this).build()
    newPlayer.setAudioAttributes(attributes, true) // Tự quản lý audio focus.
    newPlayer.addListener(object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            binding.result.text = when (state) {
                Player.STATE_BUFFERING -> "Đang tải dữ liệu"
                Player.STATE_READY -> "Sẵn sàng"
                Player.STATE_ENDED -> "Đã phát hết"
                else -> "Chưa chuẩn bị"
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            binding.result.text = "Không phát được: ${error.errorCodeName}"
        }
    })
    binding.playerView.player = newPlayer
    newPlayer.setMediaItem(MediaItem.fromUri(uri))
    newPlayer.seekTo(resumePosition)
    newPlayer.prepare()
    newPlayer.playWhenReady = false // Người dùng bấm Play trong PlayerView.
    player = newPlayer
}
```

`prepare()` bắt đầu chuẩn bị, không có nghĩa dữ liệu đã sẵn sàng ngay dòng kế tiếp. `play()` yêu cầu phát; `pause()` tạm dừng; `seekTo(milliseconds)` đổi vị trí. Các thao tác player/UI ở main thread; đừng dùng Dispatchers.IO chỉ vì player đọc mạng nội bộ.

`AudioAttributes` ở đây thuộc **Media3**. Bài Audio Focus thủ công dùng `android.media.AudioAttributes`; hai class khác package và không thay trực tiếp cho nhau.

## 4. Gắn với lifecycle màn hình demo

Giữ `selectedUri` trong trạng thái của màn hình. Nếu có URI, gọi `openMedia(selectedUri)` khi Activity vào `onStart`; nếu picker trả URI lúc màn hình đang hiển thị thì mở lúc đó. Khi đổi media khác, thay MediaItem trên player hiện có và reset vị trí cho item mới, không gọi hàm trên rồi kỳ vọng guard `player != null` sẽ đổi item.

```kotlin
override fun onStop() {
    player?.let { resumePosition = it.currentPosition }
    binding.playerView.player = null
    player?.release()
    player = null
    super.onStop()
}
```

Đây là chính sách demo dừng khi màn hình không hiển thị, phù hợp `minSdk 26` của bộ bài. Muốn khôi phục qua xoay/process recreation, lưu URI và vị trí trong SavedState/ViewModel theo ownership phù hợp. Biến Activity đơn thuần chỉ giữ qua Stop/Start của **cùng instance**, không tự sống qua process death.

## 5. Buffering, đang phát và lỗi

`STATE_READY` nghĩa player đủ dữ liệu để phát; không có nghĩa đang nghe âm thanh. `isPlaying` còn phụ thuộc playWhenReady và trạng thái bị suppress như mất focus. Dùng `onIsPlayingChanged` khi UI muốn hiển thị đang phát. Xem [Player events](https://developer.android.com/media/media3/exoplayer/listening-to-player-events).

| Hiện tượng | Kiểm tra |
|---|---|
| Video không mở | URI/grant, URL, HTTP lỗi, định dạng hỗ trợ |
| Chỉ có tiếng | Track video/codec/surface và PlayerView |
| Buffering lâu | Mạng, bitrate, server và buffer |
| Âm thanh vẫn chạy sau rời màn hình | Ownership/lifecycle và release |
| Wrong thread exception | Thao tác player ở application looper |

Đừng retry tự động vô hạn mọi error; lỗi quyền/codec/404 không được sửa bằng việc lặp lại.

## 6. Focus và phát nền

`setAudioAttributes(..., true)` giao audio focus cho ExoPlayer. Không đồng thời request focus thủ công cho cùng player. Bài 18 giải thích vì sao và điều kiện request focus trên Android mới.

App nghe nhạc nền cần MediaSessionService/notification và các yêu cầu foreground service phù hợp; giữ player trong Activity rồi bỏ release không tạo ra một thiết kế phát nền hoàn chỉnh. Xem [Background playback](https://developer.android.com/media/media3/session/background-playback).

## Bài tập

Chọn một video Gallery và phát bằng URI. Thử pause/seek, tắt mạng với URL streaming, dùng URL hỏng để xem error. Rời màn hình rồi quay lại; kiểm tra vị trí resume và đảm bảo không có hai player phát cùng lúc.

## Tự kiểm tra

1. READY luôn nghĩa đang phát? — Không.
2. prepare là đồng bộ hay bất đồng bộ? — Chuẩn bị diễn ra bất đồng bộ.
3. Media3 tự focus thì có cần focus manager riêng cho cùng player? — Không.

## Tiêu chí hoàn thành

- [ ] Phát được URI/URL và nhận state/error.
- [ ] Điều khiển player đúng thread, release theo owner.
- [ ] Phân biệt focus tự động, state đang phát và phát nền.

[Mục lục](README.md) · [Bài tiếp: MediaPlayer](15-mediaplayer.md)
