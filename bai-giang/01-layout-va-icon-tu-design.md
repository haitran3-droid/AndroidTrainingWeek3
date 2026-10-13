# 01 — Tạo layout và export icon từ design

## Mục tiêu

Chuyển một thiết kế thành giao diện Android: xuất đúng icon, chọn đúng đơn vị kích thước, căn View và tự so sánh kết quả. Đọc bài 00 trước; ví dụ dùng XML + ConstraintLayout.

## 1. Design là bản mô tả, không chỉ là ảnh

Hãy tưởng tượng design là bản vẽ của một căn phòng. Chỉ nhìn ảnh phòng không cho biết khoảng cách giữa bàn và tường; bạn cần số đo. Với giao diện, cần lấy chiều rộng, khoảng cách, font, màu và trạng thái của từng thành phần.

Trước khi code, ghi một bảng thông số. Ví dụ **tự đặt để thực hành**, không phải số đo từ ảnh chương trình học:

| Thành phần | Thông số giả định |
|---|---|
| Lề hai bên | 16 dp |
| Avatar | 72 × 72 dp |
| Tên | 20 sp, đậm |
| Khoảng cách avatar → tên | 16 dp |
| Nút | Tối thiểu 48 dp cao |

Ghi thêm màu bằng mã hex, font family/weight và các trạng thái loading/error/disabled. Nếu design không có trạng thái, ghi giả định của bạn để mentor review.

## 2. px, dp, sp khác nhau thế nào?

