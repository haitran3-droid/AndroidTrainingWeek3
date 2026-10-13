# 02 — Debug database bằng Android Studio

## Mục tiêu

Tìm được dữ liệu app đang lưu, chạy SQL để kiểm chứng lỗi, phân biệt lỗi ghi dữ liệu với lỗi cập nhật giao diện. Cần biết chạy app debug và đọc Kotlin cơ bản.

## 1. Database là gì?

Database giống một cuốn sổ có cấu trúc. **Table** là một loại danh sách, **row** là một bản ghi, **column** là một thuộc tính. Ví dụ bảng `notes` chứa các ghi chú; mỗi ghi chú có `id`, `title`, `created_at`.

Android có SQLite. Room là thư viện giúp sử dụng SQLite qua các class/DAO; Room không phải server database. Database Inspector có thể làm việc với SQLite của hệ thống và thư viện xây trên đó như Room, trên thiết bị API 26 trở lên. Xem [Database Inspector](https://developer.android.com/studio/inspect/database).

## 2. Tạo database mẫu để có dữ liệu quan sát

Ví dụ này chỉ dùng SQLite có sẵn để không phải học Room trước. Tạo class trong project demo:

```kotlin
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DemoDb(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "training.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        // Demo version 1 chưa có migration.
        // Khi tăng version phải viết migration giữ dữ liệu.
    }
}
```

Trong Activity, tạo một helper và ghi dữ liệu ngoài main thread:

```kotlin
private val dbHelper by lazy { DemoDb(applicationContext) }

// Đặt trong callback bấm nút; imports ContentValues, lifecycleScope,
// Dispatchers, launch, withContext.
lifecycleScope.launch {
    val id = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("title", "Học Database Inspector")
            put("created_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.insertOrThrow("notes", null, values)
    }
    binding.result.text = "Đã tạo note $id"
}
```

`insertOrThrow` giúp nhìn thấy lỗi ràng buộc thay vì bỏ qua kết quả `-1`. Trong app thực tế, bắt lỗi để hiển thị trạng thái thất bại. Helper của demo được giữ suốt thời gian màn hình thực hành; khi dọn, hãy chờ công việc DB kết thúc rồi mới `close()`, tránh đóng database giữa lúc coroutine đang dùng.

## 3. Mở Inspector

1. Chạy **debug build** trên emulator/điện thoại API 26+.
2. Bấm nút thêm note để database được tạo/mở.
3. Mở **View → Tool Windows → App Inspection**, chọn process của app.
4. Chọn **Database Inspector**, mở `training.db` rồi bảng `notes`.
5. Bật Live updates hoặc refresh để quan sát sau mỗi thao tác.

Tên menu có thể đổi theo bản IDE; tìm tool window App Inspection. Nếu muốn sửa ô, tắt Live updates vì chế độ này hiển thị bảng chỉ đọc. Nếu process bị ngắt, dữ liệu offline là bản chụp phục vụ debug, không đại diện cho thay đổi đang chạy trên máy. Các chi tiết này được mô tả trong [hướng dẫn Inspector](https://developer.android.com/studio/inspect/database).

## 4. SQL tối thiểu cần biết

```sql
-- Xem các note mới nhất.
SELECT id, title, created_at FROM notes ORDER BY id DESC;

-- Đếm số bản ghi, kiểm tra bấm một lần có ghi hai lần không.
SELECT COUNT(*) AS total FROM notes;

-- Tìm theo điều kiện.
SELECT * FROM notes WHERE title LIKE '%Inspector%';

-- Chỉ thay đổi dữ liệu thử nghiệm có id cụ thể.
UPDATE notes SET title = 'Đã sửa bằng Inspector' WHERE id = 1;
```

`WHERE` giống điều kiện lọc. Bỏ `WHERE` trong UPDATE sẽ sửa mọi hàng. Dùng database demo để thử; không dùng dữ liệu quan trọng cho bài thực hành.

Trong code app, dùng placeholder/bind parameters khi giá trị đến từ người dùng, chẳng hạn `WHERE id = ?`. Ghép thẳng input vào chuỗi SQL gây lỗi dấu nháy và có nguy cơ SQL injection.

## 5. Tìm lỗi theo luồng dữ liệu

Giả sử bấm “Lưu” nhưng danh sách không thay đổi:

1. Kiểm tra callback bấm nút có chạy không.
2. Kiểm tra lệnh insert có trả ID thành công không.
3. Mở database đúng process, chạy SELECT.
4. Nếu bản ghi có trong DB: kiểm tra query/observer/adapter cập nhật UI.
5. Nếu không có: kiểm tra exception, transaction và database/file đang dùng.

Một giao diện Room quan sát bằng Flow/LiveData có thể tự cập nhật khi dữ liệu đổi. Một TextView chỉ được gán một lần sẽ không tự đọc lại SQLite. Hãy phân biệt “dữ liệu đã thay đổi” với “UI biết dữ liệu đã thay đổi”.

## Lỗi thường gặp

| Hiện tượng | Cần kiểm tra |
|---|---|
| Không thấy database | App đã thực sự mở DB chưa; đúng process chưa |
| Bảng rỗng | Query có filter; đúng user; đúng debug variant |
| Dữ liệu không refresh | Refresh/Live updates; app đã ghi và commit chưa |
| DB đóng | Vòng đời kết nối; tùy chọn giữ connection mở chỉ để debug |
| File DB copy không đủ dữ liệu | SQLite có thể có WAL; ưu tiên export bằng Inspector |

## Bài tập

Thêm ba note. Dùng SQL đếm chính xác ba hàng; sửa một title trong Inspector. Đọc lại danh sách từ app. Giải thích vì sao UI của ví dụ cần đọc lại, rồi tìm cách thêm một nút Refresh.

## Tự kiểm tra

1. UI không đổi có chứng minh insert thất bại không? — Không, phải kiểm tra DB.
2. Room và SQLite khác nhau thế nào? — Room là lớp hỗ trợ truy cập SQLite.
3. UPDATE thiếu WHERE ảnh hưởng gì? — Mọi hàng thỏa câu lệnh, thường là toàn bảng.

## Tiêu chí hoàn thành

- [ ] Mở đúng process/database và đọc được bảng.
- [ ] Chạy được SELECT, COUNT và UPDATE trên dữ liệu thử.
- [ ] Chỉ ra được lỗi thuộc ghi dữ liệu, đọc dữ liệu hay cập nhật UI.

[Mục lục](README.md) · [Bài tiếp: Debug network](03-debug-network.md)

