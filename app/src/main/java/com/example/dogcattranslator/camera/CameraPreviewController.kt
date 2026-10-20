package com.example.dogcattranslator.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.example.dogcattranslator.R

/**
 * Hiển thị camera trước thật trong khung nhỏ của InCall.
 *
 * Luồng: xin quyền ở màn hình → start → lấy CameraProvider bất đồng bộ → gắn Preview vào PreviewView và
 * vòng đời view → stop khi rời màn hoặc tắt camera.
 *
 * Cần nhớ: gọi start/stop trên Main, truyền viewLifecycleOwner. ID yêu cầu giúp bỏ callback cũ sau
 * stop. Chỉ tháo Preview của lớp này, không tháo toàn bộ camera use case của app.
 */
class CameraPreviewController(context: Context) {
    private val appContext = context.applicationContext

    private var currentRequestId = 0

    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var cameraInfo: CameraInfo? = null
    private var cameraObserver: Observer<CameraState>? = null
    private var previewView: PreviewView? = null
    private var streamObserver: Observer<PreviewView.StreamState>? = null

    /**
     * Bắt đầu mở và hiển thị camera trước lên [PreviewView].
     * @param owner LifecycleOwner quản lý camera; InCall truyền viewLifecycleOwner của Fragment.
     * @param view View hiển thị hình ảnh từ camera trên giao diện XML.
     * @param status Callback thông báo trạng thái hoặc mã lỗi (String Resource ID), null nếu đang hoạt
     * động tốt.
     */
    fun start(owner: LifecycleOwner, view: PreviewView, status: (Int?) -> Unit) {
        stop() // Dọn dẹp phiên trước nếu có
        if (!hasCameraPermission()) {
            status(R.string.camera_denied)
            return
        }
        val startRequestId = currentRequestId
        status(R.string.camera_loading)

        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener(
            {
                if (
                    // Bỏ yêu cầu cũ hoặc view chưa hoạt động, tránh mở camera sau khi đã rời màn hình.
                    startRequestId != currentRequestId ||
                        !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                )
                    return@addListener
                try {
                    if (!hasCameraPermission()) {
                        status(R.string.camera_denied)
                        return@addListener
                    }
                    val cameras = future.get()
                    if (!cameras.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        status(R.string.camera_unavailable)
                        return@addListener
                    }

                    val useCase = Preview.Builder().build()
                    useCase.setSurfaceProvider(view.surfaceProvider)
                    provider = cameras
                    preview = useCase

                    val camera =
                        cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
                    cameraInfo = camera.cameraInfo

                    val cameraStateObserver =
                        Observer<CameraState> { state ->
                            if (state.error != null) status(R.string.camera_error)
                        }
                    cameraObserver = cameraStateObserver
                    camera.cameraInfo.cameraState.observe(owner, cameraStateObserver)

                    previewView = view
                    val previewStreamObserver =
                        Observer<PreviewView.StreamState> { state ->
                            // Đã bind chưa chắc đã thấy hình; chỉ ẩn thông báo khi PreviewView báo đang truyền khung hình.
                            if (state == PreviewView.StreamState.STREAMING) status(null)
                        }
                    streamObserver = previewStreamObserver
                    view.previewStreamState.observe(owner, previewStreamObserver)
                } catch (_: Exception) {
                    stop()
                    status(R.string.camera_error)
                }
            },
            ContextCompat.getMainExecutor(appContext), // Chạy callback trên Main Thread để cập nhật UI an toàn
        )
    }

    /**
     * Kiểm tra xem quyền truy cập Camera đã được người dùng cấp chưa.
     */
    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Dừng camera, hủy các observer và giải phóng kết nối.
     */
    fun stop() {
        currentRequestId++ // Tăng ID để vô hiệu hóa bất kỳ callback bất đồng bộ nào đang chạy dở dang
        cameraObserver?.let { cameraInfo?.cameraState?.removeObserver(it) }
        streamObserver?.let { previewView?.previewStreamState?.removeObserver(it) }
        preview?.let { provider?.unbind(it) }
        cameraObserver = null
        streamObserver = null
        cameraInfo = null
        previewView = null
        preview = null
        provider = null
    }
}
