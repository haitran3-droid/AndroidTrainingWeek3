# 05 — Hiểu và kiểm tra memory leak

## Mục tiêu

Hiểu vì sao màn hình đã đóng vẫn chiếm RAM; dùng heap dump để tìm đối tượng giữ màn hình; sửa theo lifecycle. Cần biết Activity/Fragment và callback ở bài 00.

## 1. RAM, reference và Garbage Collector

Khi tạo object, chương trình cần bộ nhớ. **Reference** là liên kết để truy cập object đó. **Garbage Collector (GC)** thu hồi object không còn được chương trình tiếp cận từ các gốc đang sống.

Memory leak xảy ra khi object không còn cần thiết nhưng vẫn có chuỗi reference giữ nó. Ví dụ một “sổ khách” toàn cục vẫn giữ Activity đã đóng. GC nhìn thấy reference nên chưa thể thu hồi.

```text
Singleton sống lâu → listener → Activity đã đóng → View và Bitmap
```

RAM tăng chưa đủ chứng minh leak. App có thể đang cache có giới hạn, tải ảnh lần đầu hoặc chưa đến lượt GC. Cần tái hiện và tìm **reference chain**. OOM là lỗi hết bộ nhớ; leak là một nguyên nhân, nhưng ảnh quá lớn hoặc nhiều allocation cũng có thể gây OOM.

## 2. Ví dụ lỗi dễ nhìn thấy

```kotlin
// Ví dụ SAI, chỉ để học nhận diện; không dùng trong app thật.
object ScreenStore {
    var lastActivity: Activity? = null
}

// Trong onCreate của Activity:
ScreenStore.lastActivity = this
```

Khi Activity bị hủy, `ScreenStore` vẫn giữ nó. Nếu cần lưu trạng thái, lưu dữ liệu như ID/chữ; không lưu cả màn hình. Nếu một thành phần sống theo toàn app thực sự cần Context để đọc tài nguyên, cân nhắc applicationContext; nó không thay thế Activity Context trong mọi thao tác UI.

## 3. Một leak thực tế: quên bỏ listener

Singleton tự đặt để minh họa, sử dụng trên main thread:

```kotlin
object DemoBus {
    private val listeners = mutableSetOf<() -> Unit>()
    fun add(listener: () -> Unit) { listeners += listener }
    fun remove(listener: () -> Unit) { listeners -= listener }
    fun fire() { listeners.toList().forEach { it() } }
}
```

Activity giữ listener cụ thể để đăng ký và gỡ **cùng một instance**:

```kotlin
private val listener: () -> Unit = {
    binding.result.text = "Nhận sự kiện"
}

override fun onStart() {
    super.onStart()
    DemoBus.add(listener)
}

override fun onStop() {
    DemoBus.remove(listener)
    super.onStop()
}
```

Lambda truy cập binding nên có thể giữ Activity/View. `remove { ... }` tạo lambda mới không phải listener đã thêm, nên gỡ sai. Quy tắc hữu ích: ai đăng ký thì phải biết khi nào và bằng cách nào hủy đăng ký.

## 4. Binding trong Fragment

```kotlin
private var _binding: FragmentDemoBinding? = null
private val binding get() = requireNotNull(_binding)

override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
): View {
    _binding = FragmentDemoBinding.inflate(inflater, container, false)
    return binding.root
}

override fun onDestroyView() {
    // Gỡ listener/callback gắn với View trước khi xóa binding.
    _binding = null
    super.onDestroyView()
}
```

Cần XML `fragment_demo.xml` và imports Android View/Bundle cùng generated binding. Nếu listener bên ngoài còn giữ View trực tiếp thì `_binding = null` chưa sửa toàn bộ leak. Với coroutine quan sát UI trong Fragment, sử dụng `viewLifecycleOwner.lifecycleScope` và `repeatOnLifecycle` khi phù hợp; xem [Lifecycle-aware coroutines](https://developer.android.com/topic/libraries/architecture/coroutines).

## 5. Dùng Profiler để kiểm chứng

1. Chọn app debug trong Android Studio Profiler và chức năng chụp **heap dump**; vị trí/names có thể khác theo phiên bản IDE.
2. Mở/đóng màn hình nghi ngờ khoảng 5–10 lần theo cùng kịch bản.
3. Chờ công việc nền kết thúc; có thể yêu cầu GC trong công cụ nếu có.
4. Chụp heap, tìm tên Activity/Fragment đã đóng.
5. Xem instance và references để biết đối tượng nào đang giữ chúng.
6. Sửa rồi lặp lại đúng kịch bản để so sánh.

**Shallow size** gần với bộ nhớ riêng của object; **retained size** cho biết bộ nhớ có thể được giải phóng khi loại object giữ đó theo cấu trúc heap. Một Activity nhỏ có thể giữ cả cây View/ảnh lớn. Xem [Capture a heap dump](https://developer.android.com/studio/profile/capture-heap-dump). Khả năng thu dữ liệu phụ thuộc build/device; profileable phù hợp một số phép đo nhưng không thay thế debug build cho mọi phân tích heap.

## 6. Những chỗ thường cần dọn

| Tài nguyên | Thao tác cần xem xét |
|---|---|
| Listener/event bus/socket | Gỡ đúng listener khi hết chủ sở hữu |
| Runnable/Handler | Bỏ callback không còn cần |
| Coroutine cập nhật View | Scope phù hợp lifecycle của View |
| MediaPlayer/AudioTrack/Recorder | Dừng và release theo chính sách |
| Executor | Ngừng nhận việc mới và shutdown khi hết chủ sở hữu |

`WeakReference` không phải thuốc sửa mọi leak. Nếu thiết kế ownership sai, weak reference có thể chỉ che lỗi và làm callback mất dữ liệu bất ngờ. Ưu tiên hiểu object sống bao lâu và ai quản lý nó.

## Bài tập

Trong app demo, cố tình giữ Activity bằng `ScreenStore`, mở/đóng ba lần và tìm trong heap. Sau đó bỏ reference, lặp lại kịch bản. Ghi một chuỗi “A giữ B giữ C” và giải thích tại sao sửa của bạn cắt được chuỗi đó.

## Tự kiểm tra

1. RAM cao luôn là leak? — Không; cần xem dữ liệu giữ và vòng đời.
2. GC có xóa object vẫn được singleton giữ không? — Không nếu vẫn reachable.
3. Null binding đã đủ nếu socket giữ View? — Chưa; phải gỡ callback/socket phù hợp.

## Tiêu chí hoàn thành

- [ ] Giải thích leak bằng reference chain cụ thể.
- [ ] Tìm được Activity đã đóng trong heap và người giữ nó.
- [ ] Sửa và kiểm chứng bằng cùng kịch bản tái hiện.

[Mục lục](README.md) · [Bài tiếp: Glide](06-glide-cache-size-transform.md)
