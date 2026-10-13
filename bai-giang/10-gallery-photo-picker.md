# 10 — Lấy ảnh và video từ Gallery bằng Photo Picker

## Mục tiêu

Cho người dùng chọn ảnh/video, nhận URI, đọc dữ liệu đúng cách và xử lý hủy/quyền truy cập. Cần bài 00; dùng Glide bài 06 để hiển thị ảnh.

## 1. App cần một ảnh hay cần đọc cả thư viện?

Đổi avatar chỉ cần ảnh người dùng chọn, không cần quyền đọc toàn bộ thư viện. Photo Picker cho phép người dùng cấp quyền với media được chọn. Activity contract hỗ trợ fallback sang cơ chế chọn tài liệu nếu picker không có; cần `androidx.activity` 1.7.0 trở lên. Xem [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker).

Nếu project đã có Activity đủ mới thì dùng version đó. Nếu thiếu, một mốc ví dụ là `implementation("androidx.activity:activity-ktx:1.10.1")`; kiểm tra compile SDK của dự án và [release Activity](https://developer.android.com/jetpack/androidx/releases/activity).

## 2. Chọn một ảnh hoặc video

Đặt launcher ở cấp class của Activity, đăng ký trước khi lifecycle đã STARTED. Layout cần ImageView `preview` và TextView `result`:

```kotlin
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide

private val pickMedia = registerForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri ->
    if (uri == null) {
        binding.result.text = "Bạn chưa chọn media"
    } else {
        val type = contentResolver.getType(uri).orEmpty()
        binding.result.text = "Đã chọn: $type"
        if (type.startsWith("image/")) {
            Glide.with(this).load(uri).into(binding.preview)
        } else {
            // Với video, truyền uri sang player ở bài 14.
            binding.result.text = "Đã chọn video; URI sẵn sàng để phát"
        }
    }
}
```

Trong callback bấm nút, dùng **một** lệnh tương ứng:

```kotlin
pickMedia.launch(PickVisualMediaRequest(
    ActivityResultContracts.PickVisualMedia.ImageOnly
))

// Nếu nút chọn video: dùng VideoOnly.
// Nếu nút cho cả hai: dùng ImageAndVideo.
```

`uri == null` là người dùng hủy, không phải lỗi mạng. Một số provider không báo MIME type; đừng suy ra loại file bằng việc ghép URI thành đường dẫn rồi đọc đuôi file. MIME và nội dung thực tế đều cần được kiểm tra khi xử lý/upload.

## 3. Chọn nhiều media

```kotlin
private val pickMany = registerForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(5)
) { uris ->
    // Fallback có thể không áp dụng giới hạn picker.
    val accepted = uris.take(5)
    binding.result.text = "Chọn ${accepted.size} media để xử lý"
}
```

Launch bằng `PickVisualMediaRequest(ImageAndVideo)` tương tự trên. Nếu người dùng hủy, danh sách thường rỗng. Giới hạn app vẫn cần enforce sau callback, đặc biệt khi fallback không hỗ trợ giới hạn chọn.

## 4. URI không phải file path

`content://provider/...` giống địa chỉ qua quầy tiếp nhận: muốn đọc cần hỏi ContentResolver. `File(uri.path)` không biến URI thành file thật.

Ví dụ copy media nhỏ sang vùng riêng để demo; chạy ngoài main thread. Hàm giới hạn dung lượng để tránh copy video quá lớn:

```kotlin
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

fun copyToCache(context: Context, uri: Uri): File {
    val destination = File(context.cacheDir, "picked-${UUID.randomUUID()}.bin")
    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Không mở được URI")
        input.use { source ->
            destination.outputStream().use { target ->
                val buffer = ByteArray(8 * 1024)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count == -1) break
                    total += count
                    if (total > 20L * 1024 * 1024) {
                        throw IOException("Demo giới hạn 20 MB")
                    }
                    target.write(buffer, 0, count)
                }
            }
        }
        return destination
    } catch (error: Exception) {
        destination.delete() // Dọn bản copy dở dang do hàm này tạo.
        throw error
    }
}
```

Đuôi `.bin` cố ý không đoán định dạng. Nếu cần `.jpg`, phải xác nhận/encode JPEG. Dùng `withContext(Dispatchers.IO)` để gọi hàm; bắt IOException/SecurityException ở UI và báo kết quả. Với upload, có thể stream thẳng nếu client hỗ trợ thay vì copy mọi file; đừng `readBytes()` video dài vào RAM.

## 5. Dùng URI sau khi app đóng

Quyền đọc mặc định có thể hết sau khi app dừng hoặc thiết bị khởi động lại. Nếu cần dùng lâu hơn, thử giữ grant bằng `takePersistableUriPermission` với cờ đọc và xử lý SecurityException. Lưu chuỗi URI chưa đủ; phải có quyền còn hiệu lực. Nội dung cũng có thể bị người dùng xóa. Xem [Persist media file access](https://developer.android.com/training/data-storage/shared/photo-picker#persist-media-file-access).

```kotlin
try {
    contentResolver.takePersistableUriPermission(
        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
    )
} catch (error: SecurityException) {
    // Provider/grant không cho giữ: copy khi còn quyền hoặc yêu cầu chọn lại.
}
```

Import `android.content.Intent`; đặt trong nhánh callback có URI. Chỉ giữ grant thực sự cần và giải phóng khi không dùng nữa.

## 6. Khi nào cần MediaStore/permission khác?

App gallery tự duyệt toàn bộ media có nhu cầu khác. Trên Android 13 có READ_MEDIA_IMAGES/VIDEO, Android 14 còn có cơ chế truy cập ảnh được chọn; hệ thống và target SDK ảnh hưởng hành vi. Không thêm READ_EXTERNAL_STORAGE rồi kỳ vọng hoạt động giống nhau trên mọi Android. Với nhu cầu chỉ chọn ảnh/video, ưu tiên picker; xem [Shared media storage](https://developer.android.com/training/data-storage/shared/media).

## Bài tập

Tạo ba nút chọn ảnh, video và nhiều media. Hủy mỗi picker để thử UI. Chọn ảnh cloud nếu có, hiển thị bằng Glide; thử mở lại URI sau khi khởi động app. Ghi trường hợp cần grant bền vững hoặc copy.

## Tự kiểm tra

1. Đổi avatar có cần đọc cả thư viện? — Không khi dùng picker.
2. Có nên dùng File(uri.path)? — Không với content URI.
3. Lưu URI String đã đủ đọc vĩnh viễn? — Chưa.

## Tiêu chí hoàn thành

- [ ] Chọn ảnh/video và xử lý hủy.
- [ ] Đọc qua resolver, không ép URI thành file path.
- [ ] Hiểu quyền tạm, persist grant và file có thể biến mất.

[Mục lục](README.md) · [Bài tiếp: CameraX](11-camerax-preview-chup-anh.md)

