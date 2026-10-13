# 00 — Chuẩn bị và kiến thức nền

## Mục tiêu

Hiểu những từ xuất hiện xuyên suốt bài giảng: Activity, lifecycle, main thread, callback, URI, permission và dependency. Chuẩn bị một app nhỏ để thực hành.

## 1. Những thành phần bạn sẽ gặp

| Thành phần | Cách hiểu dễ nhớ | Ví dụ |
|---|---|---|
| Activity | Nơi Android quản lý một giao diện và vòng đời của nó | Màn hình demo Camera |
| Fragment | Một phần giao diện có vòng đời riêng, nằm trong Activity | Tab Hồ sơ |
| View | Một thành phần hiển thị hoặc nhận thao tác | TextView, ImageView, Button |
| XML layout | Bản mô tả cách sắp xếp các View | `res/layout/activity_main.xml` |
| Context | Cách tiếp cận tài nguyên/dịch vụ của Android | Đọc string, mở file, lấy AudioManager |
| Dependency | Thư viện được thêm vào dự án qua Gradle | Glide, Retrofit |
| Callback | Hàm được gọi khi một công việc có kết quả | Người dùng chọn xong ảnh |
| Coroutine | Cách Kotlin viết công việc bất đồng bộ | Đọc file ở `Dispatchers.IO` |

Ví dụ callback giống việc bạn đặt đồ ăn: đặt xong vẫn làm việc khác, khi đồ ăn đến thì chuông báo chạy. Đừng giả định dòng sau lệnh “bắt đầu” đã có kết quả.

## 2. Tạo nơi thực hành

1. Trong Android Studio tạo project bằng template **Empty Views Activity**, ngôn ngữ Kotlin. Nếu tên template khác theo phiên bản IDE, chọn template dùng XML/Views.
2. Với bộ demo này có thể chọn `minSdk = 26`; chọn compile SDK đủ cho dependency, chẳng hạn 36 nếu SDK và Gradle hiện tại hỗ trợ. Đây là lựa chọn cho app học, không phải yêu cầu mọi app phải bỏ Android cũ.
3. Chạy màn hình mặc định trên emulator. Bài Camera/Recorder nên thử thêm trên điện thoại.
4. Thêm dependency vào **module app**, rồi Sync Gradle. Không thêm thư viện vào file Gradle của root project nhầm vị trí.
5. Dùng một Activity riêng cho mỗi demo khi mới học để dễ xác định tài nguyên thuộc màn hình nào.

Ví dụ cấu hình cần có trong `app/build.gradle.kts` của project thực hành:

```kotlin
android {
    buildFeatures {
        viewBinding = true
    }
}
```

Template thường đã có AppCompat, Core, Activity và ConstraintLayout. Bài dùng `lifecycleScope` cần dependency `androidx.lifecycle:lifecycle-runtime-ktx` tương thích với dự án. Chỉ thêm phần thiếu, tránh khai báo cùng thư viện bằng hai cách. Repository cho AndroidX là `google()`, cho đa số thư viện còn lại là `mavenCentral()`; uCrop dùng thêm JitPack ở bài 12.

**ViewBinding** sinh một class từ XML. Ví dụ `activity_main.xml` sinh `ActivityMainBinding`; View có ID `avatar` được truy cập bằng `binding.avatar`. Nó giúp tránh ép kiểu khi dùng `findViewById`.

### Màn hình đầu tiên để chạy thử

Thay nội dung `res/layout/activity_main.xml` bằng layout demo sau:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp">
    <Button
        android:id="@+id/demoButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Bấm để thử" />
    <TextView
        android:id="@+id/result"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Chưa bấm nút" />
</LinearLayout>
```

Trong `MainActivity.kt`, giữ dòng package của project. Nếu namespace không phải `com.example.medialab`, sửa import binding bên dưới theo namespace thật; Android Studio có thể gợi ý import sau khi Sync/build:

```kotlin
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.medialab.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("LifecycleDemo", "onCreate")
        binding.demoButton.setOnClickListener {
            binding.result.text = "Bạn đã bấm nút"
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("LifecycleDemo", "onStart")
    }

    override fun onStop() {
        Log.d("LifecycleDemo", "onStop")
        super.onStop()
    }
}
```

`lateinit` cho biết binding sẽ được gán trước khi dùng; đừng truy cập nó trước inflate. `setContentView` đưa cây View lên màn hình. `setOnClickListener` đăng ký callback bấm nút. Khi thêm ví dụ các bài sau, thay callback hoặc thêm View có ID tương ứng, không dán nhiều class MainActivity trùng nhau. Chữ hardcoded ở demo nên chuyển sang string resources khi hoàn thiện; xử lý insets ở bài 01.

Với các bài dùng coroutine, nếu project chưa có dependency thì thêm vào khối `dependencies` của module app:

```kotlin
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
```

Đây là mốc ví dụ đã phát hành; dự án có Lifecycle mới hơn nên giữ version tương thích đang dùng. Tham khảo [Lifecycle release notes](https://developer.android.com/jetpack/androidx/releases/lifecycle).

## 3. Hiểu lifecycle trước khi dùng camera/audio

Luồng phổ biến:

```text
onCreate → onStart → onResume
                         ↓
