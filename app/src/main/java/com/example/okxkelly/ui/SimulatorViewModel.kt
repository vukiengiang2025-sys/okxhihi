package com.example.okxkelly.ui

import android.app.Application
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.okxkelly.BuildConfig
import com.example.okxkelly.data.api.NetworkClients
import com.example.okxkelly.data.api.parseOkxCandle
import com.example.okxkelly.data.model.Candle
import com.example.okxkelly.data.model.CapitalPoint
import com.example.okxkelly.data.model.GeminiContent
import com.example.okxkelly.data.model.GeminiPart
import com.example.okxkelly.data.model.GeminiRequest
import com.example.okxkelly.data.model.SimMetrics
import com.example.okxkelly.data.model.Trade
import com.example.okxkelly.domain.KellyStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SimulatorUiState(
    val instId: String = "BTC-USDT",
    val bar: String = "1m",
    val startCapital: Double = 1000.0,
    val tpPercentStr: String = "2.0",
    val slPercentStr: String = "1.0",
    val kellyMultiplier: Double = 0.5,
    val simMode: String = "continuous", // "continuous", "single", "limited"
    val maxTrades: Int = 5,
    val metrics: SimMetrics? = null,
    val isRunning: Boolean = false,
    val currentCapital: Double = 1000.0,
    val capitalHistory: List<CapitalPoint> = emptyList(),
    val liveTrades: List<Trade> = emptyList(),
    val openTrade: Trade? = null,
    val liveCandles: List<Candle> = emptyList(),
    val isCalibrating: Boolean = false,
    val isAnalyzing: Boolean = false,
    val aiAnalysis: String? = null,
    val userNotice: String? = null,
    val showSettings: Boolean = false,
    val okxApiKey: String = "",
    val okxSecret: String = "",
    val okxPassphrase: String = "",
    val geminiApiKey: String = ""
)

class SimulatorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = EncryptedSharedPreferences.create(
        application,
        "okx_kelly_secure_prefs",
        MasterKey.Builder(application).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private var lastProcessedCandleTimestamp = Long.MIN_VALUE
    init {
        val legacyPrefs = application.getSharedPreferences("okx_kelly_prefs", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        listOf("okx_api_key", "okx_api_secret", "okx_api_passphrase", "gemini_api_key").forEach { key ->
            val value = legacyPrefs.getString(key, null)
            if (!value.isNullOrBlank() && prefs.getString(key, null).isNullOrBlank()) editor.putString(key, value)
        }
        editor.apply()
        legacyPrefs.edit().clear().apply()
    }

    private val _uiState = MutableStateFlow(
        SimulatorUiState(
            okxApiKey = prefs.getString("okx_api_key", "") ?: "",
            okxSecret = prefs.getString("okx_api_secret", "") ?: "",
            okxPassphrase = prefs.getString("okx_api_passphrase", "") ?: "",
            geminiApiKey = prefs.getString("gemini_api_key", "") ?: ""
        )
    )
    val uiState: StateFlow<SimulatorUiState> = _uiState.asStateFlow()

    private var simulationJob: Job? = null
    private var tradeCount = 0
    private var consecutiveApiFailures = 0
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private fun invalidateCalibration(transform: (SimulatorUiState) -> SimulatorUiState) {
        if (!_uiState.value.isRunning) _uiState.update { transform(it).copy(metrics = null) }
    }
    fun updateInstId(newInstId: String) = invalidateCalibration { it.copy(instId = newInstId.trim()) }

    fun updateBar(newBar: String) = invalidateCalibration { it.copy(bar = newBar) }

    fun updateStartCapital(value: Double) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(startCapital = value, currentCapital = value) }
    }

    fun updateTpPercent(value: String) = invalidateCalibration { it.copy(tpPercentStr = value) }

    fun updateSlPercent(value: String) = invalidateCalibration { it.copy(slPercentStr = value) }

    fun updateKellyMultiplier(value: Double) {
        if (value.isFinite() && value in 0.0..1.0 && !_uiState.value.isRunning) _uiState.update { it.copy(kellyMultiplier = value) }
    }

    fun updateSimMode(mode: String) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(simMode = mode) }
    }

    fun updateMaxTrades(count: Int) {
        if (count > 0 && !_uiState.value.isRunning) _uiState.update { it.copy(maxTrades = count) }
    }

    fun setShowSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(userNotice = null) }
    }

    fun saveSettings(okxKey: String, okxSec: String, okxPass: String, geminiKey: String) {
        prefs.edit()
            .putString("okx_api_key", okxKey)
            .putString("okx_api_secret", okxSec)
            .putString("okx_api_passphrase", okxPass)
            .putString("gemini_api_key", geminiKey)
            .apply()

        _uiState.update {
            it.copy(
                okxApiKey = okxKey,
                okxSecret = okxSec,
                okxPassphrase = okxPass,
                geminiApiKey = geminiKey,
                showSettings = false,
                userNotice = "Đã lưu cài đặt API thành công"
            )
        }
    }

    fun autoCalibrate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCalibrating = true, userNotice = null) }
            try {
                val state = _uiState.value
                val response = NetworkClients.okxService.getHistoryCandles(
                    instId = state.instId,
                    bar = state.bar,
                    limit = 100
                )
                if (response.code != "0") error(response.msg ?: "OKX API error (${response.code})")
                val candles = response.data.mapNotNull { parseOkxCandle(it) }.filter { it.confirm }.distinctBy { it.timestamp }.sortedBy { it.timestamp }

                if (candles.size < 50) {
                    _uiState.update {
                        it.copy(
                            isCalibrating = false,
                            userNotice = "Không đủ dữ liệu lịch sử để đánh giá (nhận được ${candles.size} nến)"
                        )
                    }
                    return@launch
                }

                val tpPct = state.tpPercentStr.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.01..50.0 }
                    ?: error("Take profit phải nằm trong khoảng 0,01%–50%")
                val slPct = state.slPercentStr.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.01..50.0 }
                    ?: error("Stop loss phải nằm trong khoảng 0,01%–50%")
                val metrics = KellyStrategy.backtestStrategy(candles, tpPct / 100.0, slPct / 100.0)
                val current = _uiState.value
                if (current.instId != state.instId || current.bar != state.bar || current.tpPercentStr != state.tpPercentStr || current.slPercentStr != state.slPercentStr) {
                    _uiState.update { it.copy(isCalibrating = false, metrics = null, userNotice = "Thông số đã đổi trong lúc hiệu chuẩn; hãy chạy lại.") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isCalibrating = false,
                        metrics = metrics,
                        userNotice = buildString {
                            append("Đã hiệu chuẩn từ ${metrics.totalTrades} lệnh đóng: WinRate ${(metrics.winRate * 100).toInt()}%, Kelly f* ${(metrics.kellyFraction * 100).toInt()}%.")
                            if (metrics.totalTrades < 30) append(" Mẫu dưới 30 lệnh nên ước lượng Kelly rất nhiễu; chỉ xem là tham khảo.")
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCalibrating = false,
                        userNotice = "Lỗi khi lấy dữ liệu OKX: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun startSimulation() {
        val state = _uiState.value
        val metrics = state.metrics
        val tpPct = state.tpPercentStr.toDoubleOrNull()
        val slPct = state.slPercentStr.toDoubleOrNull()
        if (state.startCapital <= 0.0 || !state.startCapital.isFinite() || tpPct == null || tpPct !in 0.01..50.0 || slPct == null || slPct !in 0.01..50.0 || state.kellyMultiplier <= 0.0 || state.kellyMultiplier > 1.0) {
            _uiState.update { it.copy(userNotice = "Vui lòng kiểm tra vốn, TP/SL (0,01%–50%) và hệ số Kelly trước khi chạy.") }
            return
        }
        if (metrics == null || metrics.kellyFraction <= 0.0) {
            _uiState.update {
                it.copy(userNotice = "Vui lòng Đánh giá chiến lược và đảm bảo Kelly > 0 trước khi chạy giả lập.")
            }
            return
        }

        val expectedIntervalMillis = barIntervalMillis(state.bar)
        if (expectedIntervalMillis == null) {
            _uiState.update { it.copy(userNotice = "Khung nến không được hỗ trợ; vui lòng chọn 1m, 5m, 15m hoặc 1H.") }
            return
        }
        tradeCount = 0
        consecutiveApiFailures = 0
        lastProcessedCandleTimestamp = Long.MIN_VALUE
        val initialPoints = listOf(
            CapitalPoint(time = timeFormat.format(Date()), capital = state.startCapital)
        )

        _uiState.update {
            it.copy(
                isRunning = true,
                currentCapital = state.startCapital,
                capitalHistory = initialPoints,
                liveTrades = emptyList(),
                openTrade = null,
                userNotice = "Đang chạy giả lập với dữ liệu thị trường trực tiếp từ OKX..."
            )
        }

        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRunning) {
                processTick()
                delay(3000L) // Poll every 3 seconds
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _uiState.update { it.copy(isRunning = false, userNotice = "Đã dừng giả lập.") }
    }

    private fun barIntervalMillis(bar: String): Long? = when (bar) {
        "1m" -> 60_000L
        "5m" -> 5 * 60_000L
        "15m" -> 15 * 60_000L
        "1H" -> 60 * 60_000L
        else -> null
    }

    private suspend fun processTick() {
        val state = _uiState.value
        try {
            val interval = barIntervalMillis(state.bar) ?: error("Unsupported candle interval")
            val response = NetworkClients.okxService.getCandles(state.instId, state.bar, limit = 50)
            if (response.code != "0") error(response.msg ?: "OKX API error (${response.code})")
            val candles = response.data.mapNotNull { parseOkxCandle(it) }
                .filter { it.confirm }
                .distinctBy { it.timestamp }
                .sortedBy { it.timestamp }
            if (candles.size < 16) error("Không đủ nến đã xác nhận để tính RSI")
            consecutiveApiFailures = 0

            if (lastProcessedCandleTimestamp == Long.MIN_VALUE) {
                // Start from a live baseline; never create a position from history before the session.
                lastProcessedCandleTimestamp = candles.last().timestamp
                _uiState.update { it.copy(liveCandles = candles) }
                return
            }

            val pendingCandles = candles.filter { it.timestamp > lastProcessedCandleTimestamp }
            if (pendingCandles.isEmpty()) return
            if (KellyStrategy.hasCandleGap(lastProcessedCandleTimestamp, pendingCandles, interval)) {
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        liveCandles = candles,
                        userNotice = "Đã dừng giả lập an toàn: phát hiện thiếu nến dữ liệu. Hãy hiệu chuẩn và bắt đầu phiên mới."
                    )
                }
                simulationJob?.cancel()
                return
            }

            val rsi = KellyStrategy.calculateRSI(candles, 14)
            val tp = (state.tpPercentStr.toDoubleOrNull() ?: return) / 100.0
            val sl = (state.slPercentStr.toDoubleOrNull() ?: return) / 100.0
            val metrics = state.metrics ?: return
            var openTrade = state.openTrade
            var capital = state.currentCapital
            val completedTrades = state.liveTrades.toMutableList()
            val history = state.capitalHistory.toMutableList()
            var shouldStop = false

            for (candle in pendingCandles) {
                val index = candles.indexOfFirst { it.timestamp == candle.timestamp }
                var closedThisCandle = false
                if (openTrade != null) {
                    val best = (candle.high - openTrade.entryPrice) / openTrade.entryPrice
                    val worst = (candle.low - openTrade.entryPrice) / openTrade.entryPrice
                    // If TP and SL are both touched within one candle, assume the adverse stop first.
                    val won = worst > -sl && best >= tp
                    if (worst <= -sl || best >= tp) {
                        val realizedRatio = if (won) tp else -sl
                        val exit = openTrade.entryPrice * (1.0 + realizedRatio)
                        val riskBudget = capital * metrics.kellyFraction * state.kellyMultiplier
                        val pnl = if (won) riskBudget * (tp / sl) else -riskBudget
                        capital = (capital + pnl).coerceAtLeast(0.0)
                        tradeCount++
                        completedTrades.add(0, openTrade.copy(exitPrice = exit, profitRatio = realizedRatio, won = won, exitTime = candle.timestamp))
                        history.add(CapitalPoint(time = timeFormat.format(Date()), capital = capital))
                        openTrade = null
                        closedThisCandle = true
                        if (state.simMode == "single" || (state.simMode == "limited" && tradeCount >= state.maxTrades) || capital <= 0.0) {
                            shouldStop = true
                        }
                    }
                }
                // Do not close and reopen at the same candle's close after an intrabar exit.
                if (!shouldStop && openTrade == null && !closedThisCandle && index >= 15 && rsi[index - 1] < 30.0 && rsi[index] >= 30.0) {
                    openTrade = Trade(type = "BUY", entryPrice = candle.close, entryTime = candle.timestamp)
                }
                lastProcessedCandleTimestamp = candle.timestamp
                if (shouldStop) break
            }

            _uiState.update {
                it.copy(
                    liveCandles = candles,
                    openTrade = openTrade,
                    currentCapital = capital,
                    capitalHistory = history,
                    liveTrades = completedTrades,
                    isRunning = if (shouldStop) false else it.isRunning,
                    userNotice = if (shouldStop) "Đã hoàn thành phiên giả lập ($tradeCount lệnh)." else it.userNotice
                )
            }
            if (shouldStop) simulationJob?.cancel()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            consecutiveApiFailures++
            val stopAfterRepeatedFailures = consecutiveApiFailures >= 5
            _uiState.update {
                it.copy(
                    isRunning = if (stopAfterRepeatedFailures) false else it.isRunning,
                    userNotice = if (stopAfterRepeatedFailures) {
                        "Đã dừng giả lập sau 5 lỗi dữ liệu liên tiếp: ${e.localizedMessage ?: "hãy kiểm tra kết nối"}"
                    } else {
                        "Lỗi dữ liệu thị trường ($consecutiveApiFailures/5); đang thử lại: ${e.localizedMessage ?: "thử lại sau"}"
                    }
                )
            }
            if (stopAfterRepeatedFailures) simulationJob?.cancel()
        }
    }
    fun runAIAnalysis() {
        val state = _uiState.value
        val effectiveGeminiKey = if (state.geminiApiKey.isNotBlank()) {
            state.geminiApiKey
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        if (effectiveGeminiKey.isBlank() || effectiveGeminiKey == "your_api_key_here") {
            _uiState.update {
                it.copy(
                    showSettings = true,
                    userNotice = "Vui lòng nhập Gemini API Key trong Cài đặt để kích hoạt AI Evaluation."
                )
            }
            return
        }

        if (state.liveTrades.isEmpty()) {
            _uiState.update {
                it.copy(userNotice = "Cần có ít nhất 1 lệnh đã hoàn thành để AI đánh giá. Hãy khởi chạy giả lập trước.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, aiAnalysis = null, userNotice = null) }
            try {
                val totalTrades = state.liveTrades.size
                val wins = state.liveTrades.count { it.won == true }
                val losses = totalTrades - wins
                val winRate = if (totalTrades > 0) (wins.toDouble() / totalTrades) * 100.0 else 0.0

                val prompt = """
                Bạn là chuyên gia phân tích định lượng (Quantitative Analyst). Hãy đánh giá kết quả của chiến lược giao dịch tự động dựa trên công thức Kelly:
                
                Thông số giả lập:
                - Cặp giao dịch: ${state.instId} (Khung: ${state.bar})
                - Vốn ban đầu: $${String.format(Locale.US, "%.2f", state.startCapital)}
                - Vốn hiện tại: $${String.format(Locale.US, "%.2f", state.currentCapital)}
                - Lãi/Lỗ ròng: $${String.format(Locale.US, "%.2f", state.currentCapital - state.startCapital)}
                - Tổng số lệnh: $totalTrades
                - Lệnh Thắng / Thua: $wins / $losses
                - Tỷ lệ Thắng (Win Rate): ${String.format(Locale.US, "%.1f", winRate)}%
                - Hệ số Kelly đã dùng: ${state.kellyMultiplier}x
                
                Yêu cầu:
                1. Đánh giá mức độ hiệu quả của phương pháp Kelly trong đợt giả lập này. (Công thức có phát huy tác dụng bảo vệ vốn / tối ưu lợi nhuận không?)
                2. Nhận xét về Tỷ lệ Thắng và Mức Rủi Ro.
                3. Đề xuất cải tiến (ví dụ: nên đổi khung thời gian, hay giảm/tăng hệ số Kelly, điều chỉnh Take Profit / Stop Loss).
                Trả lời ngắn gọn, súc tích, mang tính chuyên môn cao, chia làm 3 đoạn văn nhỏ tương ứng 3 yêu cầu, viết bằng tiếng Việt.
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    )
                )

                val response = NetworkClients.geminiService.generateContent(
                    apiKey = effectiveGeminiKey,
                    request = request
                )

                val analysisText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Không nhận được phản hồi từ mô hình AI."

                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        aiAnalysis = analysisText,
                        userNotice = "Đã phân tích xong kết quả từ Gemini AI"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        userNotice = "Lỗi khi gọi Gemini AI: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
    }
}
