# 13 — Lấy và chỉnh sửa frame realtime từ Camera

## Mục tiêu

Nhận frame bằng ImageAnalysis, xử lý ngoài main thread, tạo ảnh xám và đóng frame đúng lúc. Cần CameraX bài 11; đây là bài khó hơn, nên chạy preview/chụp thành công trước.

## 1. Frame và backpressure

Video là nhiều ảnh liên tiếp. Camera có thể tạo khoảng 30 frame/giây; thuật toán cần 100 ms/frame chỉ xử lý được khoảng 10 frame/giây. Nếu cứ xếp hàng, kết quả ngày càng trễ.

**Backpressure** là cách xử lý khi bên sản xuất nhanh hơn bên tiêu thụ. Với hiệu ứng realtime, thường chọn `STRATEGY_KEEP_ONLY_LATEST` để bỏ frame cũ và xử lý ảnh mới. `BLOCK_PRODUCER` có thể làm các use case cùng camera bị chặn khi hàng đợi đầy. Đóng `ImageProxy` sau khi dùng để CameraX trả buffer; xem [Image analysis](https://developer.android.com/media/camera/camerax/analyze).

## 2. Phân biệt phân tích và chỉnh preview

ImageAnalysis đưa bản frame cho bạn xử lý. Nếu sửa pixel trong bitmap của analyzer, PreviewView gốc không tự đổi. Bài này hiển thị **ảnh đã xử lý ở ImageView riêng**, để hiểu được pipeline trước khi học GPU/effect cho Preview/VideoCapture.

```text
Camera ─→ PreviewView (ảnh gốc)
       └→ ImageAnalysis → Bitmap → Grayscale → ImageView (ảnh xử lý)
```

Thêm ImageView ID `processedPreview` vào layout bài 11. Tái sử dụng dependency CameraX 1.5.3 và permission đã có.

## 3. Tạo analyzer

Các thuộc tính/hàm dưới đây thuộc Activity camera:

```kotlin
private val analysisExecutor = Executors.newSingleThreadExecutor()
private var imageAnalysis: ImageAnalysis? = null
private var lastProcessedAt = 0L // Chỉ đọc/ghi trong executor một thread.

private fun buildAnalysis(): ImageAnalysis {
    val selector = ResolutionSelector.Builder()
        .setResolutionStrategy(ResolutionStrategy(
            Size(640, 480),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
        ))
        .build()

    return ImageAnalysis.Builder()
        .setResolutionSelector(selector)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build().also { analysis ->
            analysis.setAnalyzer(analysisExecutor) { frame ->
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProcessedAt >= 150) { // Demo khoảng 6–7 fps.
                        lastProcessedAt = now
                        val output = grayscale(frame)
                        runOnUiThread {
                            if (lifecycle.currentState.isAtLeast(
                                Lifecycle.State.STARTED
                            )) {
                                binding.processedPreview.setImageBitmap(output)
                            }
                        }
                    }
                } catch (error: Exception) {
                    Log.e("FrameDemo", "Không xử lý được frame", error)
                } finally {
                    frame.close() // Kể cả bỏ qua frame hoặc xảy ra lỗi.
                }
            }
        }
}
```

Imports: `java.util.concurrent.Executors`, `android.util.Size`, `android.util.Log`, `android.os.SystemClock`, `androidx.lifecycle.Lifecycle`, `androidx.camera.core.ImageAnalysis`, `ImageProxy`, `androidx.camera.core.resolutionselector.ResolutionSelector`, `ResolutionStrategy`.

Kích thước được yêu cầu có fallback; thiết bị có thể chọn độ phân giải khác. Đọc width/height thực tế, đừng hardcode vòng lặp 640 × 480. Xem [ResolutionStrategy](https://developer.android.com/reference/androidx/camera/core/resolutionselector/ResolutionStrategy).

Trong callback setup của `startCamera()` bài 11, tạo và bind cùng các use case:

```kotlin
val analysis = buildAnalysis()
imageAnalysis = analysis
provider.bindToLifecycle(
    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis
)
```

Đoạn này thay cho lệnh bind chỉ có preview/capture. Nếu gọi setup lại, clear/unbind analyzer cũ trước khi thay; không để hai pipeline cùng sở hữu UI.

## 4. Thực sự thay đổi pixel

```kotlin
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix

private fun grayscale(frame: ImageProxy): Bitmap {
    val source = frame.toBitmap()
    try {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (index in pixels.indices) {
            val color = pixels[index]
            val gray = ((Color.red(color) + Color.green(color)
                + Color.blue(color)) / 3).coerceIn(0, 255)
            pixels[index] = Color.argb(Color.alpha(color), gray, gray, gray)
        }

        val grayBitmap = Bitmap.createBitmap(
            pixels, width, height, Bitmap.Config.ARGB_8888
        )
        val rotation = frame.imageInfo.rotationDegrees
        if (rotation == 0) return grayBitmap

        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(
            grayBitmap, 0, 0, width, height, matrix, true
        )
        if (rotated !== grayBitmap) grayBitmap.recycle()
        return rotated
    } finally {
        source.recycle() // Chỉ bitmap tạm do hàm này tạo, chưa đưa lên UI.
    }
}
```

Trung bình RGB là công thức dễ hiểu cho demo; độ sáng cảm nhận thực tế thường dùng trọng số màu. `ImageProxy.toBitmap()` có từ CameraX 1.3.0 và hỗ trợ các định dạng được tài liệu nêu, gồm RGBA/YUV/JPEG; xem [ImageProxy.toBitmap](https://developer.android.com/reference/androidx/camera/core/ImageProxy#toBitmap()). Bài dùng method đó để tránh tự nối byte/plane sai.

Nếu đọc plane thủ công, phải hiểu **rowStride** (byte giữa hai hàng), **pixelStride** (byte giữa hai pixel) và định dạng YUV/RGBA. Buffer không mặc định là một file JPEG. Hướng ảnh còn phụ thuộc rotation; camera trước có thể cần mirror tùy cách hiển thị. Ví dụ chưa mô phỏng crop/mirror của PreviewView gốc.

## 5. Dọn và tối ưu

Trong `onDestroy`, gọi `imageAnalysis?.clearAnalyzer()` và `analysisExecutor.shutdown()`; lifecycle binding dừng camera. `shutdown()` để tác vụ đang chạy kết thúc và đóng frame trong finally; không đóng/recycle bitmap đang được UI sử dụng từ thread khác.

Ví dụ tạo bitmap/array cho mỗi lần xử lý để code dễ đọc, phù hợp demo độ phân giải thấp và tốc độ giới hạn. App hiệu ứng 30 fps nên tái sử dụng buffer, giảm allocation và cân nhắc GPU. Throttle ở đây giúp học, không bảo đảm hàng đợi UI luôn có giới hạn; bản thực tế cần cơ chế chỉ giữ kết quả mới nhất khi main thread chậm.

Nếu dùng SDK AI bất đồng bộ, không đặt `frame.close()` ngay sau lệnh “bắt đầu xử lý” rồi để SDK còn đọc frame. Đóng ở callback hoàn tất, hoặc copy dữ liệu riêng trước khi đóng.

## Bài tập

Hiển thị ảnh gốc và ảnh xám song song. Đo thời gian xử lý bằng elapsedRealtime. Bỏ throttle để quan sát CPU/GC; sau đó bật lại. Xoay máy, bấm Home và quay lại để kiểm tra orientation/lifecycle. Thử cố tình bỏ close trên app demo và giải thích preview/analyzer bị kẹt.

## Tự kiểm tra

1. Vì sao frame.close cần finally? — Mọi nhánh đều phải trả buffer.
2. Sửa bitmap analyzer có đổi PreviewView gốc? — Không.
3. RGBA/YUV buffer có phải byte JPEG? — Không.

## Tiêu chí hoàn thành

- [ ] Có hiệu ứng đổi pixel thực tế, không chỉ đọc frame.
- [ ] Xử lý ngoài main thread, cập nhật UI trên main thread.
- [ ] Đóng mọi frame, hiểu backpressure và các giới hạn demo.

[Mục lục](README.md) · [Bài tiếp: ExoPlayer](14-media3-exoplayer.md)

