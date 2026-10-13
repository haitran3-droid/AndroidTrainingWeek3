# 04 — Kiểm tra app data: Preferences, files và cache

## Mục tiêu

Biết dữ liệu nằm ở đâu, đọc một giá trị SharedPreferences, kiểm tra file cache và tránh nhầm “clear cache” với “clear data”. Đọc bài 00 trước.

## 1. Chọn chỗ lưu theo loại dữ liệu

| Nhu cầu | Chỗ lưu thường dùng | Ví dụ |
|---|---|---|
| Cấu hình nhỏ, key/value | SharedPreferences hoặc DataStore | Bật dark mode |
| Danh sách cần query | Database | Ghi chú, tin nhắn |
| File riêng của app | `filesDir` | Bản nháp cần giữ |
| File có thể tái tạo | `cacheDir` | Thumbnail, response tạm |
| Media người dùng dùng ngoài app | MediaStore | Ảnh đã lưu vào thư viện |

Cache giống đồ đặt tạm trên bàn: có thể mất và cần tạo lại được. File cần giữ cho người dùng không nên chỉ sống ở cache. Hệ thống có thể thu hồi cache; app phải xử lý file không còn tồn tại. Xem [App-specific files](https://developer.android.com/training/data-storage/app-specific).

SharedPreferences là kiến thức cần biết để đọc app hiện có. Android khuyến nghị cân nhắc DataStore cho lưu cấu hình mới vì hỗ trợ mô hình bất đồng bộ tốt hơn; xem [SharedPreferences](https://developer.android.com/training/data-storage/shared-preferences). DataStore không phải file XML SharedPreferences và không nên tự sửa byte trong file protobuf.

## 2. Ghi và đọc Preferences

Trong Activity demo:

```kotlin
val prefs = getSharedPreferences("settings", MODE_PRIVATE)

// Ghi một lựa chọn của người dùng.
prefs.edit()
    .putBoolean("dark_mode", true)
    .putString("display_name", "Android Intern")
    .apply()

// Đọc; false là mặc định nếu chưa có key.
val darkMode = prefs.getBoolean("dark_mode", false)
val displayName = prefs.getString("display_name", "Khách")
binding.result.text = "$displayName, dark mode = $darkMode"
```

`settings` là tên file, `dark_mode` là key. Hai tên này khác nhau. `apply()` cập nhật giá trị trong bộ nhớ và ghi disk bất đồng bộ; `commit()` ghi đồng bộ và trả Boolean. Không gọi commit trên UI thread cho thao tác ghi nặng. Nếu phải chắc chắn dữ liệu đã bền vững, hiểu semantics của API đang dùng, không coi apply là callback xác nhận disk đã ghi xong.

Ghi đúng kiểu và đọc đúng kiểu. Nếu key từng là String rồi đổi sang Boolean, dữ liệu cũ có thể gây lỗi kiểu; cần migration hoặc xóa key thử nghiệm có chủ đích.

## 3. Tạo cache mẫu

```kotlin
// Bên trong Activity; imports File, lifecycleScope, Dispatchers,
// launch, withContext như bài 00.
lifecycleScope.launch {
    val cacheFile = withContext(Dispatchers.IO) {
        File(cacheDir, "lesson.txt").apply {
            writeText("Dữ liệu tạm, có thể tạo lại")
        }
    }
    binding.result.text = "Đã tạo: ${cacheFile.name}"
}
```

Chỉ tạo một file demo nhỏ. Khi đọc lại, kiểm tra `exists()` và tạo lại nếu thiếu. Khi xóa, ưu tiên file/cache thuộc tính năng mình quản lý; không xóa cả thư mục dữ liệu trong lúc thư viện/database đang sử dụng.

## 4. Quan sát bằng Device Explorer

Mở **View → Tool Windows → Device Explorer**, chọn thiết bị, tìm vùng private của **app debug**. Thường thấy `/data/data/<package>/` hoặc `/data/user/0/<package>/` tùy cách IDE hiển thị. Các thư mục hữu ích là `shared_prefs`, `files`, `cache`, `databases`. App không debuggable hoặc vùng thuộc app khác có thể không đọc được; xem [Device Explorer](https://developer.android.com/studio/debug/device-file-explorer).

Thử mở `shared_prefs/settings.xml`; bạn có thể thấy:

```xml
<map>
    <boolean name="dark_mode" value="true" />
    <string name="display_name">Android Intern</string>
</map>
```

Đây là nội dung quan sát được, không phải file cần đặt vào `res/xml`. Kiểm tra `cache/lesson.txt` để nối thao tác trong code với file thật trên thiết bị.

File mở qua Device Explorer có thể là bản copy trên máy tính; sửa nó chưa chắc sửa file trên device. Việc app giữ dữ liệu trong RAM cũng có thể khiến chỉnh file ngoài app không phản ánh ngay. Cách dễ học nhất là đổi bằng code app rồi kiểm tra file.

## 5. Clear cache và clear data

- **Clear cache**: loại dữ liệu tạm; app phải hoạt động được bằng cách tải/tạo lại.
- **Clear storage/data**: đưa dữ liệu app về trạng thái gần như mới cài; cấu hình, DB và trạng thái đăng nhập có thể mất.
- Gỡ app thường loại dữ liệu riêng; khi cài lại, backup/restore có thể làm một số dữ liệu quay lại. Không xem “gỡ rồi cài” là bằng chứng tuyệt đối cho lần chạy hoàn toàn sạch.

Trong bài thực hành hãy sử dụng dữ liệu giả. Nếu lỗi biến mất khi clear data, vẫn cần tìm nguyên nhân: schema cũ, key sai kiểu, cache sai version hoặc migration thiếu.

## Lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
|---|---|
| Đọc luôn giá trị mặc định | Tên file/key có trùng nơi ghi không |
| Đổi setting không thấy file đổi | Chờ ghi; refresh; đúng package/variant |
| Ảnh biến mất sau clear cache | App đang coi cache là nguồn dữ liệu duy nhất |
| Không truy cập được private directory | App build debug? Thiết bị/IDE hỗ trợ truy cập? |

## Bài tập

Lưu một tùy chọn dark mode và tạo một file cache. Đóng/mở app để kiểm tra tùy chọn còn. Clear cache rồi kiểm tra tùy chọn và file; cuối cùng clear data của app demo và ghi sự khác biệt.

## Tự kiểm tra

1. Có nên lưu bản ghi âm duy nhất vào cache? — Không nếu muốn giữ lâu dài.
2. MODE_PRIVATE nghĩa là mã hóa dữ liệu? — Không; nó nói về phạm vi truy cập file của app.
3. Clear data sửa hết lỗi thì đã hiểu lỗi chưa? — Chưa, cần tìm dữ liệu/trạng thái gây lỗi.

## Tiêu chí hoàn thành

- [ ] Chọn được storage phù hợp cho từng loại dữ liệu.
- [ ] Tìm được Preferences và file cache của app debug.
- [ ] Giải thích được apply/commit và clear cache/data.

[Mục lục](README.md) · [Bài tiếp: Memory leak](05-memory-leak.md)

