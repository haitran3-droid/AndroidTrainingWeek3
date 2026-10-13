# Bài giảng Android tuần 3 dành cho intern

Bộ bài giảng bám theo toàn bộ phần nội dung đọc được trong ảnh kế hoạch học: Design, Android Studio, Libraries, Ảnh/Video/Camera, Audio/Recorder và Audio Focus. Mỗi chủ đề chính có một file riêng để dễ học và tra cứu.

Ngôn ngữ ví dụ: **Kotlin**, giao diện **XML + Views**. Giải thích bắt đầu từ khái niệm, sau đó mới đến code. Kiến thức và đường dẫn tài liệu được đối chiếu ngày **13/10/2026**.

## Bắt đầu ở đâu?

Đọc [00 — Chuẩn bị và kiến thức nền](00-chuan-bi-va-kien-thuc-nen.md) trước. Mỗi bài có mục tiêu, ví dụ, giải thích, lỗi thường gặp, bài tập, câu hỏi tự kiểm tra và tiêu chí hoàn thành. Một số đoạn code là phần tích hợp vào Activity/Fragment; bài học ghi rõ điều kiện cần có.

## Mục lục và đối chiếu với ảnh

| Ngày trong ảnh | Nhóm | Bài giảng | Nội dung được bao phủ |
|---|---|---|---|
| 1 | Design | [01 — Làm giao diện từ design](01-layout-va-icon-tu-design.md) | Export icon; dp/sp; tạo layout đúng kích thước; đối chiếu design |
| 2 | Android Studio | [02 — Debug database](02-debug-database.md) | Database Inspector; SQL; kiểm tra dữ liệu |
| 2 | Android Studio | [03 — Debug network](03-debug-network.md) | Network Inspector; request/response; tìm lỗi API |
| 2 | Android Studio | [04 — Preferences và cache](04-app-data-preferences-cache.md) | Kiểm tra app data bằng Device Explorer; phân biệt dữ liệu và cache |
| 2 | Android Studio | [05 — Memory leak](05-memory-leak.md) | GC; heap dump; reference; sửa leak theo lifecycle |
| 3 | Libraries | [06 — Glide](06-glide-cache-size-transform.md) | Cài đặt; cơ chế cache; custom size; transform |
| 3 | Libraries | [07 — Gson](07-gson-json-object.md) | Cài đặt; JSON → object; object → JSON; danh sách và lỗi dữ liệu |
| 3 | Libraries | [08 — Retrofit và Interceptor](08-retrofit-okhttp-interceptor.md) | Gọi API; sửa request/response; logging; timeout |
| 3 | Libraries | [09 — Socket.IO](09-socketio-reconnect-timeout.md) | Socket; emit/on; reconnect; connection timeout và ack timeout |
| 4 | Ảnh, Video, Camera | [10 — Lấy ảnh/video từ Gallery](10-gallery-photo-picker.md) | Photo Picker; URI; đọc dữ liệu; quyền truy cập |
| 4 | Ảnh, Video, Camera | [11 — CameraX](11-camerax-preview-chup-anh.md) | Permission; preview; chụp ảnh; lifecycle |
| 4 | Ảnh, Video, Camera | [12 — Crop ảnh](12-crop-anh-camera-gallery.md) | Crop ảnh từ Camera/Gallery; tỉ lệ; file đầu ra |
| 4 | Ảnh, Video, Camera | [13 — Frame realtime](13-camera-frame-realtime.md) | ImageAnalysis; backpressure; đóng frame; sửa pixel |
| 4 | Ảnh, Video, Camera | [14 — ExoPlayer](14-media3-exoplayer.md) | Media3; phát video/audio; buffering; giải phóng player |
| 5 | Audio, Recorder | [15 — MediaPlayer](15-mediaplayer.md) | Phát âm thanh; state machine; prepareAsync; release |
| 5 | Audio, Recorder | [16 — AudioTrack](16-audiotrack-static-stream.md) | AudioPlayback; PCM; Static Mode; Stream Mode |
| 5 | Audio, Recorder | [17 — MediaRecorder](17-mediarecorder.md) | Thu âm; permission; thứ tự cấu hình; xử lý stop |
| 6 + 7 | Audio Focus | [18 — Audio Focus](18-audio-focus.md) | Request focus; gain/loss; duck; abandon; giới hạn Android mới |

