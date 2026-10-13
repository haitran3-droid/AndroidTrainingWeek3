# 11 — CameraX: preview và chụp ảnh

## Mục tiêu

Xin quyền camera, hiển thị preview, chụp ra file và hiểu việc gắn camera với lifecycle. Cần bài 00 về permission và bài 10 về URI.

## 1. Những use case của CameraX

CameraX hỗ trợ tổ chức camera thành các use case:

- `Preview`: hiển thị thứ camera đang nhìn thấy.
- `ImageCapture`: chụp ảnh để lưu/xử lý.
- `ImageAnalysis`: đưa frame cho thuật toán, học ở bài 13.
- `VideoCapture`: ghi video, khác ImageCapture.

Preview giống cửa sổ nhìn ra ngoài; ImageCapture giống bấm lưu một ảnh. Có preview không tự tạo file ảnh. CameraX giúp xử lý tương thích thiết bị nhưng bạn vẫn cần permission, lifecycle và xử lý lỗi.

## 2. Dependency, manifest và layout

```kotlin
val cameraVersion = "1.5.3"
implementation("androidx.camera:camera-camera2:$cameraVersion")
implementation("androidx.camera:camera-lifecycle:$cameraVersion")
implementation("androidx.camera:camera-view:$cameraVersion")
```

Giữ cùng version cho các module; đây là mốc có trong [CameraX release notes](https://developer.android.com/jetpack/androidx/releases/camera). Thêm CAMERA vào manifest. Nếu app vẫn dùng được khi không có camera, khai báo `<uses-feature android:name="android.hardware.camera.any" android:required="false" />` và xử lý không có thiết bị camera.

Ví dụ layout đơn giản `activity_camera.xml`:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <Button
        android:id="@+id/captureButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Chụp ảnh" />

    <TextView
        android:id="@+id/result"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
</LinearLayout>
```

Chữ hardcoded ở XML này chỉ để nhìn nhanh demo; chuyển sang string resources khi hoàn thiện. Áp dụng insets từ bài 01.

## 3. Xin quyền rồi mới mở camera

Đặt ở cấp class Activity, sau khi chuẩn bị `ActivityCameraBinding`:

```kotlin
private val cameraPermission = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) startCamera()
    else binding.result.text = "Cần quyền camera để chụp"
}

private fun ensureCameraPermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
        startCamera()
    } else {
        cameraPermission.launch(Manifest.permission.CAMERA)
    }
}
```

Trong `onCreate`, inflate binding, `setContentView`, gắn nút `captureButton` với `takePhoto()`, rồi gọi `ensureCameraPermission()`. Người dùng từ chối thì cho UI giải thích và thao tác lại có chủ đích; không lặp popup liên tục. Xem [Request permissions](https://developer.android.com/training/permissions/requesting).

## 4. Bind Preview và ImageCapture

```kotlin
private var imageCapture: ImageCapture? = null

private fun startCamera() {
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({
        if (isDestroyed) return@addListener
        try {
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            provider.unbindAll() // Demo này sở hữu toàn bộ use case camera.
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
            )
            imageCapture = capture
            binding.result.text = "Camera sẵn sàng"
        } catch (error: Exception) {
            imageCapture = null
            binding.result.text = "Không mở được camera"
        }
    }, ContextCompat.getMainExecutor(this))
}
```

`future.get()` chạy khi future đã hoàn tất, không phải chờ blocking trước callback. `bindToLifecycle(this, ...)` gắn use case với Activity để camera hoạt động theo trạng thái hiển thị. Nếu ở Fragment, chọn `viewLifecycleOwner` sau khi View đã tạo. `unbindAll()` chỉ phù hợp demo sở hữu các use case; app có nhiều owner nên unbind đúng use case. Xem [CameraX Preview](https://developer.android.com/media/camera/camerax/preview).

## 5. Chụp ra file

```kotlin
private fun takePhoto() {
    val capture = imageCapture ?: return
    binding.captureButton.isEnabled = false
    capture.targetRotation = binding.previewView.display.rotation
    val file = File(cacheDir, "photo-${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()

    capture.takePicture(options, ContextCompat.getMainExecutor(this),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                if (isDestroyed) return
                binding.captureButton.isEnabled = true
                binding.result.text = "Đã chụp ${file.name}"
                val photoUri = Uri.fromFile(file)
                // Có thể đưa photoUri vào crop ở bài 12, trong cùng app.
            }

            override fun onError(error: ImageCaptureException) {
                file.delete() // File demo chưa chụp xong.
                if (isDestroyed) return
                binding.captureButton.isEnabled = true
                binding.result.text = "Chụp ảnh thất bại"
            }
        })
}
```

Imports chính: `android.Manifest`, `android.content.pm.PackageManager`, `android.net.Uri`, `java.io.File`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.core.content.ContextCompat`, `androidx.camera.core.CameraSelector`, `Preview`, `ImageCapture`, `ImageCaptureException`, `androidx.camera.lifecycle.ProcessCameraProvider`.

Lưu file private/cache không cần quyền storage rộng. File cache này chỉ là ảnh tạm; muốn ảnh tồn tại trong Gallery, lưu qua MediaStore. Với đích File, `savedUri` có thể null nên ví dụ tạo URI từ file mình sở hữu. Không chia sẻ `file://` ra app khác; dùng FileProvider/content URI và grant phù hợp. Xem [Capture an image](https://developer.android.com/media/camera/camerax/take-photo) và [Share files](https://developer.android.com/training/secure-file-sharing).

## 6. Những điều dễ nhầm

Preview có thể crop theo vùng View; ảnh chụp có tỉ lệ/độ phân giải khác. Không kết luận ảnh chụp sai vì không giống toàn bộ vùng preview. Kiểm tra EXIF orientation nếu thư viện đọc ảnh không xoay đúng. Camera trước có thể hiển thị mirror; file đầu ra và preview cần thống nhất theo yêu cầu sản phẩm.

## Bài tập

Mở preview, từ chối rồi cấp quyền, chụp ba ảnh. Xoay thiết bị và kiểm tra hướng ảnh. Bấm Home/quay lại để xác nhận camera theo lifecycle; đổi DEFAULT_BACK_CAMERA sang FRONT nếu thiết bị hỗ trợ.

## Tự kiểm tra

1. Preview có tự lưu ảnh? — Không.
2. Chụp vào cacheDir cần READ_MEDIA_IMAGES? — Không.
3. Gallery không thấy ảnh cache là lỗi camera? — Không, đó là nơi lưu private.

## Tiêu chí hoàn thành

- [ ] Xin quyền đúng thời điểm và xử lý từ chối.
- [ ] Preview/chụp hoạt động theo lifecycle.
- [ ] Biết file được lưu ở đâu, URI nào dùng để crop/chia sẻ.

[Mục lục](README.md) · [Bài tiếp: Crop ảnh](12-crop-anh-camera-gallery.md)
