# 12 — Crop ảnh từ Camera và Gallery

## Mục tiêu

Nhận URI ảnh từ bài 10/11, mở giao diện crop và lấy URI file đầu ra. Phân biệt crop hiển thị với tạo ảnh mới để lưu/upload.

## 1. Crop là gì?

Crop là chọn một vùng ảnh và bỏ phần ngoài vùng đó. Resize đổi kích thước ảnh. Hai thao tác có thể nối tiếp nhau nhưng không giống nhau.

Ví dụ ảnh 1200 × 800: crop vuông ở giữa lấy vùng 800 × 800; resize sau đó thành 400 × 400. Nếu chỉ ép ảnh 1200 × 800 thành 400 × 400, khuôn mặt có thể bị méo.

`Glide.centerCrop()` ở bài 06 chủ yếu phục vụ hiển thị. Khi upload avatar đã crop, cần file đầu ra chứa vùng người dùng chọn. Android không cung cấp một implicit crop intent được bảo đảm tồn tại trên mọi máy; dùng thư viện có UI hoặc tự xây tính năng.

## 2. Luồng thống nhất cho hai nguồn

```text
Gallery → content URI ─┐
                     ├→ Crop UI → URI ảnh đầu ra → hiển thị/lưu/upload
Camera  → URI ảnh ─────┘
```

Chỉ crop **ảnh**, không đưa URI video vào. Với ảnh camera lưu cacheDir, biết rõ file thuộc app. Với Gallery, dùng grant URI còn hiệu lực; nếu thư viện/provider không tương thích, copy ảnh sang file riêng khi còn quyền rồi crop bản copy.

## 3. Thêm uCrop

Trong `settings.gradle.kts` của project thực hành, bổ sung repository vào khối hiện có, không tạo hai khối trùng:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

Trong module app:

```kotlin
implementation("com.github.yalantis:ucrop:2.2.11")
```

Mốc 2.2.11 theo [uCrop repository](https://github.com/Yalantis/uCrop); kiểm tra yêu cầu SDK/Gradle khi tích hợp. Bài dùng bản thường để bắt đầu. Nếu dùng variant native, kiểm tra riêng ABI và hỗ trợ page size, không chỉ đổi hậu tố dependency rồi coi đã xong.

Thêm Activity vào `<application>` trong manifest:

```xml
<activity
    android:name="com.yalantis.ucrop.UCropActivity"
    android:exported="false"
    android:theme="@style/Theme.AppCompat.Light.NoActionBar" />
```

App demo cần AppCompat. Không bắt buộc khóa portrait trong bài này; thử xoay và lưu trạng thái theo yêu cầu ứng dụng.

## 4. Đăng ký kết quả và mở crop

Đặt launcher ở cấp class Activity. Layout có ImageView `preview` và TextView `result`:

```kotlin
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.UUID

private val cropLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    val intent = result.data
    when {
        result.resultCode == Activity.RESULT_OK && intent != null -> {
            val uri = UCrop.getOutput(intent)
            if (uri != null) {
                Glide.with(this).load(uri).into(binding.preview)
                binding.result.text = "Đã crop ảnh"
                // Lưu/truyền URI này, không upload URI ảnh nguồn nhầm.
            } else binding.result.text = "Không có ảnh đầu ra"
        }
        result.resultCode == UCrop.RESULT_ERROR && intent != null -> {
            val error = UCrop.getError(intent)
            binding.result.text = "Crop thất bại: ${error?.message}"
        }
        else -> binding.result.text = "Bạn đã hủy crop"
    }
}

private fun cropImage(sourceUri: Uri) {
    val destination = Uri.fromFile(
        File(cacheDir, "crop-${UUID.randomUUID()}.jpg")
    )
    val options = UCrop.Options().apply {
        setCompressionFormat(Bitmap.CompressFormat.JPEG)
        setCompressionQuality(90)
    }
    val intent = UCrop.of(sourceUri, destination)
        .withAspectRatio(1f, 1f)
        .withMaxResultSize(1024, 1024)
        .withOptions(options)
        .getIntent(this)
    cropLauncher.launch(intent)
}
```

Gọi `cropImage(uri)` sau callback chọn ảnh hoặc chụp ảnh thành công. Activity crop nằm trong cùng app; khi gửi file sang **app khác**, dùng FileProvider/content URI theo [Sharing files](https://developer.android.com/training/secure-file-sharing).

## 5. Giải thích tham số và đầu ra

- `withAspectRatio(1, 1)` khóa tỉ lệ vuông; `16, 9` cho khung ngang.
- `withMaxResultSize` đặt giới hạn pixel đầu ra, không bảo đảm mọi ảnh được phóng lên đúng 1024 × 1024.
- JPEG quality 90 liên quan nén; nó không khôi phục chi tiết đã mất trong ảnh gốc.
- Đích mới khác nguồn giúp giữ ảnh gốc. Cache là nơi tạm, không phải cam kết lưu vĩnh viễn.
- Khung crop tròn trong UI không tự chứng minh file có nền trong suốt/hình tròn; JPEG không giữ alpha.

Thông số là lựa chọn demo. Xác nhận yêu cầu backend về tỉ lệ, số pixel, định dạng và dung lượng trước khi thiết kế upload.

## 6. Xử lý các trường hợp thực tế

Ảnh camera có thể cần đọc EXIF orientation; ảnh cloud cần thời gian tải; ảnh lớn cần tránh decode toàn bộ vào RAM thủ công. Thư viện giúp xử lý nhiều việc nhưng bạn vẫn phải thử ảnh lớn, xoay, hủy và permission hết hạn.

Nếu người dùng hủy, giữ ảnh avatar trước đó. Nếu lưu lâu dài, copy file crop từ cache sang filesDir hoặc MediaStore tùy nhu cầu. Dọn file tạm do tính năng tạo sau khi không dùng nữa; không xóa ảnh Gallery nguồn của người dùng.

## Bài tập

Chọn một ảnh Gallery và chụp một ảnh Camera, crop cả hai thành vuông. So sánh URI nguồn/đích, width/height và dung lượng file. Hủy crop để chứng minh app không ghi đè avatar cũ. Thử ảnh xoay dọc có EXIF.

## Tự kiểm tra

1. Crop khác resize thế nào? — Crop chọn vùng; resize đổi số pixel.
2. Muốn upload ảnh đã crop dùng URI nào? — URI đầu ra.
3. JPEG có hỗ trợ nền trong suốt? — Không.

## Tiêu chí hoàn thành

- [ ] Hai nguồn Camera/Gallery đi qua cùng luồng crop.
- [ ] Có xử lý thành công, lỗi và hủy.
- [ ] Hiểu tỉ lệ, pixel đầu ra, định dạng và nơi lưu.

[Mục lục](README.md) · [Bài tiếp: Frame realtime](13-camera-frame-realtime.md)

