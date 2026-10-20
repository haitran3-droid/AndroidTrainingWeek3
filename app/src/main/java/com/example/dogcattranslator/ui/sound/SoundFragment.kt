package com.example.dogcattranslator.ui.sound

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dogcattranslator.R
import com.example.dogcattranslator.databinding.FragmentSoundBinding
import com.example.dogcattranslator.media.PlaybackState
import com.example.dogcattranslator.media.PlaybackStatus
import com.example.dogcattranslator.media.SoundPlayerManager
import com.example.dogcattranslator.media.rawMediaUri
import com.example.dogcattranslator.ui.common.MediaCreditsDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Màn nghe tiếng thú cưng: chọn card, phát/tạm dừng và kéo tua.
 *
 * Luồng: card → ViewModel.select → player.prepare → Play → theo dõi trạng thái/tiến độ.
 * SoundSeekBarListener phân biệt thao tác tua với cập nhật từ player.
 *
 * Cần nhớ: tạo player trong onStart, release trong onStop. Back dùng popBackStack về Home có sẵn, tránh
 * thêm Home mới vào lịch sử điều hướng.
 */
class SoundFragment : Fragment(R.layout.fragment_sound) {
    private var binding: FragmentSoundBinding? = null
    private val viewModel: SoundViewModel by viewModels()
    private var player: SoundPlayerManager? = null
    private var playerJob: Job? = null
    private var seekListener: SoundSeekBarListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = FragmentSoundBinding.bind(view)
        binding = ui

        ui.toolbar.inflateMenu(R.menu.media_credits)
        ui.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.media_credits) {
                MediaCreditsDialog.showIfNeeded(childFragmentManager)
                true
            } else {
                false
            }
        }

        ui.toolbar.setNavigationOnClickListener {
            // Trở về Home có sẵn, không thêm một Home mới vào lịch sử điều hướng.
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        val adapter =
            SoundAdapter(viewModel.sounds) { item ->
                viewModel.select(item.id)
                player?.prepare(rawMediaUri(requireContext(), item.audioResId))
            }

        ui.sounds.layoutManager = GridLayoutManager(requireContext(), 3)
        ui.sounds.adapter = adapter

        ui.playButton.setOnClickListener { player?.toggle() }

        seekListener = SoundSeekBarListener { position -> player?.seekTo(position) }
        ui.seekBar.setOnSeekBarChangeListener(seekListener)

        observeUiState(adapter)
    }

    /**
     * Quan sát StateFlow từ ViewModel để cập nhật UI và Adapter.
     */
    private fun observeUiState(adapter: SoundAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selected.collect { item ->
                        val ui = binding ?: return@collect
                        ui.poster.show(item.posterKey)
                        ui.poster.contentDescription = soundDisplayName(requireContext(), item)
                        adapter.selectedId = item.id
                    }
                }
                launch { viewModel.state.collect(::renderPlayback) }
            }
        }
    }

    /**
     * Cập nhật giao diện phát nhạc dựa vào [PlaybackState].
     */
    private fun renderPlayback(state: PlaybackState) {
        val ui = binding ?: return
        val isPlaying = state.status == PlaybackStatus.PLAYING

        ui.playButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        ui.playButton.contentDescription =
            getString(if (isPlaying) R.string.pause else R.string.play)

        ui.playButton.isEnabled =
            state.status !in
                listOf(
                    PlaybackStatus.ERROR,
                    PlaybackStatus.LOADING,
                    PlaybackStatus.IDLE,
                )

        ui.seekBar.isEnabled = state.duration > 0
        ui.seekBar.max = state.duration.coerceAtLeast(1)

        // Khi đang kéo, tiến độ từ player không được giành lại con trượt.
        if (seekListener?.isDragging != true) ui.seekBar.progress = state.position
        ui.status.text = state.message?.let(::getString) ?: ""
    }

    override fun onStart() {
        super.onStart()
        val manager = SoundPlayerManager(requireContext(), viewLifecycleOwner.lifecycleScope)
        player = manager
        // Nạp vị trí đã lưu, nhưng chờ người dùng bấm Play mới phát.
        manager.prepare(
            rawMediaUri(requireContext(), viewModel.selected.value.audioResId),
            position = viewModel.position,
        )
        playerJob =
            viewLifecycleOwner.lifecycleScope.launch {
                manager.state.collect(viewModel::update)
            }
    }

    override fun onStop() {
        // Dừng khi sang màn khác để không phát chồng với Home/FakeCall.
        player?.pause()
        playerJob?.cancel()
        playerJob = null
        player?.release()
        player = null
        seekListener?.cancelDrag()
        super.onStop()
    }

    override fun onDestroyView() {
        binding?.sounds?.adapter = null
        binding?.seekBar?.setOnSeekBarChangeListener(null)
        seekListener = null
        binding = null
        super.onDestroyView()
    }
}
