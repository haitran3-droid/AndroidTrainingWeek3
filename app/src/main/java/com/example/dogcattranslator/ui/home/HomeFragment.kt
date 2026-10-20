package com.example.dogcattranslator.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dogcattranslator.R
import com.example.dogcattranslator.data.model.AnimalType
import com.example.dogcattranslator.databinding.FragmentHomeBinding
import com.example.dogcattranslator.media.AmplitudeAnalysis
import com.example.dogcattranslator.media.SoundPlayerManager
import com.example.dogcattranslator.media.rawMediaUri
import com.example.dogcattranslator.ui.common.MicrophonePermissionDialog
import com.example.dogcattranslator.ui.common.formatElapsed
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Màn Home: xin quyền micro, nhận thao tác và hiển thị kết quả ghi âm giả lập.
 *
 * Luồng: bấm ghi → xin quyền → ViewModel ghi âm → nhận state → phát sound. ViewModel giữ dữ liệu;
 * Fragment giữ view và player audio phản hồi.
 *
 * Cần nhớ: chỉ quan sát state khi view ở STARTED trở lên; onStop hủy ghi và release player. Bỏ binding
 * trong onDestroyView vì Fragment có thể sống lâu hơn view.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {
    private var binding: FragmentHomeBinding? = null

    private val viewModel: HomeViewModel by viewModels()

    private var player: SoundPlayerManager? = null
    private var playerJob: Job? = null
    private var lastPlayedAnalysis: AmplitudeAnalysis? = null

    private val requestMicrophone =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            if (allowed) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    beginRecording()
                }
            } else {
                showMicrophoneDenied()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = FragmentHomeBinding.bind(view)
        binding = ui

        ui.humanPoster.show("human")

        listenForPermissionDialog()

        setupClickListeners(ui)

        observeUiState()
    }

    override fun onStart() {
        super.onStart()
        val manager = SoundPlayerManager(requireContext(), viewLifecycleOwner.lifecycleScope)
        player = manager

        // Quay lại Home không tự phát kết quả cũ; chỉ kết quả phân tích mới được tự phát.
        lastPlayedAnalysis = viewModel.state.value.analysis
        playerJob =
            viewLifecycleOwner.lifecycleScope.launch {
                manager.state.collect { playback ->
                    val errorMessage = playback.message
                    if (errorMessage != null && errorMessage != R.string.missing_audio) {
                        binding?.status?.setText(errorMessage)
                    }
                }
            }
    }

    /**
     * Nhận sự kiện khi người dùng bấm nút "Cho phép" trên MicrophonePermissionDialog.
     */
    private fun listenForPermissionDialog() {
        childFragmentManager.setFragmentResultListener(
            MicrophonePermissionDialog.RESULT,
            viewLifecycleOwner,
        ) { _, _ ->
            preferences().edit().putBoolean(MICROPHONE_ASKED, true).apply()
            requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /**
     * Gán các sự kiện click cho nút Chọn con vật, Nút ghi âm, Nút nghe lại.
     */
    private fun setupClickListeners(ui: FragmentHomeBinding) {
        ui.animalSelector.setOnClickListener { showAnimalSelector() }
        ui.recordButton.setOnClickListener {
            if (viewModel.state.value.status == HomeStatus.RECORDING) {
                viewModel.stopEarly()
            } else {
                ensureMicrophone()
            }
        }
        ui.replayButton.setOnClickListener {
            val sound = viewModel.state.value.selectedSound ?: return@setOnClickListener
            player?.prepare(rawMediaUri(requireContext(), sound.audioResId), autoPlay = true)
        }
    }

    /**
     * Hiển thị hộp thoại Material Dialog cho phép người dùng chọn giữa Chó và Mèo.
     */
    private fun showAnimalSelector() {
        val animalNames = arrayOf(getString(R.string.dog), getString(R.string.cat))
        MaterialAlertDialogBuilder(requireContext())
            .setItems(animalNames) { _, index ->
                player?.release()
                val selectedType = if (index == 0) AnimalType.DOG else AnimalType.CAT
                viewModel.select(selectedType)
            }
            .show()
    }

    /**
     * Nhận state để vẽ UI và phát kết quả mới, chỉ khi vòng đời view ở STARTED trở lên.
     * repeatOnLifecycle hủy phần collect khi view ngừng hoạt động và chạy lại khi view hoạt động lại.
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            // STARTED trở lên thì collect; xuống dưới thì hủy phần collect, lên lại thì nhận state mới nhất.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    render(state)
                    playNewResult(state)
                }
            }
        }
    }

    /**
     * Cập nhật toàn bộ các view trên XML layout dựa trên dữ liệu từ [HomeUiState].
     */
    private fun render(state: HomeUiState) {
        val ui = binding ?: return
        val isRecording = state.status == HomeStatus.RECORDING
        val isBusy = state.status == HomeStatus.PREPARING || state.status == HomeStatus.STOPPING

        val animalLabel = if (state.animalType == AnimalType.DOG) R.string.dog else R.string.cat
        val recordIcon = if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic
        val recordDescription = if (isRecording) R.string.stop_recording else R.string.record

        ui.animalSelector.setText(animalLabel)
        ui.animalSelector.isEnabled = !isRecording && !isBusy
        ui.recordButton.isEnabled = !isBusy
        ui.recordButton.setImageResource(recordIcon)
        ui.recordButton.contentDescription = getString(recordDescription)
        ui.timer.text = formatElapsed(state.elapsedMs)
        ui.status.text = statusText(state)
        ui.replayButton.isVisible =
            state.status == HomeStatus.RESULT && state.selectedSound?.audioResId != null

        if (isRecording) {
            ui.waveform.addPeak(state.lastAmplitude)
        }
    }

    /**
     * Chuyển đổi trạng thái enum thành chuỗi văn bản hướng dẫn thân thiện hiển thị trên màn hình.
     */
    private fun statusText(state: HomeUiState): String {
        return when (state.status) {
            HomeStatus.IDLE -> getString(R.string.record_hint)
            HomeStatus.PREPARING -> getString(R.string.recording_preparing)
            HomeStatus.RECORDING -> getString(R.string.recording_active)
            HomeStatus.STOPPING -> getString(R.string.recording_stopping)
            HomeStatus.RESULT -> {
                val soundName = state.selectedSound?.let { getString(it.label) } ?: ""
                val averagePeak = state.analysis?.averagePeak ?: 0.0
                val result = getString(R.string.translation_result, soundName, averagePeak)
                val message = state.message
                if (message == null) result else result + "\n" + getString(message)
            }
            HomeStatus.SILENT,
            HomeStatus.ERROR -> state.message?.let { getString(it) } ?: ""
        }
    }

    /**
     * Tự động phát âm thanh kết quả bản dịch khi vừa thu âm xong.
     */
    private fun playNewResult(state: HomeUiState) {
        if (state.status != HomeStatus.RESULT) return

        // Một kết quả có thể được nhận lại khi vẽ UI; so cùng instance để không phát lặp.
        if (state.analysis === lastPlayedAnalysis) return

        lastPlayedAnalysis = state.analysis
        val soundUri = rawMediaUri(requireContext(), state.selectedSound?.audioResId)
        player?.prepare(soundUri, autoPlay = true)
    }

    /**
     * Kiểm tra quyền Micro trước khi bắt đầu ghi âm.
     * Xử lý trường hợp bị từ chối vĩnh viễn (Permanently Denied) thì hướng dẫn vào Settings.
     */
    private fun ensureMicrophone() {
        val permission = Manifest.permission.RECORD_AUDIO
        val isGranted =
            ContextCompat.checkSelfPermission(requireContext(), permission) ==
                PackageManager.PERMISSION_GRANTED
        if (isGranted) {
            beginRecording()
            return
        }

        val wasAskedBefore = preferences().getBoolean(MICROPHONE_ASKED, false)
        val isPermanentlyDenied =
            wasAskedBefore && !shouldShowRequestPermissionRationale(permission)
        if (isPermanentlyDenied) {
            viewModel.showError(R.string.permission_settings)
            val root = view ?: return
            Snackbar.make(root, R.string.permission_settings, Snackbar.LENGTH_LONG)
                .setAction(R.string.open_settings) { openSettings() }
                .show()
            return
        }

        val existingDialog = childFragmentManager.findFragmentByTag(MicrophonePermissionDialog.TAG)
        if (!childFragmentManager.isStateSaved && existingDialog == null) {
            MicrophonePermissionDialog().show(childFragmentManager, MicrophonePermissionDialog.TAG)
        }
    }

    /**
     * Hiển thị thông báo khi người dùng từ chối cấp quyền Micro.
     */
    private fun showMicrophoneDenied() {
        viewModel.showError(R.string.microphone_denied)
        val root = view ?: return
        val snackbar = Snackbar.make(root, R.string.microphone_denied, Snackbar.LENGTH_LONG)
        if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            snackbar.setAction(R.string.open_settings) { openSettings() }
        }
        snackbar.show()
    }

    /**
     * Chuẩn bị và bắt đầu quá trình ghi âm.
     */
    private fun beginRecording() {
        player?.release()
        lastPlayedAnalysis = null
        binding?.waveform?.reset()
        viewModel.startRecording()
    }

    private fun preferences() =
        requireContext().getSharedPreferences("permissions", Context.MODE_PRIVATE)

    /**
     * Mở Cài đặt ứng dụng để người dùng tự cấp quyền khi app suy luận không thể xin lại bằng popup.
     */
    private fun openSettings() {
        val packageUri = Uri.parse("package:${requireContext().packageName}")
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }

    override fun onStop() {
        // Đổi tab, xoay máy hoặc background đều có thể gọi onStop; hủy ghi và dừng audio ở đây.
        viewModel.stopAndDiscard()
        playerJob?.cancel()
        playerJob = null
        player?.release()
        player = null
        super.onStop()
    }

    override fun onDestroyView() {
        // Bỏ view cũ vì Fragment có thể còn trong lịch sử điều hướng sau khi view đã bị hủy.
        binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MICROPHONE_ASKED = "microphone_asked"
    }
}
