# 17 — Thu âm bằng MediaRecorder

## Mục tiêu

Xin quyền microphone, thu âm ra file, xử lý stop quá sớm và giải phóng recorder. Cần bài 00 về permission/thread và bài 15 để phát lại.

## 1. MediaRecorder khác MediaPlayer/AudioTrack

| API | Vai trò |
|---|---|
| MediaPlayer/Media3 | Phát media đã mã hóa |
| AudioTrack | Phát mẫu PCM do app cấp |
| MediaRecorder | Thu và mã hóa âm thanh/video thành file |
| AudioRecord | Thu mẫu âm thanh để app tự xử lý |

Muốn ghi một lời nhắn thành `.m4a`, MediaRecorder là bước bắt đầu phù hợp. Muốn đọc PCM microphone để tính mức âm/thuật toán realtime, đó là bài AudioRecord riêng; không nhầm với AudioTrack.

## 2. Permission và nơi lưu

Thêm vào manifest:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Xin quyền runtime trước khi bắt đầu. Lưu vào filesDir không cần quyền ghi toàn bộ storage. File private chưa xuất hiện trong Gallery/file manager; muốn chia sẻ ra app khác cần FileProvider/content URI.

Từ Android 9, app chạy nền bị hạn chế truy cập microphone. Bài thực hành chỉ thu khi màn hình đang hiển thị; thu nền cần foreground service và tuân thủ các yêu cầu về khởi chạy/quyền/type của phiên bản Android. Xem [MediaRecorder overview](https://developer.android.com/media/platform/mediarecorder).

Audio focus là cơ chế phối hợp **phát âm thanh**, không phải permission mở microphone. Bản ghi có thể bị ảnh hưởng bởi app khác dùng mic/đường âm thanh; vẫn cần xử lý lỗi recorder và tình huống cuộc gọi.

## 3. Thứ tự cấu hình phải đúng

```text
Tạo recorder
→ setAudioSource
→ setOutputFormat
→ setAudioEncoder
→ setOutputFile và các tham số
→ prepare
→ start
→ stop
→ release
```

Không gọi start trước prepare hoặc stop trước start. Đuôi file không quyết định encoding: cấu hình AAC + MPEG_4 mới tạo nội dung tương ứng với file `.m4a`.

## 4. Helper ghi âm

Class dưới dùng trên **một worker duy nhất** để mọi thao tác tuần tự; Activity không truy cập recorder trực tiếp:

```kotlin
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

class DemoRecorder(context: Context) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            MediaRecorder()
        }

    fun start(): File {
        check(recorder == null) { "Đang ghi âm" }
        val file = File(appContext.filesDir, "voice-${UUID.randomUUID()}.m4a")
        val candidate = newRecorder()
        try {
            candidate.setAudioSource(MediaRecorder.AudioSource.MIC)
            candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            candidate.setAudioSamplingRate(44_100)
            candidate.setAudioEncodingBitRate(128_000)
            candidate.setOutputFile(file.absolutePath)
            candidate.prepare()
            candidate.start()
            recorder = candidate // Chỉ đánh dấu ghi khi start thành công.
            output = file
            return file
        } catch (error: Exception) {
            candidate.release()
            file.delete()
            throw error
        }
    }

    fun stop(): File? {
        val current = recorder ?: return null
        val file = output
        recorder = null
        output = null
        var valid = false
        try {
            current.stop()
            valid = file != null && file.length() > 0
        } catch (error: RuntimeException) {
            // stop quá sớm hoặc recorder lỗi có thể chưa có media hợp lệ.
        } finally {
            current.release()
        }
        if (!valid) file?.delete()
        return if (valid) file else null
    }
}
```

`MediaRecorder(Context)` dùng trên API 31+; nhánh dưới giữ khả năng chạy demo API 26. Sampling rate/bitrate là lựa chọn thử nghiệm, không bảo đảm mọi cấu hình đều được mọi thiết bị hỗ trợ. Khi prepare/start thất bại, báo lỗi và chọn cấu hình hỗ trợ thay vì tiếp tục gọi stop như thể đang ghi.

File không rỗng là kiểm tra ban đầu, chưa chứng minh decode thành công. Bài tập sẽ phát lại để xác nhận. Bản thực tế cần lắng nghe lỗi/info của recorder và định nghĩa cách giữ/xóa file khi bị ngắt.

## 5. Kết nối với Activity

Đặt worker/helper và launcher ở cấp class. Layout cần `result`, `startButton`, `stopButton`; init binding trước khi bấm nút:

```kotlin
private val recorderWorker = Executors.newSingleThreadExecutor()
private val demoRecorder by lazy { DemoRecorder(applicationContext) }

private val micPermission = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        startRecording()
    } else binding.result.text = "Chưa có quyền/không còn ở màn hình thu âm"
}

private fun requestRecording() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED) {
        startRecording()
    } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
}

private fun startRecording() {
    binding.startButton.isEnabled = false
    recorderWorker.execute {
        val message = try {
            demoRecorder.start()
            "Đang ghi âm"
        } catch (error: Exception) {
            "Không bắt đầu được ghi âm: ${error.message}"
        }
        runOnUiThread {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                binding.result.text = message
            }
        }
    }
}

private fun stopRecording() {
    recorderWorker.execute {
        val file = demoRecorder.stop()
        runOnUiThread {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                binding.startButton.isEnabled = true
                binding.result.text = file?.let { "Đã lưu ${it.name}" }
                    ?: "Chưa có bản ghi hợp lệ"
            }
        }
    }
}
```

Imports Activity: `java.util.concurrent.Executors`, `android.Manifest`, `android.content.pm.PackageManager`, `androidx.core.content.ContextCompat`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.lifecycle.Lifecycle`. Nút Start gọi requestRecording, Stop gọi stopRecording. Sau start lỗi có thể bấm Stop để về trạng thái cho phép thử lại; app hoàn chỉnh nên trả trạng thái typed thay vì suy ra từ message.

Khi rời màn hình, xếp lệnh stop vào cùng worker; khi Activity hủy, shutdown để các lệnh đã xếp hoàn tất:

```kotlin
override fun onStop() {
    recorderWorker.execute { demoRecorder.stop() }
    super.onStop()
}

override fun onDestroy() {
    recorderWorker.shutdown()
    super.onDestroy()
}
```

Khi quay lại, đặt trạng thái nút phù hợp trong onStart. Không tạo worker mới ở mỗi lần bấm Start và không release recorder ở main thread đồng thời worker đang prepare/stop. Code này cố ý serialize mọi thao tác để tránh cuộc đua đó.

## 6. Pause, resume và trường hợp bị gián đoạn

MediaRecorder có pause/resume trên các API phù hợp, nhưng chỉ gọi khi state cho phép. Pause vẫn dùng cùng bản ghi; stop kết thúc bản ghi. Với intern, hoàn thành start/stop trước rồi bổ sung enum trạng thái `Idle/Preparing/Recording/Paused/Stopping/Error` khi làm UI đầy đủ.

Thử trên thiết bị thật: emulator có thể không đại diện tốt cho microphone, codec và route Bluetooth. Khi người dùng từ chối quyền, cung cấp trạng thái rõ ràng, không gọi start rồi chờ exception như cách xin quyền.

## Bài tập

Ghi 5 giây, stop và phát lại bằng bài 15. Bấm Start rồi Stop gần như ngay lập tức để thử nhánh không có bản ghi hợp lệ. Bấm Home lúc đang ghi, quay lại và kiểm tra mic đã dừng. Thử từ chối quyền; kiểm tra các file lỗi không bị giữ lại.

## Tự kiểm tra

1. Đuôi m4a có tự biến nội dung thành AAC? — Không, cấu hình format/encoder quyết định.
2. Vì sao stop có thể ném RuntimeException? — Chưa có đủ dữ liệu hợp lệ hoặc recorder lỗi.
3. Audio focus có cấp quyền microphone không? — Không.

## Tiêu chí hoàn thành

- [ ] Có permission, chuẩn thứ tự cấu hình và file đầu ra xác định.
- [ ] Start/stop tuần tự, có dọn file lỗi và release.
- [ ] Phát lại được bản ghi và dừng khi rời màn hình demo.

[Mục lục](README.md) · [Bài tiếp: Audio Focus](18-audio-focus.md)

