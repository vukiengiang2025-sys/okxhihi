# OKX Kelly Trading Simulator (Android)

Ứng dụng Android native (Kotlin + Jetpack Compose) mô phỏng chiến lược giao dịch định lượng dựa trên công thức Kelly và chỉ báo kỹ thuật RSI với dữ liệu thị trường thực tế từ OKX.

## Tính năng chính
- **Tích hợp OKX Market API**: Lấy dữ liệu nến giá thực tế (1m, 5m, 15m, 1H) trực tiếp từ sàn OKX.
- **Đánh giá chiến lược (Backtest & Calibration)**: Tính toán Win Rate (p), tỷ lệ lời/lỗ (b = win/loss ratio), và tỷ lệ phân bổ vốn tối ưu theo công thức Kelly ($f^* = p - \frac{1-p}{b}$).
- **Giả lập giao dịch thời gian thực (Forward Simulator)**:
  - Chế độ chạy: Liên tục, 1 lệnh, hoặc N lệnh.
  - Phân bổ vốn linh hoạt: Half Kelly (0.5x), Full Kelly (1.0x), Conservative (0.25x).
  - Tự động theo dõi lệnh mở, điểm Take Profit và Stop Loss.
- **Biểu đồ tăng trưởng vốn trực quan**: Canvas vẽ đường cong tài sản thực tế theo thời gian kèm biến động PnL.
- **Gemini AI Quantitative Analyst**: Đánh giá kết quả giao dịch và đưa ra nhận xét rủi ro, phân tích hiệu quả vốn và đề xuất tối ưu hóa thông số.
- **Giao diện hiện đại**: Phong cách Terminal tài chính định lượng cao cấp (Dark theme, neon lime accent) thiết kế theo Material 3 chuẩn Android.

## Kiến trúc kỹ thuật
- **Ngôn ngữ**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Kiến trúc**: MVVM (Model-View-ViewModel) + StateFlow + Coroutines
- **Networking**: Retrofit 2 + OkHttp 4 + Kotlinx Serialization
- **Biểu đồ**: Native Compose Canvas Drawing
- **Bảo mật & Cấu hình**: Secrets Gradle Plugin (.env / .env.example) & SharedPreferences
