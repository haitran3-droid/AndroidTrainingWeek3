# 09 — Socket.IO: sự kiện, reconnect và timeout

## Mục tiêu

Hiểu kết nối realtime, gửi/nhận event, khôi phục kết nối và phân biệt hai kiểu timeout. Cần bài 03 về network, bài 05 về listener và một server Socket.IO thử nghiệm.

## 1. Socket khác gọi API như thế nào?

HTTP API thường là hỏi–đáp: app hỏi danh sách, server trả danh sách. Socket giữ một kết nối để hai bên trao đổi sự kiện khi có dữ liệu mới, ví dụ tin nhắn đến.

Socket.IO là giao thức/thư viện ở tầng cao hơn, có event, ack và reconnect; nó có thể dùng polling rồi nâng cấp WebSocket. Client Socket.IO không kết nối tùy ý với server WebSocket thuần. Client Java 2.x dùng với Socket.IO server 3.x/4.x theo [bảng tương thích](https://socketio.github.io/socket.io-client-java/installation.html).

Các từ cần nhớ:

- `connect/disconnect`: mở/đóng kết nối.
- `emit`: gửi event.
- `on/off`: đăng ký/gỡ listener.
- `ack`: phía nhận xác nhận sự kiện bằng callback.
- `room`: nhóm do server quản lý; reconnect có thể cần tham gia lại.

## 2. Cài client Android

```kotlin
implementation("io.socket:socket.io-client:2.1.1") {
    exclude(group = "org.json", module = "json")
}
```

Mốc này theo tài liệu Java client; loại `org.json` vì Android đã có. Cần INTERNET. Nếu dùng chung OkHttp/Retrofit, kiểm tra version Gradle thực sự resolve; đừng mặc định mọi thư viện đều chọn version riêng độc lập.

## 3. Tạo kết nối với cấu hình rõ ràng

Đặt trong Activity demo; URL cần thay bằng server thật:

```kotlin
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URI

private val socket: Socket by lazy {
    val options = IO.Options.builder()
        .setReconnection(true)
        .setReconnectionAttempts(5)
        .setReconnectionDelay(1_000)
        .setReconnectionDelayMax(5_000)
        .setTimeout(10_000)
        .build()
    IO.socket(URI.create("https://example.com"), options)
}

private fun showStatus(text: String) {
    runOnUiThread {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            binding.result.text = text
        }
    }
}

private val onConnect = Emitter.Listener {
    showStatus("Đã kết nối")
    // Nếu server yêu cầu room/subscription, tham gia lại tại đây.
}
private val onDisconnect = Emitter.Listener {
    showStatus("Đã ngắt kết nối")
}
private val onError = Emitter.Listener {
    showStatus("Không kết nối được; kiểm tra server/mạng")
}
private val onMessage = Emitter.Listener { args ->
    val data = args.firstOrNull() as? JSONObject
    showStatus(data?.optString("text") ?: "Payload không đúng dạng")
}
```

Import thêm `androidx.lifecycle.Lifecycle`. Callback socket không mặc định chạy ở main thread; `runOnUiThread` giúp cập nhật View đúng thread. Kiểm tra lifecycle tránh callback trễ cập nhật màn hình đã đóng.

Đăng ký **một lần** trong `onCreate`, trước connect:

```kotlin
socket.on(Socket.EVENT_CONNECT, onConnect)
socket.on(Socket.EVENT_DISCONNECT, onDisconnect)
socket.on(Socket.EVENT_CONNECT_ERROR, onError)
socket.on("chat:message", onMessage)
```

Trong `onStart` gọi `socket.connect()`. Trong `onStop` gọi `socket.disconnect()` cho chính sách demo chỉ kết nối lúc màn hình hiển thị. Trong `onDestroy` gỡ chính xác từng listener bằng `socket.off(eventName, listener)`. App chat thật có thể đặt connection ở repository/service có ownership khác; không dùng chung socket rồi để một màn hình tùy tiện disconnect của màn hình khác. Xem [Java client trên Android](https://socketio.github.io/socket.io-client-java/android.html).

## 4. Gửi event và nhận ack có timeout

```kotlin
import io.socket.client.AckWithTimeout
import java.util.UUID

fun sendDemoMessage(text: String) {
    if (!socket.connected()) {
        showStatus("Chưa kết nối; demo không gửi lúc offline")
        return
    }
    val payload = JSONObject()
        .put("messageId", UUID.randomUUID().toString())
        .put("text", text)

    socket.emit("chat:send", payload, object : AckWithTimeout(5_000) {
        override fun onTimeout() {
            showStatus("Chưa nhận xác nhận sau 5 giây")
        }
        override fun onSuccess(vararg args: Any?) {
            val result = args.firstOrNull() as? JSONObject
            showStatus("Server xác nhận: ${result?.optBoolean("ok")}")
        }
    })
}
```

Ack chỉ chứng minh việc mà server định nghĩa trong callback. Nếu server ack trước khi ghi DB, ack không chứng minh dữ liệu đã lưu. Timeout cũng không chứng minh server chưa xử lý: có thể server đã xử lý nhưng ack bị mất. Khi retry cần dùng **cùng messageId** và server chống trùng, không tạo ID mới cho mỗi retry của một tin.

`AckWithTimeout` có từ Java client 2.1.0; không sao chép `socket.timeout(...).emit(...)` của JS client vào Kotlin nếu Java API không có method đó. Xem [Emitting events và timeout](https://socketio.github.io/socket.io-client-java/emitting_events.html).

## 5. Server nhỏ để hiểu hai đầu

Nếu đã có Node.js, tạo thư mục demo riêng, chạy `npm init -y` rồi `npm install socket.io@4`. Tạo `server.cjs` và chạy `node server.cjs`:

```javascript
const { Server } = require("socket.io");
const io = new Server(3000);

io.on("connection", (socket) => {
  socket.on("chat:send", (payload, ack) => {
    if (typeof payload?.text !== "string") {
      if (typeof ack === "function") ack({ ok: false });
      return;
    }
    socket.emit("chat:message", payload);
    if (typeof ack === "function") ack({ ok: true });
  });
});
```

Đây là server echo để học, chưa có auth/lưu DB/chống trùng. Emulator tiêu chuẩn dùng host `10.0.2.2`; local HTTP cần cấu hình debug giới hạn theo bài 03. Chưa có Node thì dùng server mentor cung cấp; không cần dựng server để hiểu client.

## 6. Reconnect không đồng nghĩa đồng bộ dữ liệu

Khi mất mạng ngoài ý muốn, client có thể thử lại với thời gian chờ tăng và yếu tố ngẫu nhiên. `reconnectionAttempts` giới hạn số lần thử; `timeout` là thời gian chờ mỗi **connection attempt**, không phải ack timeout. Xem [Initialization options](https://socketio.github.io/socket.io-client-java/initialization.html).

Manual `disconnect()` khác mất mạng: bạn chủ động ngắt thì cần `connect()` khi muốn mở lại. Đăng ký `onMessage` bên trong onConnect sẽ có nguy cơ nhân listener mỗi lần reconnect. Sau reconnect cần kiểm tra lại auth, room/subscription và lấy phần dữ liệu bỏ lỡ bằng cursor/ID; không mặc định mọi tin sẽ tự được khôi phục.

## Bài tập

Gửi một tin và quan sát event/ack. Tắt mạng 10 giây rồi bật lại. Ghi số lần reconnect; gửi năm tin để kiểm tra mỗi tin chỉ hiện một lần. Bỏ callback ack ở server, phân biệt “event vẫn đến” với “client báo timeout”.

## Tự kiểm tra

1. Connection timeout và ack timeout giống nhau? — Không.
2. Reconnect có tự phục hồi mọi tin bỏ lỡ? — Không, cần cơ chế đồng bộ.
3. Timeout có cho phép retry không chống trùng? — Không nếu thao tác có tác dụng phụ.

## Tiêu chí hoàn thành

- [ ] Gửi/nhận event và cập nhật UI đúng thread.
- [ ] Không tạo listener trùng, gỡ listener theo owner.
- [ ] Phân biệt mất mạng, manual disconnect, reconnect và ack timeout.

[Mục lục](README.md) · [Bài tiếp: Gallery](10-gallery-photo-picker.md)

