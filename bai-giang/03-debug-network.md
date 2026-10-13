# 03 — Debug network và đọc request/response

## Mục tiêu

Quan sát một lần gọi API, đọc method/URL/header/body/status và xác định hướng sửa lỗi. Bài này dùng request Retrofit ở bài 08 hoặc request sẵn có của app.

## 1. Một cuộc gọi API gồm những gì?

Tưởng tượng gọi API là gửi đơn hàng đến cửa hàng:

- **URL**: địa chỉ cửa hàng và quầy cần đến.
- **Method**: muốn xem, tạo, sửa hay xóa dữ liệu (`GET`, `POST`, `PUT`, `DELETE`).
- **Header**: thông tin bổ sung như định dạng, token.
- **Request body**: dữ liệu gửi đi, thường là JSON.
- **Response**: status, header và dữ liệu server trả về.

```http
GET /users/42 HTTP/1.1
Host: api.example.com
Accept: application/json
Authorization: Bearer <token-thu-nghiem>
```

Response mẫu tự đặt:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":42,"name":"Minh"}
```

Không có body không phải lúc nào cũng lỗi: ví dụ `204 No Content`. Request thành công ở tầng HTTP cũng chưa chắc thành công theo nghiệp vụ; API có thể trả `200` nhưng payload có trường báo thất bại.

## 2. Dùng Network Inspector

Chạy app debug → **View → Tool Windows → App Inspection** → chọn device/process → **Network Inspector**. Thực hiện đúng một thao tác, chọn khoảng thời gian đó trên timeline, rồi mở request để đọc headers, body và call stack. Công cụ hỗ trợ OkHttp và HttpsURLConnection; Retrofit thường quan sát được vì dùng OkHttp. Nó không bảo đảm hiển thị chi tiết mọi loại kết nối, đặc biệt không thay thế công cụ theo dõi sự kiện Socket.IO. Xem [Network Inspector](https://developer.android.com/studio/debug/network-profiler).

Hãy đọc theo thứ tự:

1. Method và URL có đúng endpoint/môi trường không?
2. Header có token và Content-Type phù hợp không?
3. Request body có đúng tên trường và kiểu dữ liệu không?
4. Status và response body nói gì?
5. Thời gian chờ nằm ở đâu; request có bị gọi lặp không?

## 3. Đọc status để chọn hướng kiểm tra

| Kết quả | Hướng kiểm tra ban đầu |
|---|---|
| 200–299 | HTTP thành công; kiểm tra payload, parse và UI |
| 400 | Dữ liệu gửi sai; đọc thông báo validation |
| 401 | Thiếu/token không hợp lệ hoặc hết hạn |
| 403 | Server từ chối theo quyền/chính sách |
| 404 | Sai path/ID hoặc tài nguyên không tồn tại |
| 429 | Gửi quá nhiều; kiểm tra retry và hướng dẫn server |
| 500–599 | Server/proxy gặp lỗi; ghi lại dữ liệu tái hiện |
| Không có HTTP status | DNS, TLS, mất mạng, timeout hoặc hủy request |

Đây là hướng chẩn đoán, không phải kết luận tuyệt đối. Một server có thể dùng mã lỗi theo quy ước riêng. Đừng sửa DTO chỉ vì mọi lỗi đều bị UI hiển thị thành “Không tải được”.

## 4. Ví dụ điều tra: màn hình luôn rỗng

Giả sử response thực tế:

```json
{
  "data": [{"id": 1, "name": "Lan"}],
  "total": 1
}
```

Nhưng code đang yêu cầu `List<UserDto>` ở root. Mạng có thể thành công; lỗi là shape JSON không khớp model. Cần object chứa `data` và `total`:

```kotlin
data class UsersResponse(
    val data: List<UserDto>?,
    val total: Int?
)
```

Đối chiếu raw body trước khi đổi code. Tiếp tục xem [Gson](07-gson-json-object.md) và [Retrofit](08-retrofit-okhttp-interceptor.md).

## 5. Server chạy trên máy tính thì app gọi địa chỉ nào?

`localhost` trong emulator thường là chính emulator. Android Emulator tiêu chuẩn dùng `10.0.2.2` để truy cập loopback của máy host. Điện thoại thật cần địa chỉ LAN của máy và kết nối/firewall phù hợp. Xem [Emulator network address space](https://developer.android.com/studio/run/emulator-networking-address).

Ví dụ `http://10.0.2.2:3000/` chỉ minh họa server local. Android có chính sách chặn cleartext tùy cấu hình/target; nên dùng HTTPS. Nếu bài thử local cần HTTP, cấu hình ngoại lệ **giới hạn trong debug và host cần dùng**, theo [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config), không bật cleartext rộng cho mọi môi trường.

## 6. Log thế nào cho hữu ích?

Ghi method, path, status, thời gian và request ID nếu server có. Token/password không cần để chẩn đoán; che chúng. Body có thể chứa dữ liệu cá nhân, nên chỉ bật log chi tiết với dữ liệu giả trong debug. Bài 08 có HttpLoggingInterceptor và cách che header.

Nếu Inspector không thấy request, lần lượt kiểm tra process, thời điểm ghi, thư viện hỗ trợ và request thực sự đã chạy chưa. Đừng vội kết luận app không gửi mạng chỉ vì cửa sổ đang trống.

## Bài tập

Với API thử nghiệm do bạn/mentor cung cấp, tạo ba tình huống: thành công, token sai, mất mạng. Ghi một bảng gồm URL, status/exception, payload lỗi và hướng xử lý UI. Bấm nút tải liên tiếp năm lần để kiểm tra app có gửi trùng request không.

## Tự kiểm tra

1. 401 có phải lỗi mất Wi-Fi? — Thường là lỗi xác thực, server đã trả response.
2. 200 nhưng UI rỗng kiểm tra gì? — Payload, model, logic ánh xạ và cập nhật UI.
3. Connection timeout có HTTP status không? — Thường chưa có response HTTP.

## Tiêu chí hoàn thành

- [ ] Đọc được method/URL/headers/body/status của một request.
- [ ] Phân biệt lỗi truyền tải, HTTP, parse và nghiệp vụ.
- [ ] Tái hiện lỗi và viết mô tả đủ để mentor kiểm tra.

[Mục lục](README.md) · [Bài tiếp: Preferences và cache](04-app-data-preferences-cache.md)

