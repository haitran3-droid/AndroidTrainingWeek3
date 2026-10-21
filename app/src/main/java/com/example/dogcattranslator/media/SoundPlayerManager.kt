package com.example.dogcattranslator.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.example.dogcattranslator.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Trạng thái SoundPlayerManager gửi cho UI, không phải toàn bộ state machine của MediaPlayer.
 *
 * Luồng thường gặp: IDLE → LOADING → READY → PLAYING ↔ PAUSED → COMPLETED.
 *
 * ERROR dùng cho lỗi media; Audio Focus bị từ chối chỉ thêm message, giữ nguyên status.
 */
enum class PlaybackStatus {
    IDLE, //chưa nạp âm thanh
    LOADING, // đang chuẩn bị
    READY, // chuẩn bị xong, có thể phát
    PLAYING, // đang phát
    PAUSED, // đã tạm dừng
    COMPLETED, // đã phát hết
    ERROR, // có lỗi
}

/**
 * Ảnh chụp trạng thái phát để Fragment vẽ nút Play/Pause và thanh tiến độ.
 *
 * @property status Bước hiện tại: chuẩn bị, phát, tạm dừng, hoàn tất hoặc lỗi.
 * @property position Vị trí hiện tại, đơn vị mili-giây (ms).
 * @property duration Tổng thời lượng, đơn vị ms.
 * @property message ID chuỗi cảnh báo/lỗi; null nếu không có.
 */
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val position: Int = 0,
    val duration: Int = 0,
    val message: Int? = null,
)

/**
 * Quản lý một MediaPlayer để phát tiếng thú cưng ở Home và Sound.
 *
 * Luồng: prepare → OnPrepared → play/pause/seek → release khi rời màn hình. Fragment quan sát
 * StateFlow: luồng dữ liệu giữ trạng thái mới nhất.
 *
 * Cần nhớ: gọi hàm và truyền CoroutineScope chạy trên Main (luồng giao diện). prepareAsync chuẩn bị bất
 * đồng bộ; chỉ phát khi sẵn sàng. Audio Focus là ưu tiên âm thanh, không phải quyền xin bằng popup.
 * release không tự reset snapshot state.
 */
