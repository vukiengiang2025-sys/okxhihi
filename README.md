# OKX Kelly Trading Simulator (Android)

Ứng dụng Android native bằng Kotlin/Jetpack Compose để nghiên cứu chiến lược RSI/Kelly trên dữ liệu nến công khai từ OKX. Ứng dụng chỉ mô phỏng, không gửi lệnh và không cần API key OKX. Đây không phải tư vấn đầu tư; mọi kết quả backtest/forward simulation đều có thể khác giao dịch thực tế.

## Tính năng và quy ước mô phỏng

- Lấy nến thị trường công khai từ OKX, backtest RSI và tính Kelly fraction từ các giao dịch đã đóng.
- Tín hiệu RSI chỉ được biết sau khi nến đóng; lệnh được giả định khớp tại giá mở cửa nến kế tiếp, không dùng giá close tạo tín hiệu.
- Nến vào lệnh được kiểm tra TP/SL theo OHLC; nếu TP và SL cùng bị chạm trong một nến, giả định bất lợi là SL xảy ra trước. Không vào lại cùng nến vừa đóng lệnh.
- Dữ liệu lịch sử có khoảng trống sẽ khiến backtest từ chối kết quả; vị thế còn mở cuối mẫu được báo riêng và không tính vào Kelly.
- Mô phỏng forward hỗ trợ chế độ liên tục, một lệnh hoặc số lệnh giới hạn; lưu tối đa 500 lệnh gần nhất và 2.000 điểm vốn để hạn chế tăng bộ nhớ không giới hạn.
- Phân tích Gemini là tùy chọn. Người dùng có thể nhập key cá nhân; key được lưu bằng Android encrypted preferences và gửi qua HTTPS header, không đưa vào mã nguồn hay BuildConfig.

## Giới hạn quan trọng

Backtest chưa mô hình hóa phí, spread, slippage, funding, thanh khoản, gap-fill hay độ trễ thực thi. Dữ liệu nến OHLC không cho biết thứ tự giá intrabar. Kelly nhạy với sai số ước lượng; mẫu nhỏ đặc biệt không đáng tin cậy. Dưới 30 lệnh đóng, ứng dụng cảnh báo rằng ước lượng Kelly có độ bất định cao. Đây là công cụ nghiên cứu, không phải lời khuyên hoặc cam kết lợi nhuận.

Gemini key trong ứng dụng Android vẫn có thể bị trích xuất từ thiết bị/APK; mã hóa khi lưu chỉ bảo vệ dữ liệu ở trạng thái nghỉ, không biến BYOK trên client thành bí mật tuyệt đối. Chỉ nhập khóa cá nhân có giới hạn/quota và có thể thu hồi. Với ứng dụng phân phối rộng hoặc khóa có quyền/quota quan trọng, cần backend proxy; không dùng khóa tổ chức/sản xuất trong ứng dụng client.

## Build

Yêu cầu JDK 21 và Android SDK Platform 36. Gradle Wrapper khóa Gradle 9.9.0 kèm checksum phân phối. Tại thư mục gốc chạy:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions chạy unit tests, lint và build APK debug khi push/pull request vào `main`; tải artifact từ tab Actions. Artifact CI là debug APK, không phải bản release đã ký để phát hành.
