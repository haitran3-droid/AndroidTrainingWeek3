# 18 — Audio Focus: request, mất focus và tiếp tục phát

## Mục tiêu

Hiểu vì sao app âm thanh cần phối hợp với app khác, request focus đúng lúc và xử lý loss/gain mà không tự phát sau khi người dùng đã Pause. Cần MediaPlayer/AudioTrack ở bài 15/16.

## 1. Audio focus là gì?

Nhiều app có thể cùng phát âm thanh. Audio focus là cơ chế hệ thống giúp phối hợp: khi app khác cần nghe rõ, app của bạn có thể phải dừng, tạm dừng hoặc nhỏ tiếng.

Ví dụ bạn nghe nhạc và mở app khác phát video. Nhạc cần nhường quyền theo yêu cầu focus; không phải cứ có nút Play là được phát bất chấp app khác.

Focus khác volume và microphone permission. Có focus không chứng minh dữ liệu đã chuẩn bị; bạn vẫn cần player đúng state. Mất focus cũng khác việc người dùng bấm Pause: lý do khác nhau sẽ quyết định có tự tiếp tục không.

## 2. Các kiểu request và sự kiện

| Request | Nhu cầu thường gặp |
|---|---|
| AUDIOFOCUS_GAIN | Phát lâu, ví dụ nhạc/video |
| AUDIOFOCUS_GAIN_TRANSIENT | Cần nghe trong một thời gian ngắn |
| AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK | Ngắn, cho phép bên đang phát nhỏ tiếng |
| AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE | Cần ưu tiên độc quyền trong thời gian ngắn |

| Callback | Ý nghĩa và cách xử lý thường dùng |
|---|---|
| AUDIOFOCUS_GAIN | Được focus trở lại; khôi phục volume và có thể resume |
| AUDIOFOCUS_LOSS | Mất lâu dài; dừng/pause, xóa ý định auto-resume |
| AUDIOFOCUS_LOSS_TRANSIENT | Mất tạm; pause và ghi ý định resume nếu hợp lệ |
| AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK | Có thể nhỏ tiếng; hoặc pause theo chính sách |

