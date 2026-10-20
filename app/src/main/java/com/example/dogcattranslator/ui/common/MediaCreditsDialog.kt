package com.example.dogcattranslator.ui.common

import android.app.Dialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.dogcattranslator.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Hiển thị nguồn và thông tin bản quyền media ngay trong app offline.
 *
 * Luồng: menu Sound → đọc assets/media-credits.txt → biến URL thành link bấm được. DialogFragment quản
 * lý việc tạo lại dialog theo vòng đời màn hình.
 *
 * Cần nhớ: use đóng luồng đọc kể cả khi lỗi. Tag giảm mở lại dialog đã ghi nhận; show bất đồng bộ nên
 * chưa chặn mọi lần gọi trùng đang chờ.
 */
class MediaCreditsDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val credits =
            requireContext().assets.open("media-credits.txt").bufferedReader().use {
                it.readText()
            }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.media_credits)
            .setMessage(credits)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<TextView>(android.R.id.message)?.let { message ->
            Linkify.addLinks(message, Linkify.WEB_URLS)
            message.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    companion object {
        private const val TAG = "media_credits"

        /**
         * Hiển thị dialog an toàn:
         * - Kiểm tra `!manager.isStateSaved`: Tránh lỗi crash nổi tiếng `IllegalStateException: Can not
         * perform this action after onSaveInstanceState`.
         * - Kiểm tra tag để tránh mở lại dialog đã được FragmentManager ghi nhận.
         * show() commit bất đồng bộ: kiểm tra này không chặn mọi lần gọi trùng khi transaction còn chờ.
         */
        fun showIfNeeded(manager: FragmentManager) {
            // Không thêm sau khi state đã lưu; tag chỉ thấy transaction đã được thực thi.
            if (!manager.isStateSaved && manager.findFragmentByTag(TAG) == null) {
                MediaCreditsDialog().show(manager, TAG)
            }
        }
    }
}
