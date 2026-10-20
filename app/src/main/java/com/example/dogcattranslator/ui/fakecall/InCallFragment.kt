package com.example.dogcattranslator.ui.fakecall

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.dogcattranslator.R
import com.example.dogcattranslator.camera.CameraPreviewController
import com.example.dogcattranslator.databinding.FragmentInCallBinding
import com.example.dogcattranslator.media.FakeCallVideoManager
import com.example.dogcattranslator.media.rawMediaUri
import com.example.dogcattranslator.ui.common.CameraPermissionGate
import com.example.dogcattranslator.ui.common.formatElapsed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Phối hợp video thú cưng, camera trước thật và đồng hồ trong cuộc gọi giả lập.
 *
 * Luồng: tạo view → đặt mốc giờ lần đầu → onStart mở video/camera → onStop lưu vị trí và giải phóng.
 * ViewModel giữ state; hai controller quản lý video và camera.
 *
 * Cần nhớ: timer không chờ video READY. Nút mic chỉ tắt tiếng video. Camera không ghi hình hay gửi lên
 * mạng; bỏ binding khi hủy view.
 */
class InCallFragment : Fragment(R.layout.fragment_in_call) {
    private var binding: FragmentInCallBinding? = null
    private val viewModel: FakeCallViewModel by viewModels()
    private var video: FakeCallVideoManager? = null
    private var timerJob: Job? = null
    private var camera: CameraPreviewController? = null
    private val permission = CameraPermissionGate(this) { startCamera() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = FragmentInCallBinding.bind(view)
        binding = ui

        // Đặt mốc khi tạo view InCall lần đầu, không chờ video chuẩn bị xong.
        viewModel.startClock()

        ui.cameraFrame.clipToOutline = true
        permission.attach()

        camera = CameraPreviewController(requireContext())

        ui.poster.show("${viewModel.animal.posterKey}_call")
        ui.poster.contentDescription = viewModel.animal.name

        ui.endButton.setOnClickListener {
            findNavController().popBackStack(R.id.fakeCallFragment, false)
        }

        ui.micButton.setOnClickListener {
            viewModel.muted = !viewModel.muted
            // Biểu tượng mic chỉ đổi volume video; app không thu tiếng người gọi.
            video?.player?.volume = if (viewModel.muted) 0f else 1f
            updateMute()
        }
        updateMute()
        updateCameraButton()

        ui.cameraButton.setOnClickListener {
            if (viewModel.cameraEnabled) {
                viewModel.cameraEnabled = false
                camera?.stop()
                showCameraOff()
                updateCameraButton()
            } else permission.request()
        }
    }

    override fun onStart() {
        super.onStart()
        val ui = binding ?: return

        if (viewModel.cameraEnabled) startCamera() else showCameraOff()

        val uri = rawMediaUri(requireContext(), viewModel.animal.videoResId)
        if (uri == null) ui.status.setText(R.string.missing_video)
        else {
            ui.playerView.isVisible = true
            video =
                FakeCallVideoManager(
                    requireContext(),
                    uri,
                    viewModel.videoPosition,
                    viewModel.muted,
                    onStatus = { message ->
                        binding?.status?.text = message?.let(::getString) ?: ""
                    },
                )
            ui.playerView.player = video?.player
        }

        timerJob =
            viewLifecycleOwner.lifecycleScope.launch {
                while (isActive) {
                    ui.timer.text = formatElapsed(viewModel.elapsedMs())
                    delay(250)
                }
            }
    }

    /**
     * Khởi động camera trước và stream vào PreviewView.
     */
    private fun startCamera() {
        val ui = binding ?: return
        if (!permission.hasPermission()) {
            viewModel.cameraEnabled = false
            ui.previewView.isVisible = false
            ui.cameraPlaceholder.isVisible = true
            ui.cameraPlaceholder.setText(R.string.camera_denied)
            updateCameraButton()
            return
        }
        viewModel.cameraEnabled = true
        updateCameraButton()
        ui.previewView.isVisible = true
        camera?.start(viewLifecycleOwner, ui.previewView) { message ->
            binding?.cameraPlaceholder?.apply {
                isVisible = message != null
                text = message?.let(::getString) ?: ""
            }
        }
    }

    /**
     * Ẩn camera trước khi người dùng chủ động tắt camera.
     */
    private fun showCameraOff() {
        val ui = binding ?: return
        ui.previewView.isVisible = false
        ui.cameraPlaceholder.isVisible = true
        ui.cameraPlaceholder.setText(R.string.camera_off)
    }

    /**
     * Cập nhật icon và màu sắc của nút Camera trên thanh công cụ.
     */
    private fun updateCameraButton() {
        binding?.cameraButton?.apply {
            isSelected = viewModel.cameraEnabled
            setImageResource(
                if (viewModel.cameraEnabled) R.drawable.ic_camera else R.drawable.ic_camera_off,
            )
            contentDescription =
                getString(
                    if (viewModel.cameraEnabled) R.string.turn_camera_off
                    else R.string.turn_camera_on,
                )
        }
    }

    /**
     * Cập nhật icon và màu sắc của nút Micro (Mute).
     */
    private fun updateMute() {
        binding?.micButton?.apply {
            isSelected = viewModel.muted
            setImageResource(if (viewModel.muted) R.drawable.ic_mic_off else R.drawable.ic_mic)
            contentDescription =
                getString(if (viewModel.muted) R.string.video_muted else R.string.video_unmuted)
        }
    }

    override fun onStop() {
        camera?.stop()
        timerJob?.cancel()
        timerJob = null
        // Lưu vị trí trước khi release; player mới seek tới vị trí này khi quay lại.
        video?.let { viewModel.videoPosition = it.player.currentPosition }
        binding?.playerView?.player = null
        video?.release()
        video = null
        super.onStop()
    }

    override fun onDestroyView() {
        camera?.stop()
        camera = null
        binding = null
        super.onDestroyView()
    }
}
