package com.example.dogcattranslator.ui.home

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.dogcattranslator.BuildConfig
import com.example.dogcattranslator.R
import com.example.dogcattranslator.data.model.AmplitudeLevel
import com.example.dogcattranslator.data.model.AnimalType
import com.example.dogcattranslator.data.repository.DemoMediaRepository
import com.example.dogcattranslator.media.AmplitudeAnalysis
import com.example.dogcattranslator.media.AudioRecorderManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Điều khiển lượt ghi Home và chọn tiếng thú cưng từ kết quả phân tích.
 *
 * Luồng: startRecording → recorder → tiến độ → phân loại → tìm sound. HomeFragment quan sát state và
 * phát audio; ViewModel không giữ View/Fragment.
 *
 * Cần nhớ: recordingJob chặn ghi chồng. stopEarly giữ kết quả; stopAndDiscard hủy khi rời Home.
 * AndroidViewModel cung cấp Application context. SavedStateHandle lưu loài, không lưu cả lượt ghi đang
 * chạy.
 */
class HomeViewModel(application: Application, private val savedState: SavedStateHandle) :
    AndroidViewModel(application) {

    private val mutableState =
        MutableStateFlow(
            HomeUiState(animalType = AnimalType.valueOf(savedState["animalType"] ?: "DOG")),
        )
    val state = mutableState.asStateFlow()

    // Giữ Job để chặn ghi chồng và có thể hủy khi Home ngừng hoạt động.
    private var recordingJob: Job? = null //giữ coroutine của phiên ghi, dùng để chặn ghi chồng và hủy phiên.
    private var recorder: AudioRecorderManager? = null //giữ manager hiện tại để gửi yêu cầu dừng sớm.

    private var discardRecordingResult = false // đánh dấu phiên này phải bị bỏ kết quả, chẳng hạn khi người dùng chuyển tab.

    /**
     * Người dùng chuyển đổi loại động vật (Chó <-> Mèo).
     */
    fun select(type: AnimalType) {
        if (recordingJob != null) return // Đang thu âm thì không cho đổi
        savedState["animalType"] = type.name
        mutableState.value = HomeUiState(animalType = type)
    }

    /**
     * Bắt đầu phiên thu âm tiếng nói để dịch.
     */
    fun startRecording() {
        if (recordingJob != null) return // Đã đang thu âm rồi thì bỏ qua
        val app = getApplication<Application>()

        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            showError(R.string.microphone_unavailable)
            return
        }

        discardRecordingResult = false
        val animalType = state.value.animalType
        mutableState.value = HomeUiState(status = HomeStatus.PREPARING, animalType = animalType)

        // Tạo manager mới mỗi lượt để cờ dừng cũ không ảnh hưởng lượt sau.
        val activeRecorder = AudioRecorderManager(app)
        recorder = activeRecorder

        // Scope tự hủy khi ViewModel bị clear; Home.onStop vẫn cần chủ động hủy lượt ghi.
        recordingJob = viewModelScope.launch {
            try {
                val analysis = activeRecorder.record { elapsed, peak ->
                    // Recorder lấy mẫu trên IO; chuyển về Main trước khi cập nhật state.
                    withContext(Dispatchers.Main.immediate) { updateProgress(elapsed, peak) }
                }
                mutableState.value = state.value.copy(status = HomeStatus.STOPPING)
                showResult(animalType, analysis)
            } catch (cancelled: CancellationException) {
                // Hủy là kết thúc có chủ đích; ném tiếp để không biến thành lỗi ghi âm.
                throw cancelled
            } catch (_: Exception) {
                showError(R.string.recording_error)
            } finally {
                recorder = null
                recordingJob = null
                if (discardRecordingResult) {
                    mutableState.value = HomeUiState(animalType = animalType)
                }
            }
        }
    }

    /**
     * Cập nhật thời gian đã trôi qua và biên độ sóng âm lên giao diện.
     */
    private fun updateProgress(elapsedMs: Long, peak: Int) {
        if (discardRecordingResult || state.value.status == HomeStatus.STOPPING) return
        mutableState.value =
            state.value.copy(
                status = HomeStatus.RECORDING,
                elapsedMs = elapsedMs,
                lastAmplitude = peak,
            )
    }

    /**
     * Hiển thị kết quả phân tích sau khi thu âm xong.
     */
    private fun showResult(animalType: AnimalType, analysis: AmplitudeAnalysis) {
        if (BuildConfig.DEBUG) {
            Log.d(
                "Amplitude",
                "averagePeak=${analysis.averagePeak}, samples=${analysis.sampleCount}, level=${analysis.level}",
            )
        }
        if (analysis.level == AmplitudeLevel.SILENT) {
            mutableState.value =
                state.value.copy(
                    status = HomeStatus.SILENT,
                    analysis = analysis,
                    message = R.string.silent_recording,
                )
            return
        }

        val sound = DemoMediaRepository.soundFor(animalType, analysis.level)
        mutableState.value =
            state.value.copy(
                status = HomeStatus.RESULT,
                analysis = analysis,
                selectedSound = sound,
                message = if (sound?.audioResId == null) R.string.missing_audio else null,
            )
    }

    /**
     * Dừng thu âm sớm (trước mốc 15s) khi người dùng bấm nút Dừng, vẫn giữ lại các mẫu đã thu để dịch.
     */
    fun stopEarly() {
        if (state.value.status != HomeStatus.RECORDING) return
        mutableState.value = state.value.copy(status = HomeStatus.STOPPING)
        recorder?.requestStop()
    }

    /**
     * Hủy bỏ toàn bộ phiên thu âm khi người dùng chuyển sang tab khác (không phát âm thanh dịch ngầm).
     */
    fun stopAndDiscard() {
        if (recordingJob == null) return
        discardRecordingResult = true
        mutableState.value = state.value.copy(status = HomeStatus.STOPPING)
        recordingJob?.cancel()
    }

    /**
     * Cập nhật thông báo lỗi lên State.
     */
    fun showError(message: Int) {
        mutableState.value = state.value.copy(status = HomeStatus.ERROR, message = message)
    }
}
