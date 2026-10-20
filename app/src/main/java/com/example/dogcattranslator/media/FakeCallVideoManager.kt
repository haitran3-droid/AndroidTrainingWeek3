package com.example.dogcattranslator.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.dogcattranslator.R

/**
 * Phát lặp video thú cưng bằng Media3 ExoPlayer trong cuộc gọi giả lập.
 *
 * Luồng: InCall tạo manager → gắn player vào PlayerView → release trong onStop. Không có cuộc gọi mạng
 * hay truyền hình ảnh người dùng.
 *
 * @param context Dùng applicationContext để tạo player.
 * @param uri Nguồn video local.
 * @param position Vị trí phát lại, đơn vị ms.
 * @param muted Tắt tiếng video, không điều khiển microphone thật.
 * @param onStatus Báo ID chuỗi trạng thái; null khi player sẵn sàng.
 */
class FakeCallVideoManager(
    context: Context,
    uri: Uri,
    position: Long,
    muted: Boolean,
    onStatus: (Int?) -> Unit,
) {
    val player =
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)

            repeatMode = Player.REPEAT_MODE_ONE

            volume = if (muted) 0f else 1f

            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> onStatus(R.string.video_loading) // Đang load video
                            Player.STATE_READY -> onStatus(null)                       // Sẵn sàng phát, ẩn thông báo load
                            Player.STATE_IDLE,
                            Player.STATE_ENDED -> Unit
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        onStatus(R.string.video_error)
                    }
                },
            )

            setMediaItem(MediaItem.fromUri(uri))
            seekTo(position)
            prepare()
            playWhenReady = true // Tự động phát ngay khi nạp xong dữ liệu
        }

    /**
     * InCall gọi trong onStop để giải phóng tài nguyên player, gồm bộ giải mã đang sử dụng.
     * Player được tạo lại khi màn hình hoạt động trở lại, theo quy tắc không phát nền của app.
     */
    fun release() {
        player.release()
    }
}