`px` là pixel vật lý. `dp` là đơn vị giúp kích thước giao diện nhất quán giữa các mật độ màn hình; `sp` dành cho chữ và có tính đến cài đặt cỡ chữ. Công thức gần đúng cho kích thước layout là `px = dp × density`; Android có cách xử lý riêng cho font scaling, không nên tự đổi sp bằng công thức tuyến tính cố định trên Android mới. Xem [Support different pixel densities](https://developer.android.com/training/multiscreen/screendensities).

Ví dụ density = 3: icon 24 dp chiếm khoảng 72 px. Đừng lấy ảnh chụp có icon rộng 72 px rồi viết `72dp`.

Với Figma/Sketch, cần xác nhận frame đại diện cho kích thước logic nào và export scale bao nhiêu. Không mặc định mọi số “px” trong design đều cần chia cho density của điện thoại đang dùng.

## 3. Export icon

Quy trình nếu file design nằm trong Figma; công cụ khác dùng bước tương đương:

1. Chọn đúng layer/group của icon, kiểm tra khung và padding trong layer.
2. Với hình đơn giản, xuất SVG. Trong Android Studio chọn `res` → **New → Vector Asset** → import local SVG.
3. Android tạo VectorDrawable XML trong `res/drawable`. Kiểm tra preview, nét, màu và kích thước; SVG có tính năng không được hỗ trợ thì có thể cần đơn giản hóa hoặc dùng raster.
4. Với ảnh nhiều chi tiết, xuất PNG/WebP. Nếu asset phải hiển thị theo mật độ, chuẩn bị các kích thước phù hợp cho thư mục `drawable-mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`.
5. Đặt tên dạng `ic_camera.xml`, `ic_back.xml`, `img_placeholder.webp`.

Vector phù hợp với nhiều icon nhưng không phải mọi SVG đều import nguyên vẹn. `drawable-nodpi` dùng khi bạn chủ động không muốn Android scale theo mật độ, không phải thư mục mặc định cho mọi ảnh. Tham khảo [Vector Asset Studio](https://developer.android.com/studio/write/vector-asset-studio).

Nếu icon đã xuất có khoảng trắng bên trong, đổi kích thước ImageView chưa chắc làm hình bên trong đúng số đo. Phải so sánh cả bounds của asset.

## 4. Tạo layout có ràng buộc rõ ràng

Trong `res/layout/activity_profile.xml`; cần dependency ConstraintLayout và icon `ic_profile` do bạn import:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <ImageView
        android:id="@+id/avatar"
        android:layout_width="72dp"
        android:layout_height="72dp"
        android:contentDescription="@string/avatar_description"
        android:src="@drawable/ic_profile"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <TextView
        android:id="@+id/name"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:text="@string/profile_name"
        android:textSize="20sp"
        android:textStyle="bold"
        app:layout_constraintStart_toEndOf="@id/avatar"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="@id/avatar" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

Trong `res/values/strings.xml` thêm:

```xml
<resources>
    <string name="avatar_description">Ảnh đại diện</string>
    <string name="profile_name">Nguyễn Minh — Android Intern</string>
</resources>
```

Giải thích từng quyết định:

- Avatar có kích thước cụ thể vì design yêu cầu vùng ảnh cố định.
- Tên rộng `0dp` trong ConstraintLayout nghĩa là **match constraints**, lấy phần còn lại giữa hai ràng buộc; không phải luôn rộng bằng 0.
- `wrap_content` giúp chiều cao chữ thích nghi khi tên xuống dòng.
- `start/end` hỗ trợ hướng đọc; tránh dùng left/right tùy tiện.
- Mỗi View cần ràng buộc theo cả chiều ngang và dọc. Vị trí kéo thả trong editor chưa bảo đảm vị trí runtime nếu thiếu constraints.

Xem [ConstraintLayout](https://developer.android.com/develop/ui/views/layout/constraint-layout) để mở rộng sang chain, guideline và ratio.

## 5. Đừng quên vùng thanh hệ thống

Khi giao diện vẽ edge-to-edge, nội dung có thể nằm dưới status bar/navigation bar. Xử lý **window insets** để nội dung đọc và bấm được. Đừng cộng một số cố định như “status bar luôn 24dp”. Các dự án target Android mới cần chú ý quy tắc edge-to-edge; tham khảo [Edge-to-edge trong Views](https://developer.android.com/develop/ui/views/layout/edge-to-edge).

Trong Activity, sau khi inflate binding; giữ padding design rồi cộng insets, không cộng dồn mỗi callback:

```kotlin
val root = binding.root
val left = root.paddingLeft
val top = root.paddingTop
val right = root.paddingRight
val bottom = root.paddingBottom

ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.setPadding(left + bars.left, top + bars.top,
        right + bars.right, bottom + bars.bottom)
    insets
}
```

Imports: `androidx.core.view.ViewCompat`, `androidx.core.view.WindowInsetsCompat`. Nếu template đã có listener insets cho root thì sửa listener đó; tránh xử lý trùng hai lần.

## 6. Quy trình tự kiểm tra

So sánh trên cùng kích thước logic với frame design. Đối chiếu lề, kích thước icon, vị trí baseline chữ, font và màu. Sau đó chạy ở màn hình hẹp/rộng và tăng cỡ chữ: “giống design” vẫn phải dùng được khi tên dài.

| Lỗi | Nguyên nhân thường gặp | Cách kiểm tra |
|---|---|---|
| Icon mờ | Raster nhỏ bị phóng lớn | Mở file gốc và xem kích thước |
| Vị trí runtime khác preview | Thiếu constraints | Kiểm tra ngang/dọc của từng View |
| Khoảng cách chữ có vẻ lệch | Font metrics/padding hoặc font sai | So baseline và đúng font weight |
| Nội dung bị thanh hệ thống che | Chưa xử lý insets | Thử gesture navigation và xoay màn hình |

## Bài tập

Tự chọn một màn hình hồ sơ có avatar, tên và hai nút. Xuất hai icon; lập bảng số đo trước khi code. Tạo XML và chụp màn hình ở hai kích thước thiết bị. Đánh dấu ba khác biệt với design và sửa chúng.

## Tự kiểm tra

1. Vì sao dùng sp cho chữ? — Để tôn trọng cỡ chữ người dùng.
2. `0dp` ở ConstraintLayout có luôn vô hình? — Không, nó phụ thuộc constraints.
3. SVG import xong có cần kiểm tra? — Có, vector Android chỉ hỗ trợ một tập tính năng.

## Tiêu chí hoàn thành

- [ ] Xuất icon đúng layer, đúng định dạng và tên resource.
- [ ] Giải thích được dp/sp và không sao chép px máy móc.
- [ ] Layout có đủ constraints, đúng số đo đã thống nhất.
- [ ] Giao diện vẫn dùng được với tên dài, chữ lớn và insets.

[Mục lục](README.md) · [Bài tiếp: Debug database](02-debug-database.md)
