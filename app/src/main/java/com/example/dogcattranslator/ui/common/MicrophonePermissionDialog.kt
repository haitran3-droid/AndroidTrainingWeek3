package com.example.dogcattranslator.ui.common

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.dogcattranslator.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Giải thích micro dùng để ghi âm và chọn tiếng thú cưng phản hồi ở Home.
 *
 * Luồng: Home mở dialog → Cho phép → Fragment Result → Home mở popup quyền hệ thống. Gửi sự kiện giúp
 * dialog không phải gọi trực tiếp hàm HomeFragment.
 *
 * Cần nhớ: đồng ý ở đây chưa cấp RECORD_AUDIO. Dialog riêng là lựa chọn UX của app.
 */
class MicrophonePermissionDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.microphone_permission)
            .setMessage(R.string.microphone_reason)
            .setPositiveButton(R.string.allow) { _, _ ->
                // Đồng ý xin quyền chưa có nghĩa hệ thống đã cấp quyền micro.
                parentFragmentManager.setFragmentResult(RESULT, bundleOf())
            }
            .setNegativeButton(R.string.later, null)
            .create()

    companion object {
        const val RESULT = "microphone_permission_action"
        const val TAG = "microphone_permission_dialog"
    }
}
