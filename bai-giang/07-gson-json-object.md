# 07 — Gson: JSON thành object và object thành JSON

## Mục tiêu

Cài Gson, đọc JSON thành model Kotlin, chuyển model thành JSON và nhận biết dữ liệu không hợp lệ. Bài này có thể thử phần thuần Kotlin mà chưa cần mạng.

## 1. JSON là gì?

JSON là định dạng trao đổi dữ liệu bằng văn bản. Nó có object `{}`, array `[]`, string, number, Boolean và null. JSON chuẩn cần dấu nháy kép cho tên trường và string.

```json
{"id": 7, "full_name": "Lan", "active": true}
```

Trong Kotlin, object có kiểu dữ liệu và thuộc tính; JSON là chuỗi ký tự. Gson làm cầu nối:

```text
JSON String --fromJson--> Object
Object      --toJson----> JSON String
```

Trong ảnh kế hoạch, “parse object từ JSON String” được hiểu là **JSON → object**; “convert object to JSON String” là chiều ngược lại.

## 2. Cài đặt và tạo model

```kotlin
implementation("com.google.code.gson:gson:2.14.0")
```

Version trên là một mốc cụ thể từ [repository Gson](https://github.com/google/gson). Nếu dự án đã có Gson từ Retrofit converter, xem dependency resolution để tránh tưởng rằng hai version chạy song song.

```kotlin
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class UserDto(
    val id: Long? = null,
    @SerializedName("full_name") val fullName: String? = null,
    val active: Boolean? = null
)
```

**DTO** là model dùng trao đổi dữ liệu. `@SerializedName` nối key `full_name` với thuộc tính `fullName`. Các trường nullable thể hiện rằng dữ liệu mạng có thể thiếu/null; app vẫn phải kiểm tra trước khi dùng.

## 3. JSON → object

```kotlin
val gson = Gson()
val json = """{"id":7,"full_name":"Lan","active":true}"""
val user: UserDto? = gson.fromJson(json, UserDto::class.java)

println(user?.fullName) // Lan
```

Đối số đầu là dữ liệu; đối số sau mô tả kiểu cần tạo. Chuỗi JSON `"null"` có thể cho kết quả null, vì vậy đừng giả định luôn nhận được object.

Gson là thư viện Java dùng reflection; nó không hiểu đầy đủ các bảo đảm của Kotlin như non-null và constructor/default parameters trong mọi model. Không dùng `val name: String` rồi kết luận dữ liệu mạng sẽ không bao giờ null. Đọc [Gson Troubleshooting](https://github.com/google/gson/blob/main/Troubleshooting.md).

## 4. Object → JSON

```kotlin
val original = UserDto(id = 9, fullName = "Minh", active = false)
val output = gson.toJson(original)
println(output)
// Ví dụ: {"id":9,"full_name":"Minh","active":false}
```

Không dựa vào thứ tự key để so sánh JSON. Mặc định Gson thường bỏ field null khi serialize; muốn đưa null vào output:

```kotlin
import com.google.gson.GsonBuilder

val gsonWithNulls = GsonBuilder().serializeNulls().create()
```

`toJson` không gửi request. Nó chỉ tạo chuỗi. Retrofit converter mới nối chuỗi/stream JSON với HTTP ở bài 08. Xem [Gson User Guide](https://github.com/google/gson/blob/main/UserGuide.md).

## 5. Parse danh sách và generic

Nếu JSON bắt đầu bằng `[` thì thường là array. `List::class.java` không chứa đủ thông tin kiểu phần tử, nên dùng TypeToken:

```kotlin
import com.google.gson.reflect.TypeToken

val listJson = """[
  {"id":1,"full_name":"Lan","active":true},
  {"id":2,"full_name":"Minh","active":false}
]"""
val listType = object : TypeToken<List<UserDto>>() {}.type
val users: List<UserDto>? = gson.fromJson(listJson, listType)
println(users?.size) // 2
```

Hãy nhìn shape JSON trước. `{ "data": [...] }` là object chứa array, không phải array ở root; cần model wrapper tương ứng.

## 6. Parse thành công chưa có nghĩa dữ liệu đúng

Ví dụ server trả `{"id":7}`: parser có thể tạo DTO nhưng tên không có. Ta chuyển DTO thành model dùng trong app sau khi validate:

```kotlin
data class User(val id: Long, val name: String)

fun UserDto.toUser(): User {
    val validId = requireNotNull(id) { "Thiếu id" }
    require(validId > 0) { "id phải dương" }
    val validName = fullName?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: error("Thiếu full_name")
    return User(validId, validName)
}
```

Đây là ví dụ validation; khi dùng trên UI, bắt lỗi và hiển thị thông báo thay vì để app crash. Cần phân biệt lỗi cú pháp JSON, lỗi kiểu/shape và lỗi nghiệp vụ như ID âm.

## 7. Xử lý lỗi và release build

```kotlin
import com.google.gson.JsonParseException

val dto = try {
    gson.fromJson("{sai-json", UserDto::class.java)
} catch (error: JsonParseException) {
    null // Demo; thực tế lưu nguyên nhân và đưa UI sang trạng thái lỗi.
}
```

Đừng biến mọi lỗi thành object mặc định: UI có thể báo thành công với dữ liệu sai. Release build có R8/obfuscation cần được thử riêng vì Gson dùng reflection; annotations, constructor và generic signatures đều có thể liên quan. Áp dụng hướng dẫn R8 của version đang dùng, không thêm keep rule rộng cho toàn app theo thói quen.

## Bài tập

Tạo `ProductDto` có id, tên và giá nullable. Parse một sản phẩm và danh sách ba sản phẩm; serialize lại. Thử JSON thiếu tên, giá sai kiểu và object wrapper. Ghi lỗi thuộc parse hay validation.

## Tự kiểm tra

1. fromJson và toJson đi theo chiều nào? — JSON → object và object → JSON.
2. Vì sao cần TypeToken? — Giữ thông tin generic như kiểu phần tử List.
3. Field non-null Kotlin có thay validation không? — Không.

## Tiêu chí hoàn thành

- [ ] Parse/serialize được object và danh sách.
- [ ] Đặt SerializedName đúng key của API.
- [ ] Phân biệt shape, kiểu dữ liệu và điều kiện nghiệp vụ.

[Mục lục](README.md) · [Bài tiếp: Retrofit và Interceptor](08-retrofit-okhttp-interceptor.md)

