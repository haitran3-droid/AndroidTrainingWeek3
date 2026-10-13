# 06 — Glide: tải ảnh, cache, kích thước và transform

## Mục tiêu

Hiển thị ảnh trong ImageView, giải thích các tầng cache, đổi kích thước tải và biến đổi ảnh. Cần một layout có `ImageView` ID `avatar`.

## 1. Vì sao dùng Glide?

Tải ảnh không chỉ là lấy byte từ mạng. App còn phải giải mã, chọn kích thước, tránh chặn UI, xử lý lỗi và tránh gán ảnh cũ vào View đã tái sử dụng. Glide giúp quản lý chuỗi công việc đó.

Thêm vào module app, rồi Sync:

```kotlin
implementation("com.github.bumptech.glide:glide:4.16.0")
```

Đây là mốc v4 cụ thể để phù hợp API trong bài, không phải tuyên bố bản mới nhất; xem [release 4.16.0](https://github.com/bumptech/glide/releases/tag/v4.16.0). Ví dụ dùng `Glide` thông thường nên chưa cần generated API/annotation processor. Ảnh mạng cần quyền `INTERNET`.

## 2. Request đầu tiên

Trong Activity; tạo `img_placeholder`, `img_error` trong drawable và thay URL bằng ảnh thử nghiệm hoạt động:

```kotlin
import com.bumptech.glide.Glide

Glide.with(this)
    .load("https://example.com/avatar.jpg")
    .placeholder(R.drawable.img_placeholder)
    .error(R.drawable.img_error)
    .centerCrop()
    .into(binding.avatar)
```

Đọc từ trên xuống: gắn request với Activity → nguồn ảnh → ảnh khi đang chờ → ảnh khi lỗi → kiểu crop → nơi hiển thị. `.load()` nhận được cả URL, Uri, File hoặc resource ID. Khi chạy trong Fragment, chọn scope thích hợp với màn hình/View, tránh dùng application context theo thói quen cho mọi request UI.

Placeholder không chứng minh mạng đang chạy; nó chỉ là trạng thái hiển thị. Nếu ảnh đã có trong cache, có thể bạn không kịp thấy placeholder.

## 3. Cache hoạt động thế nào?

Glide kiểm tra các tầng như tài nguyên đang dùng, memory cache và disk cache trước khi cần lấy từ nguồn. Memory chứa tài nguyên đã sẵn cho hiển thị; disk có thể lưu dữ liệu nguồn hoặc ảnh đã biến đổi tùy chiến lược. Cache key không chỉ là URL: kích thước, transform, signature và options cũng ảnh hưởng. Xem [Glide caching](https://bumptech.github.io/glide/doc/caching.html).

Ví dụ dễ nhớ: ảnh gốc là nguyên liệu; ảnh crop 100 × 100 là món đã chế biến. Cache nguyên liệu và cache món là hai việc khác nhau.

| Cấu hình | Ý nghĩa khi dùng disk cache |
|---|---|
| `AUTOMATIC` | Glide chọn chiến lược theo nguồn; mặc định |
| `DATA` | Lưu dữ liệu nguồn |
| `RESOURCE` | Lưu tài nguyên đã decode/transform |
| `ALL` | Lưu các dạng được chiến lược này hỗ trợ |
| `NONE` | Không lưu disk; không đồng nghĩa tắt memory |

```kotlin
import com.bumptech.glide.load.engine.DiskCacheStrategy

Glide.with(this).load(imageUrl)
    .diskCacheStrategy(DiskCacheStrategy.DATA)
    .into(binding.avatar)
```

`skipMemoryCache(true)` và `diskCacheStrategy(NONE)` điều khiển hai nơi khác nhau. Không tắt cache trong mọi request chỉ để “chắc chắn có ảnh mới”; nó làm tăng mạng, CPU và độ trễ.

## 4. URL giữ nguyên nhưng ảnh đã đổi

Tốt nhất server cung cấp URL/version mới. Nếu biết version ảnh, dùng signature:

```kotlin
import com.bumptech.glide.signature.ObjectKey

Glide.with(this)
    .load(imageUrl)
    .signature(ObjectKey(avatarVersion)) // Ví dụ "v2" do server cung cấp.
    .into(binding.avatar)
```

Đừng dùng thời gian hiện tại cho mọi lần load: mỗi lần tạo cache key mới gần như vô hiệu hóa việc tái sử dụng. `onlyRetrieveFromCache(true)` hữu ích cho bài kiểm tra offline: request thất bại nếu không tìm thấy cache phù hợp.

## 5. Custom size: px của ảnh tải khác dp của layout

ImageView rộng 80 dp không có nghĩa nên decode 80 px trên mọi thiết bị. `override(width, height)` nhận pixel. Chuyển từ dp khi bạn chủ động chọn size; nhiều trường hợp Glide có thể đo View giúp bạn. Xem [Targets và kích thước](https://bumptech.github.io/glide/doc/targets.html).

```kotlin
val sizePx = (80 * resources.displayMetrics.density).roundToInt()
Glide.with(this).load(imageUrl)
    .override(sizePx, sizePx)
    .centerCrop()
    .into(binding.avatar)
```

Import `kotlin.math.roundToInt`. Trên density = 3, cần khoảng 240 px. `override()` chọn mục tiêu decode/request; không thay đổi `layout_width` của ImageView. Tải ảnh gốc 4000 × 3000 chỉ để hiển thị avatar nhỏ có thể lãng phí nhiều RAM.

## 6. Transform và custom transform

`centerCrop` lấp kín vùng đích và cắt phần thừa; `fitCenter` giữ toàn bộ ảnh nhưng có thể để vùng trống. `circleCrop` tạo ảnh tròn; `RoundedCorners` bo góc. Thứ tự transform có ý nghĩa; xem [Transformations](https://bumptech.github.io/glide/doc/transformations.html).

```kotlin
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

val radiusPx = (12 * resources.displayMetrics.density).roundToInt()
Glide.with(this).load(imageUrl)
    .transform(CenterCrop(), RoundedCorners(radiusPx))
    .into(binding.avatar)
```

Nếu cần hiệu ứng riêng, có thể viết `BitmapTransformation`. Khi đó phải xác định đúng `equals`, `hashCode`, `updateDiskCacheKey`, quản lý bitmap pool và thread; đây là phần nâng cao. Với intern, nắm built-in transform và cache key trước. Transform của Glide thay ảnh được hiển thị, **không tự ghi một file đã crop để upload**; bài 12 dạy crop tạo file đầu ra.

## Lỗi thường gặp

- Ảnh cũ: URL/signature giữ nguyên hoặc server/CDN trả cache cũ.
- Ảnh mờ: source nhỏ hoặc override quá nhỏ so với vùng hiển thị.
- OOM: decode ảnh lớn quá nhu cầu hoặc giữ nhiều bitmap ngoài Glide.
- RecyclerView hiện ảnh sai: mỗi lần bind phải đặt request mới/placeholder cho trạng thái tương ứng; đừng bỏ qua nhánh item không có ảnh.

## Bài tập

Tải cùng ảnh hai lần và quan sát network. Thử `circleCrop`, `fitCenter` và bo góc. Tắt mạng rồi dùng `onlyRetrieveFromCache(true)`; ghi lại kết quả với signature cũ và signature mới.

## Tự kiểm tra

1. Tắt disk có tắt memory không? — Không.
2. override nhận dp hay px? — px.
3. centerCrop có sửa ảnh gốc trên server? — Không.

## Tiêu chí hoàn thành

- [ ] Tải được URL/Uri, có placeholder/error.
- [ ] Giải thích được memory/disk và việc đổi cache key.
- [ ] Chọn size phù hợp và phân biệt các transform.

[Mục lục](README.md) · [Bài tiếp: Gson](07-gson-json-object.md)

