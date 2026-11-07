# OKX Kelly Trading Simulator (Android)

Ứng dụng Android native bằng Kotlin/Jetpack Compose để nghiên cứu chiến lược RSI/Kelly trên dữ liệu nến công khai từ OKX. Ứng dụng chỉ mô phỏng, không gửi lệnh và không cần API key OKX. Đây không phải tư vấn đầu tư; mọi kết quả backtest/forward simulation đều có thể khác giao dịch thực tế.

## Tính năng

- Lấy nến thị trường công khai từ OKX, backtest RSI và tính Kelly fraction từ win rate cùng reward/risk.
- Mô phỏng forward trên nến đã đóng, xử lý mỗi nến một lần; nếu một nến chạm cả TP lẫn SL thì giả định bảo thủ rằng SL xảy ra trước.
- Chế độ chạy liên tục, một lệnh hoặc số lệnh giới hạn; biểu đồ vốn và nhật ký giao dịch.
- Phân tích bằng Gemini là tùy chọn; khóa API do người dùng nhập được mã hóa bằng Android Keystore-backed encrypted preferences.

## Giới hạn quan trọng

Backtest hiện chưa mô hình hóa phí, spread, slippage, funding, thanh khoản, gap-fill hay độ trễ thực thi. Dữ liệu nến OHLC không cho biết thứ tự giá intrabar; trường hợp TP/SL cùng bị chạm được tính theo hướng bất lợi. Kelly nhạy với sai số ước lượng, vì vậy không dùng kết quả mô phỏng làm cam kết lợi nhuận hoặc căn cứ duy nhất để giao dịch. Nếu mẫu backtest có dưới 30 lệnh đóng, ứng dụng hiển thị cảnh báo ước lượng Kelly có độ bất định cao. Khi forward simulation phát hiện thiếu một hoặc nhiều nến liên tiếp, phiên sẽ dừng thay vì giả định rằng các mức TP/SL không bị chạm; sau 5 lỗi lấy dữ liệu liên tiếp phiên cũng tự dừng.

## Build cục bộ

Yêu cầu JDK 21, Android SDK Platform 36 và Gradle 9.x. Tại thư mục gốc chạy:

```bash
gradle testDebugUnitTest lint assembleDebug
```

APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions tự chạy unit tests, lint và build APK khi push/pull request vào `main`; tải artifact từ tab Actions.

Gemini API key có thể nhập trong phần Cài đặt. Không đưa key vào Git hoặc `.env.example`. App hiện chỉ đọc dữ liệu công khai của OKX; các trường khóa OKX cũ trong giao diện chỉ còn để tương thích, không được sử dụng để ký/yêu cầu endpoint riêng tư.