Các kiểu là lựa chọn theo trường hợp sử dụng, không phải quyền ép mọi app khác im lặng. Xem [Manage audio focus](https://developer.android.com/media/optimize/audio-focus).

## 3. Request ngay trước khi phát

Luồng cần có:

```text
Người dùng Play → media đã sẵn sàng → request focus
                                       ├→ GRANTED: phát
                                       ├→ DELAYED: chờ callback, chưa phát
                                       └→ FAILED: chưa phát, báo trạng thái
```

API 26+ có `AudioFocusRequest`. Nếu app hỗ trợ API thấp hơn, cần nhánh legacy có kiểm tra version; bộ demo chọn minSdk 26 để tập trung cơ chế hiện đại.

## 4. Controller demo dùng với MediaPlayer

Đặt interface và class trong file Kotlin của app thực hành. Đây là utility của bài giảng, không phải class có sẵn trong SDK. Tất cả method do UI gọi trên **main thread**; callback cũng được chuyển về main thread.

```kotlin
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

interface PlaybackPort {
    val isPlaying: Boolean
    fun play()
    fun pause()
    fun setVolume(value: Float)
}

class DemoAudioFocus(context: Context, private val playback: PlaybackPort) {
    private val manager = requireNotNull(context.applicationContext
        .getSystemService(AudioManager::class.java))
    private var wantsToPlay = false
    private var hasFocus = false
    private var resumeOnGain = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!wantsToPlay) {
                    abandon() // Gain đến trễ sau khi người dùng đã Pause.
                } else {
                    hasFocus = true
                    playback.setVolume(1f)
                    if (resumeOnGain) playback.play()
                    resumeOnGain = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                wantsToPlay = false
                resumeOnGain = false
                pauseIfPlaying()
                abandon()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasFocus = false
                resumeOnGain = wantsToPlay
                pauseIfPlaying()
            }
        }
    }

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        .setAcceptsDelayedFocusGain(true)
        .setWillPauseWhenDucked(true) // Chính sách demo: pause khi bị yêu cầu duck.
        .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
        .build()

    fun playByUser(): Int {
        wantsToPlay = true
        if (hasFocus) {
            playback.play()
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        val result = manager.requestAudioFocus(request)
        when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                hasFocus = true
                resumeOnGain = false
                playback.setVolume(1f)
                playback.play()
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                hasFocus = false
                resumeOnGain = true
            }
            else -> {
                hasFocus = false
                wantsToPlay = false
                resumeOnGain = false
            }
        }
        return result
    }

    fun pauseByUser() {
        wantsToPlay = false
        resumeOnGain = false
        pauseIfPlaying()
        abandon()
    }

    fun close() = pauseByUser()

    private fun pauseIfPlaying() {
        if (playback.isPlaying) playback.pause()
    }

    private fun abandon() {
        manager.abandonAudioFocusRequest(request)
        hasFocus = false
    }
}
```

Bài 15 có adapter `PlaybackPort` cho MediaPlayer. Caller chỉ gọi `playByUser()` khi player đã prepared; kiểm tra giá trị trả về để hiển thị “chờ focus” hoặc “chưa thể phát”. Khi hoàn tất, người dùng Stop/Pause hoặc owner rời màn hình, gọi `pauseByUser()/close()` **trước khi release player**.

`wantsToPlay` là ý định hiện tại của người dùng; `resumeOnGain` là việc chờ focus để tiếp tục. Hai biến không đồng nghĩa `isPlaying`. Đây là điểm quan trọng để tránh auto-resume sai.

## 5. Ví dụ diễn biến để tự kiểm tra

| Sự kiện | isPlaying | wantsToPlay | Có resume khi gain? |
|---|---|---|---|
| User Play, focus granted | true | true | Chưa cần |
| Mất focus transient | false | true | Có |
| Trong lúc mất focus, User Pause | false | false | Không |
| Gain đến sau User Pause | false | false | Không |
| Mất focus lâu dài | false | false | Không |

Đừng viết `if (GAIN) player.start()` vô điều kiện. Với stream AudioTrack, demo bài 16 chọn stop worker khi mất focus; controller/adapter cần chờ worker cũ kết thúc trước khi tạo worker mới. Adapter stream đòi hỏi đồng bộ trạng thái thực, không thay `.pause()` bằng `.release()` giữa lúc worker write.

## 6. Duck khác pause

Ducking là giảm âm lượng tạm, ví dụ từ 1.0 xuống 0.2 rồi khôi phục khi gain. Demo chọn `setWillPauseWhenDucked(true)` để luôn nhường bằng pause, dễ kiểm tra. Với nội dung lời nói, pause thường giữ khả năng nghe đủ câu tốt hơn việc giảm âm lượng.

Nếu chọn manual duck, thay chính sách và xử lý CAN_DUCK bằng giảm volume, giữ ý định phát; Gain khôi phục mức volume trước đó, không mặc định luôn 1.0 nếu app có thanh volume riêng. Trên Android 8+ hệ thống có thể tự duck cho trường hợp phù hợp và không gửi callback duck cho app; không giả định callback luôn xuất hiện. Đọc [Audio focus trên các phiên bản Android](https://developer.android.com/media/optimize/audio-focus).

## 7. Android mới và Media3

Từ Android 12, hệ thống có thêm hành vi cưỡng chế như fade-out ở các điều kiện nhất định. App vẫn cần quản lý state/ý định phát. Khi app **target API 35 trở lên**, request focus yêu cầu app đang ở top hoặc có foreground service phù hợp; không đáp ứng có thể nhận FAILED. Không coi request focus là luôn thành công chỉ vì bản Android cũ đã chạy được. Xem [Android 15: audio focus](https://developer.android.com/about/versions/15/behavior-changes-15#audio-focus).

Media3 ExoPlayer có thể quản lý focus khi gọi `setAudioAttributes(attributes, true)` như bài 14. Trong trường hợp đó dùng quản lý tự động, không gắn thêm DemoAudioFocus cho cùng player. Cần kiểm tra `AudioAttributes` đúng usage/content type; framework class và Media3 class không phải cùng kiểu.

Rút tai nghe là tình huống **audio becoming noisy**, khác focus loss. Thiết kế app media cần xử lý cả route change/noisy để tránh tiếng phát bất ngờ qua loa. Xem [Audio becoming noisy](https://developer.android.com/media/platform/output#dont-be-noisy); Media3 có cấu hình hỗ trợ tùy phiên bản.

## Bài tập cho ngày 6 + 7

Ngày đầu: tích hợp MediaPlayer với controller, quan sát GRANTED/FAILED/DELAYED và log focus callback. Ngày sau: thử hai app phát media, app/ngắt âm tạm, và chuỗi “mất transient → User Pause → gain”. Kiểm tra player không tự phát lại trong chuỗi đó.

Kiểm tra cả foreground/background trên thiết bị Android mới, content speech/music và rút tai nghe. Ghi thiết bị/API/target SDK cùng kịch bản; không khẳng định hoạt động mọi Android chỉ từ một máy.

## Tự kiểm tra

1. Request focus trả FAILED có nên vẫn start? — Không.
2. Mất transient rồi User Pause có tự resume? — Không.
3. Thu âm cần focus thay RECORD_AUDIO? — Không.
4. ExoPlayer tự focus có nên thêm request thủ công? — Không cho cùng player.

## Tiêu chí hoàn thành

- [ ] Chọn request theo nhu cầu và phát sau khi được cấp focus.
- [ ] Xử lý permanent/transient/duck/gain rõ ràng.
- [ ] Không tự resume sau User Pause; abandon khi hết nhu cầu.
- [ ] Giải thích giới hạn Android mới và focus tự động của Media3.

[Mục lục](README.md) · Quay lại [đề bài Media Lab](README.md#bài-thực-hành-tổng-hợp-media-lab) để ghép kiến thức.
