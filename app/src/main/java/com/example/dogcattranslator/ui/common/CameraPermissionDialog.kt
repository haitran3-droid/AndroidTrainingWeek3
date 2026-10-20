package com.example.dogcattranslator.ui.common

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.dogcattranslator.R
import com.example.dogcattranslator.databinding.DialogCameraPermissionBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Popup theo mockup giải thích vì sao cần camera trước.
 *
 * Luồng: Gate mở dialog → người dùng bấm → gửi Fragment Result về Gate để xin quyền hoặc mở Settings.
 * settings=false hiện Cho phép; true hiện Mở cài đặt.
 *
 * Cần nhớ: popup không tự cấp quyền. settings chỉ là suy luận của app về khả năng xin lại.
 */
class CameraPermissionDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ui = DialogCameraPermissionBinding.inflate(layoutInflater)
        val settings = arguments?.getBoolean("settings") ?: false

        ui.artwork.show("permission_art")

        ui.allowButton.setText(if (settings) R.string.open_settings else R.string.allow)
        ui.allowButton.setOnClickListener {
            // Chỉ gửi ý định; Gate mới mở popup hệ thống hoặc Settings.
            parentFragmentManager.setFragmentResult(RESULT, bundleOf("settings" to settings))
            dismiss()
        }
        ui.laterButton.setOnClickListener { dismiss() }
        return MaterialAlertDialogBuilder(requireContext()).setView(ui.root).create()
    }

    companion object {
        const val RESULT = "camera_permission_action"
        const val TAG = "camera_permission_dialog"
    }
}