onDestroy ← onStop ← onPause
```

Đây là luồng minh họa; một màn hình có thể đi qua Start/Stop nhiều lần. Khi process bị hệ thống kết thúc, `onDestroy()` không được bảo đảm gọi. Vì vậy hãy lưu dữ liệu cần thiết sớm và dọn camera/audio theo vòng đời màn hình.

- `onCreate`: tạo giao diện, đăng ký callback.
- `onStart`: màn hình bắt đầu hiển thị; phù hợp khởi tạo tài nguyên demo cần trong thời gian nhìn thấy.
- `onStop`: màn hình không còn hiển thị; dừng tài nguyên demo theo chính sách đã chọn.
- `onDestroy`: dọn phần còn lại.
- Với Fragment, View có thể bị hủy trước Fragment. Dọn binding và listener gắn với View trong `onDestroyView()`.

## 4. Main thread và background thread

Main thread cập nhật giao diện. Nếu bạn đọc file lớn, chạy vòng lặp xử lý ảnh hoặc chờ mạng ở đây, thao tác bấm/scroll sẽ bị đứng. Công việc nặng cần chạy ngoài main thread; kết quả mới quay về cập nhật View.

```kotlin
// Bên trong Activity; cần lifecycle-runtime-ktx.
lifecycleScope.launch {
    val text = withContext(Dispatchers.IO) {
        File(filesDir, "demo.txt").readText()
    }
    binding.result.text = text // Trở lại main thread.
}
```

Imports cho ví dụ: `java.io.File`, `androidx.lifecycle.lifecycleScope`, `kotlinx.coroutines.Dispatchers`, `launch`, `withContext`. File cần được tạo trước; khi làm bài thực tế phải xử lý trường hợp file không tồn tại. `lifecycleScope` hủy coroutine khi lifecycle bị hủy, nhưng một lời gọi blocking tùy ý không tự trở thành tác vụ có thể ngắt tức thì.

## 5. Permission và URI

Manifest là nơi khai báo khả năng app cần. Permission nguy hiểm như `CAMERA` và `RECORD_AUDIO` còn phải xin lúc chạy, và người dùng có thể từ chối. Chỉ bắt đầu công việc sau callback cấp quyền; xem [Request runtime permissions](https://developer.android.com/training/permissions/requesting).

```xml
<!-- Trong <manifest>, bên ngoài <application>. Chỉ thêm quyền bài cần. -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

`INTERNET` không cần popup xin quyền runtime. **URI** là địa chỉ tài nguyên; `content://...` phải đọc qua `ContentResolver`, không ép thành đường dẫn file. Một ảnh do Photo Picker trả về có thể đến từ dịch vụ cloud, không có file path mà app đọc trực tiếp được.

## 6. Đọc code trong bộ bài giảng

- `this` trong đoạn Activity là Activity hiện tại.
- `binding` giả định bạn đã bật ViewBinding và gắn layout bằng `setContentView(binding.root)`.
- Tên `binding.result`, `binding.avatar` là ID minh họa: bạn cần tạo View tương ứng trong XML hoặc đổi sang ID của mình.
- Snippet đặt ở cấp class không được dán vào bên trong một hàm; snippet có `override` phải thuộc class tương ứng.
- Mỗi bài có imports chính. Android Studio có thể hỗ trợ import, nhưng phải chọn đúng package, đặc biệt `android.media.AudioAttributes` và `androidx.media3.common.AudioAttributes`.

## Bài tập

Tạo màn hình có Button và TextView, bấm nút đổi chữ; ghi log trong `onCreate`, `onStart`, `onStop`. Bấm Home rồi quay lại, quan sát callback. Xoay màn hình và giải thích vì sao Activity có thể được tạo lại.

## Tự kiểm tra

1. Callback khác gì giá trị trả về ngay lập tức? — Callback chạy khi công việc hoàn tất; thứ tự phụ thuộc sự kiện.
2. Có nên xử lý hàng nghìn pixel ở main thread? — Không; nó cạnh tranh với công việc vẽ UI.
3. `content://` có phải file path không? — Không; dùng resolver.
4. Khai báo CAMERA đã đủ mở camera chưa? — Chưa, cần kiểm tra/xin quyền runtime.

## Tiêu chí hoàn thành

- [ ] Chạy được project thực hành và hiểu ViewBinding.
- [ ] Giải thích được lifecycle bằng thao tác Home/quay lại.
- [ ] Biết đặt dependency ở module app và biết Sync Gradle.
- [ ] Phân biệt việc nền, UI, callback, permission và URI.

Đọc tiếp [01 — Layout và icon từ design](01-layout-va-icon-tu-design.md).