class SoundPlayerManager(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Manager sửa MutableStateFlow; UI nhận StateFlow chỉ đọc qua asStateFlow.
    private val mutableState = MutableStateFlow(PlaybackState())
    val state = mutableState.asStateFlow()

    private var player: MediaPlayer? = null // đối tượng thực sự phát ra âm thanh
    private var progressJob: Job? = null //coroutine định kì cập nhập vị trí phát
    private var isPrepared = false // player đã chuẩn bị xong chưa
    private var playWhenReady = false // có muốn tự phát ngay khi chuẩn bị xong không
    
    private val focusRequest =
        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                if (
                    change == AudioManager.AUDIOFOCUS_LOSS ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                )
                    pause()
                if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
                    player?.setVolume(0.2f, 0.2f)
                if (change == AudioManager.AUDIOFOCUS_GAIN) player?.setVolume(1f, 1f)
            }
            .build()

    /**
     * Nạp file âm thanh chuẩn bị phát.
     * @param uri Đường dẫn tài nguyên audio.
     * @param autoPlay Tự động phát ngay sau khi nạp xong nếu đặt là true.
     * @param position Vị trí mili-giây cần tua đến khi vừa nạp xong.
     */
    fun prepare(uri: Uri?, autoPlay: Boolean = false, position: Int = 0) {
        release() // Giải phóng đối tượng MediaPlayer cũ nếu đang có
        if (uri == null) {
            mutableState.value =
                PlaybackState(PlaybackStatus.ERROR, message = R.string.missing_audio)
            return
        }
        playWhenReady = autoPlay
        mutableState.value = PlaybackState(PlaybackStatus.LOADING)
        val next = MediaPlayer()
        player = next
        try {
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            next.setOnPreparedListener {
                // Sound cũ có thể chuẩn bị xong sau sound mới; bỏ callback không còn thuộc player hiện tại.
                if (player !== it) return@setOnPreparedListener
                isPrepared = true
                if (position > 0) it.seekTo(position.coerceIn(0, it.duration))
                publish(PlaybackStatus.READY)
                if (playWhenReady) play()
            }
            next.setOnCompletionListener {
                if (player === it) {
                    progressJob?.cancel()
                    publish(PlaybackStatus.COMPLETED)
                    AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)
                }
            }
            next.setOnErrorListener { failed, _, _ ->
                if (player === failed) fail(R.string.audio_error)
                true
            }
            next.setDataSource(appContext, uri)
            // Không chặn Main để chờ chuẩn bị. OnPrepared quyết định phát ngay hay chỉ chuyển READY.
            next.prepareAsync()
        } catch (_: Exception) {
            fail(R.string.audio_error)
        }
    }

    /**
     * Chuyển đổi giữa Phát và Tạm dừng; khi LOADING thì đổi ý định phát sau khi chuẩn bị xong.
     */
    fun toggle() {
        when (state.value.status) {
            PlaybackStatus.PLAYING -> pause()
            PlaybackStatus.LOADING -> playWhenReady = !playWhenReady
            PlaybackStatus.READY,
            PlaybackStatus.PAUSED,
            PlaybackStatus.COMPLETED -> play()
            else -> Unit
        }
    }

    /**
     * Bắt đầu phát âm thanh.
     */
    fun play() {
        val current = player ?: return
        if (!isPrepared) {
            playWhenReady = true
            return
        }
        if (
            // Chưa có ưu tiên âm thanh thì thêm message và chưa start; đây không phải quyền xin bằng popup.
            AudioManagerCompat.requestAudioFocus(audioManager, focusRequest) !=
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            mutableState.value = state.value.copy(message = R.string.audio_focus_denied)
            return
        }
        try {
            if (state.value.status == PlaybackStatus.COMPLETED) current.seekTo(0)
            current.start()
            publish(PlaybackStatus.PLAYING)

            progressJob?.cancel()
            // Đọc vị trí khoảng 250 ms/lần; hủy job khi pause, hoàn tất hoặc release để không cập nhật thừa.
            progressJob = scope.launch {
                while (isActive && player === current) {
                    publish(PlaybackStatus.PLAYING)
                    delay(250)
                }
            }
        } catch (_: IllegalStateException) {
            fail(R.string.audio_error)
        }
    }

    /**
     * Tạm dừng phát âm thanh.
     */
    fun pause() {
        playWhenReady = false
        if (state.value.status != PlaybackStatus.PLAYING) return
        try {
            player?.pause()
            publish(PlaybackStatus.PAUSED)
        } catch (_: IllegalStateException) {
            fail(R.string.audio_error)
        }
        progressJob?.cancel()
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)
    }

    /**
     * Tua tới vị trí thời gian bất kỳ (ms) khi người dùng kéo thanh trượt SeekBar.
     */
    fun seekTo(position: Int) {
        if (!isPrepared) return
        try {
            val target = position.coerceIn(0, state.value.duration)
            player?.seekTo(target)
            // Sau khi nghe hết, tua là chọn một điểm phát mới; Play không được reset điểm đó.
            mutableState.value =
                state.value.copy(
                    position = target,
                    status =
                        if (state.value.status == PlaybackStatus.COMPLETED) PlaybackStatus.PAUSED
                        else state.value.status,
                )
        } catch (_: IllegalStateException) {
            fail(R.string.audio_error)
        }
    }

    /**
     * Cập nhật trạng thái mới kèm thời lượng hiện tại vào StateFlow.
     */
    private fun publish(status: PlaybackStatus) {
        val current = player ?: return
        try {
            mutableState.value = PlaybackState(status, current.currentPosition, current.duration)
        } catch (_: IllegalStateException) {
            fail(R.string.audio_error)
        }
    }

    /**
     * Xử lý khi xảy ra lỗi phát nhạc.
     */
    private fun fail(message: Int) {
        release()
        mutableState.value = PlaybackState(PlaybackStatus.ERROR, message = message)
    }

    /**
     * Dọn dẹp và giải phóng tài nguyên.
     * Home/Sound gọi khi onStop để phù hợp quy tắc không phát nền của project này.
     * Hủy progress job và release player; StateFlow vẫn giữ snapshot trước đó cho đến lần cập nhật mới.
     */
    fun release() {
        progressJob?.cancel()
        progressJob = null
        val oldPlayer = player
        player = null
        isPrepared = false
        playWhenReady = false
        oldPlayer?.setOnPreparedListener(null)
        oldPlayer?.setOnCompletionListener(null)
        oldPlayer?.setOnErrorListener(null)
        oldPlayer?.release()
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)
    }
}

/**
 * Hàm tiện ích chuyển đổi `resourceId` trong thư mục `res/raw` thành đường dẫn [Uri] hợp lệ của
 * Android.
 * Định dạng: `android.resource://<package_name>/<res_id>`
 */
fun rawMediaUri(context: Context, resourceId: Int?): Uri? = resourceId?.let {
    Uri.parse("android.resource://${context.packageName}/$it")
}
