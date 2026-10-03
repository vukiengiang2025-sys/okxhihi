package com.example.okxkelly.ui

import android.app.Application
import android.content.Context
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

    private val prefs = application.getSharedPreferences("okx_kelly_prefs", Context.MODE_PRIVATE)

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
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun updateInstId(newInstId: String) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(instId = newInstId.trim()) }
    }

    fun updateBar(newBar: String) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(bar = newBar) }
    }

    fun updateStartCapital(value: Double) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(startCapital = value, currentCapital = value) }
    }

    fun updateTpPercent(value: String) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(tpPercentStr = value) }
    }

    fun updateSlPercent(value: String) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(slPercentStr = value) }
    }

    fun updateKellyMultiplier(value: Double) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(kellyMultiplier = value) }
    }

    fun updateSimMode(mode: String) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(simMode = mode) }
    }

    fun updateMaxTrades(count: Int) {
        if (!_uiState.value.isRunning) _uiState.update { it.copy(maxTrades = count) }
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
                val candles = response.data.mapNotNull { parseOkxCandle(it) }.reversed()

                if (candles.size < 50) {
                    _uiState.update {
                        it.copy(
                            isCalibrating = false,
                            userNotice = "Không đủ dữ liệu lịch sử để đánh giá (nhận được ${candles.size} nến)"
                        )
                    }
                    return@launch
                }

                val tp = (state.tpPercentStr.toDoubleOrNull() ?: 2.0) / 100.0
                val sl = (state.slPercentStr.toDoubleOrNull() ?: 1.0) / 100.0

                val metrics = KellyStrategy.backtestStrategy(candles, tp, sl)
                _uiState.update {
                    it.copy(
                        isCalibrating = false,
                        metrics = metrics,
                        userNotice = "Đã hiệu chuẩn chiến lược: WinRate ${(metrics.winRate * 100).toInt()}%, Kelly f* ${(metrics.kellyFraction * 100).toInt()}%"
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
        if (metrics == null || metrics.kellyFraction <= 0.0) {
            _uiState.update {
                it.copy(userNotice = "Vui lòng Đánh giá chiến lược và đảm bảo Kelly > 0 trước khi chạy giả lập.")
            }
            return
        }

        tradeCount = 0
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

    private suspend fun processTick() {
        val state = _uiState.value
        try {
            val response = NetworkClients.okxService.getCandles(
                instId = state.instId,
                bar = state.bar,
                limit = 50
            )
            val candles = response.data.mapNotNull { parseOkxCandle(it) }.reversed()
            if (candles.size < 15) return

            val latestCandle = candles.last()
            val rsi = KellyStrategy.calculateRSI(candles, 14)
            val currentRsi = rsi.last()
            val prevRsi = rsi[rsi.size - 2]

            val tp = (state.tpPercentStr.toDoubleOrNull() ?: 2.0) / 100.0
            val sl = (state.slPercentStr.toDoubleOrNull() ?: 1.0) / 100.0
            val metrics = state.metrics ?: return

            var openTrade = state.openTrade
            var currentCapital = state.currentCapital
            val liveTrades = state.liveTrades.toMutableList()
            val history = state.capitalHistory.toMutableList()
            var shouldStop = false

            if (openTrade != null) {
                val highestProfit = (latestCandle.high - openTrade.entryPrice) / openTrade.entryPrice
                val lowestDrop = (latestCandle.low - openTrade.entryPrice) / openTrade.entryPrice

                var closed = false
                var won = false
                var exitPrice = 0.0
                var profitRatio = 0.0

                if (lowestDrop <= -sl) {
                    exitPrice = openTrade.entryPrice * (1.0 - sl)
                    profitRatio = -sl
                    won = false
                    closed = true
                } else if (highestProfit >= tp) {
                    exitPrice = openTrade.entryPrice * (1.0 + tp)
                    profitRatio = tp
                    won = true
                    closed = true
                }

                if (closed) {
                    val betSize = currentCapital * metrics.kellyFraction * state.kellyMultiplier
                    val pnl = if (won) betSize * tp else -betSize * sl
                    currentCapital += pnl
                    tradeCount++

                    val completedTrade = openTrade.copy(
                        exitPrice = exitPrice,
                        profitRatio = profitRatio,
                        won = won,
                        exitTime = System.currentTimeMillis()
                    )
                    liveTrades.add(0, completedTrade)
                    history.add(CapitalPoint(time = timeFormat.format(Date()), capital = currentCapital))
                    openTrade = null

                    if (state.simMode == "single" || (state.simMode == "limited" && tradeCount >= state.maxTrades)) {
                        shouldStop = true
                    }
                }
            } else {
                // Entry condition: RSI < 30 cross up to >= 30 -> BUY
                if (prevRsi < 30.0 && currentRsi >= 30.0) {
                    openTrade = Trade(
                        type = "BUY",
                        entryPrice = latestCandle.close,
                        entryTime = System.currentTimeMillis()
                    )
                }
            }

            _uiState.update {
                it.copy(
                    liveCandles = candles,
                    openTrade = openTrade,
                    currentCapital = currentCapital,
                    capitalHistory = history,
                    liveTrades = liveTrades,
                    isRunning = if (shouldStop) false else it.isRunning,
                    userNotice = if (shouldStop) "Đã hoàn thành phiên giả lập ($tradeCount lệnh)." else it.userNotice
                )
            }
            if (shouldStop) {
                simulationJob?.cancel()
            }
        } catch (_: Exception) {
            // Transient network error, will retry on next tick
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
