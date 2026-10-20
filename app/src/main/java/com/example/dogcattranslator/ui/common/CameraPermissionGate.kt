package com.example.dogcattranslator.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.example.dogcattranslator.R
import com.google.android.material.snackbar.Snackbar

/**
 * Dùng chung luồng kiểm tra và yêu cầu quyền camera cho các màn FakeCall.
 *
 * Luồng: request → có quyền thì gọi granted; chưa có thì giải thích → xin quyền hệ thống. Nếu app suy
 * luận bị chặn, hướng dẫn mở Settings.
 *
 * Cần nhớ: tạo Gate trong thuộc tính Fragment để đăng ký kết quả sớm. Gọi attach trong onViewCreated.
 * granted chạy khi đã có quyền hoặc vừa được hệ thống cấp.
 */
class CameraPermissionGate(private val fragment: Fragment, private val granted: () -> Unit) {

    private val launcher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            if (allowed) granted() else showPermissionDenied()
        }

    /**
     * Hiển thị Snackbar thông báo từ chối quyền, kèm nút dẫn tới Cài đặt nếu bị chặn vĩnh viễn.
     */
    private fun showPermissionDenied() {
        val view = fragment.view ?: return
        val isPermanentlyDenied =
            !fragment.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        val message =
            if (isPermanentlyDenied) R.string.permission_settings else R.string.camera_denied
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        if (isPermanentlyDenied) {
            snackbar.setAction(R.string.open_settings) { openSettings() }
        }
        snackbar.show()
    }

    /**
     * Gắn bộ lắng nghe kết quả từ CameraPermissionDialog vào Fragment (gọi trong onViewCreated).
     */
    fun attach() {
        fragment.childFragmentManager.setFragmentResultListener(
            CameraPermissionDialog.RESULT,
            fragment.viewLifecycleOwner,
        ) { _, result ->
            if (result.getBoolean("settings")) openSettings()
            else {
                preferences().edit().putBoolean("camera_asked", true).apply()
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * Bắt đầu yêu cầu quyền Camera:
     * - Nếu đã có quyền: Gọi ngay `granted()`.
     * - Nếu chưa: Mở `CameraPermissionDialog` để người dùng xác nhận trước.
     */
    fun request() {
        if (hasPermission()) {
            granted()
            return
        }
        if (
            fragment.childFragmentManager.isStateSaved ||
                fragment.childFragmentManager.findFragmentByTag(CameraPermissionDialog.TAG) != null
        )
            return

        // Suy luận từ lịch sử và rationale (cần giải thích quyền), không đọc checkbox Không hỏi lại.
        val blocked =
            preferences().getBoolean("camera_asked", false) &&
                !fragment.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        CameraPermissionDialog()
            .apply { arguments = bundleOf("settings" to blocked) }
            .show(fragment.childFragmentManager, CameraPermissionDialog.TAG)
    }

    /**
     * Kiểm tra nhanh trạng thái cấp quyền Camera hiện tại.
     */
    fun hasPermission() =
        ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun preferences() =
        fragment.requireContext().getSharedPreferences("permissions", Context.MODE_PRIVATE)

    /**
     * Chuyển hướng người dùng sang trang Cài đặt chi tiết của ứng dụng trong hệ thống Android.
     */
    private fun openSettings() {
        fragment.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${fragment.requireContext().packageName}"),
            ),
        )
    }
}