“AudioPlayback (Static Mode, Stream Mode)” trong ảnh được diễn giải bằng **AudioTrack**, API Android có `MODE_STATIC` và `MODE_STREAM`. Nếu chương trình nội bộ dùng một lớp wrapper tên AudioPlayback, cần đối chiếu wrapper đó với hai cơ chế trong bài 16. ExoPlayer được học qua **AndroidX Media3**, bộ thư viện hiện dùng namespace `androidx.media3`.

## Cách học phù hợp với intern

1. Đọc mục tiêu và ví dụ đời thường trước khi đọc code.
2. Tạo một màn hình demo nhỏ cho từng bài; chưa ghép tất cả tính năng ngay.
3. Gõ lại code, chạy, rồi thay đổi một tham số và giải thích kết quả.
4. Thực hiện bài tập và ghi lại kết quả mong đợi/kết quả thực tế.
5. Tự trả lời câu hỏi cuối bài bằng lời của mình; nếu chưa giải thích được thì đọc lại phần tương ứng.

Không cần ép 18 bài vào đúng 7 ngày. Ngày trong bảng là nhóm nội dung gốc; thời gian thực hành tùy nền tảng của bạn. Camera, xử lý frame và audio focus thường cần nhiều lần thử trên thiết bị.

## Bài thực hành tổng hợp: Media Lab

Xem [bộ đề bài thực hành dành cho intern](../bai-tap/README.md): 11 bài riêng và 1 bài tổng hợp, có dữ liệu đầu vào, yêu cầu bắt buộc và kịch bản nghiệm thu.

Đối chiếu với [bài giải từng bước](../bai-giai/README.md), gồm hướng dẫn project, server thử nghiệm và lời giải riêng cho từng đề.

Tạo app học tập có các màn hình sau:

- Hồ sơ: layout từ design, tải avatar bằng Glide, lưu tùy chọn giao diện.
- Dữ liệu: gọi API thử nghiệm bằng Retrofit/Gson; quan sát network và database.
- Chat: kết nối một server Socket.IO thử nghiệm; gửi tin, nhận ack, thử mất mạng.
- Ảnh: chọn ảnh/video, chụp bằng CameraX, crop ảnh, hiển thị ảnh xử lý từ frame.
- Media: phát video bằng Media3, phát file âm thanh bằng MediaPlayer, phát PCM bằng AudioTrack, thu âm bằng MediaRecorder.

Đạt yêu cầu khi bạn trình bày được luồng dữ liệu, xử lý việc người dùng hủy/từ chối quyền, giải phóng tài nguyên lúc rời màn hình và chứng minh app xử lý mất audio focus đúng. Đây là đề bài thực hành, bộ tài liệu không kèm app đã build sẵn.

## Quy ước tài liệu

- URL có tên miền `example.com` là chỗ thay bằng server của bạn, không phải API demo đang hoạt động.
- Các version dependency trong bài là mốc ví dụ cụ thể, không được gọi là bản mới nhất. Khi tích hợp vào dự án khác, giữ bộ version tương thích với Gradle/SDK của dự án.
- Các bài phát âm thanh cần áp dụng bài 18; mỗi lần bắt đầu phát chỉ phát sau khi được cấp focus.
- Bài học tập trung vào màn hình đang mở. Phát/thu âm nền cần thiết kế service và notification riêng, không chỉ chuyển code vào `onStop()`.
- Code được rà soát trong tài liệu; chưa có project Gradle hay thiết bị trong thư mục này để xác nhận bằng build/runtime.

