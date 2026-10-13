# 08 — Retrofit và OkHttp Interceptor

## Mục tiêu

Gọi API bằng interface Kotlin, hiểu vai trò Retrofit/OkHttp/Gson, sửa request và response trong interceptor. Đọc bài 03 và 07 trước.

## 1. Ba thư viện làm ba việc gì?

```text
UI → Retrofit interface → OkHttp → HTTP server
                         ↕
                     Interceptor
JSON body ↔ Gson converter ↔ DTO
```

Retrofit biến interface thành lời gọi HTTP; OkHttp truyền request/response; Gson converter chuyển JSON thành model. Interceptor giống trạm xử lý giữa đường: có thể thêm header trước khi gửi và xử lý response trước khi trả lại.

Dependency ví dụ, chọn mốc tương thích để học:

```kotlin
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
```

Đây không phải danh sách version mới nhất; xem [Retrofit tag 2.11.0](https://github.com/square/retrofit/tree/2.11.0) và [OkHttp 4.12.0](https://github.com/square/okhttp/tree/parent-4.12.0). Cần quyền INTERNET. Server `example.com` bên dưới là chỗ thay thế.

## 2. Mô tả endpoint

```kotlin
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface UserApi {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Long): Response<UserDto>
}
```

`UserDto` lấy từ bài 07, nên API thử nghiệm phải trả field `full_name` tương ứng. `Response<T>` cho phép đọc status và error body; nếu hàm trả trực tiếp `T`, cách xử lý HTTP lỗi khác và thường qua exception. Đây là hai thiết kế khác nhau, cần nhất quán.

## 3. Sửa request và response header

```kotlin
import okhttp3.Interceptor
import okhttp3.Response as OkHttpResponse

class HeadersInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        val builder = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("X-Client", "android-training")

        tokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
        val response = chain.proceed(builder.build())
        return response.newBuilder()
            .header("X-Client-Observed", "true")
            .build()
    }
}
```

`Request`/`Response` được xây qua builder, không sửa trực tiếp object gốc. `.header()` thay giá trị header đó; `.addHeader()` thêm một giá trị và có thể tạo header lặp. `chain.proceed()` đưa request đi tiếp; đoạn sau nó nhận response. Header `X-Client-Observed` chỉ được thêm trong response mà app nhận, không quay về server.

Giữ `tokenProvider` an toàn khi truy cập từ thread nền; không đọc View/Activity từ interceptor. Không dùng interceptor để cập nhật UI trực tiếp.

## 4. Tạo client và Retrofit

```kotlin
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val logging = HttpLoggingInterceptor().apply {
    redactHeader("Authorization")
    redactHeader("Cookie")
    level = HttpLoggingInterceptor.Level.BASIC
}

val builder = OkHttpClient.Builder()
    .addInterceptor(HeadersInterceptor { null }) // Gắn kho token của bạn.
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)

if (BuildConfig.DEBUG) builder.addInterceptor(logging)

val api = Retrofit.Builder()
    .baseUrl("https://api.example.com/") // Phải kết thúc bằng /.
    .client(builder.build())
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(UserApi::class.java)
```

`BuildConfig` là class được sinh cho module, cần bật buildConfig generation nếu cấu hình dự án đã tắt. Đặt client dùng chung ở data layer; ví dụ trên không yêu cầu tạo lại client mỗi lần bấm nút.

Connect timeout là chờ kết nối; read timeout giới hạn một lần chờ dữ liệu đọc; write timeout liên quan ghi dữ liệu; call timeout giới hạn toàn bộ call. Read timeout không mặc định là thời gian tối đa toàn bộ tải file. Tăng timeout không sửa được URL sai hoặc server chết.

## 5. Gọi API và phân loại lỗi

```kotlin
// Trong Activity; thêm imports lifecycleScope, launch,
// CancellationException, IOException, JsonParseException.
lifecycleScope.launch {
    try {
        val response = api.getUser(7)
        binding.result.text = if (response.isSuccessful) {
            response.body()?.fullName ?: "Response không có tên"
        } else {
            "HTTP lỗi: ${response.code()}"
        }
    } catch (cancelled: CancellationException) {
        throw cancelled // Không nuốt việc hủy theo lifecycle.
    } catch (error: IOException) {
        binding.result.text = "Không kết nối được hoặc bị timeout"
    } catch (error: JsonParseException) {
        binding.result.text = "JSON không phù hợp model"
    }
}
```

Retrofit suspend dùng lời gọi mạng bất đồng bộ; không dùng `execute()` đồng bộ trên main thread. Vẫn kiểm tra response body null và validation ở bài 07 trước khi coi dữ liệu hợp lệ.

## 6. Nếu cần sửa response body

Body thường chỉ đọc được một lần. `response.body?.string()` tiêu thụ stream; nếu chỉ muốn xem thử, dùng `peekBody(limit)` với giới hạn phù hợp. Nếu đã đọc để đổi dữ liệu, phải tạo body mới và cập nhật metadata.

Ví dụ **chỉ cho response JSON nhỏ trong endpoint thử nghiệm**, gọi bên trong một application interceptor sau proceed:

```kotlin
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject

fun markDemoBody(response: OkHttpResponse): OkHttpResponse {
    val body = response.body ?: return response
    // Chỉ demo body biết trước độ dài nhỏ; không dùng cho streaming/download.
    if (!response.isSuccessful || body.contentLength() !in 1L..65_536L) {
        return response
    }
    if (body.contentType()?.subtype != "json") return response
    val raw = body.string() // Đọc một lần và đóng body gốc.
    val updated = try {
        JSONObject(raw).put("client_demo", true).toString()
    } catch (error: org.json.JSONException) {
        raw // Vẫn phải tạo lại body vì stream cũ đã tiêu thụ.
    }
    return response.newBuilder()
        .removeHeader("Content-Length")
        .removeHeader("Content-Encoding")
        .header("Content-Type", "application/json; charset=utf-8")
        .body(updated.toResponseBody("application/json; charset=utf-8".toMediaType()))
        .build()
}
```

Không gắn helper này toàn cục cho mọi API; giới hạn theo host/path demo trước khi gọi. Với payload gốc là array thì helper giữ raw. Việc đổi body là kỹ thuật cần thận trọng, không phải cách che lỗi backend hay đổi HTTP lỗi thành thành công. Xem [OkHttp Interceptors](https://github.com/square/okhttp/blob/master/docs/features/interceptors.md).

Application interceptor phù hợp logic ở mức lời gọi; network interceptor nhìn từng lần trao đổi mạng, kể cả redirect/retry. Không retry vô hạn trong interceptor. Refresh token cần cơ chế riêng, chống request đồng thời và vòng lặp; đây là phần mở rộng sau khi nắm bài.

## Bài tập

Với server thử nghiệm, thêm `X-Client`, kiểm tra bằng Network Inspector. Trả 401, JSON sai và response thành công để quan sát ba nhánh lỗi. Thêm response header như ví dụ, rồi thử helper đổi body trên một endpoint nhỏ riêng.

## Tự kiểm tra

1. Interceptor thuộc Retrofit hay OkHttp? — OkHttp.
2. Đọc body.string để log rồi trả body cũ được không? — Không, stream đã bị tiêu thụ.
3. readTimeout 20 giây có luôn giới hạn toàn call 20 giây? — Không.

## Tiêu chí hoàn thành

- [ ] Gọi API bằng suspend interface và xử lý HTTP/network/parse.
- [ ] Sửa request và response bằng builder đúng chỗ.
- [ ] Hiểu body đọc một lần, timeout và không log token.

[Mục lục](README.md) · [Bài tiếp: Socket.IO](09-socketio-reconnect-timeout.md)
