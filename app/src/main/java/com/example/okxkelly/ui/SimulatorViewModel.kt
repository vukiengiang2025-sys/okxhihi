package com.example.okxkelly.ui

import android.app.Application
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.okxkelly.data.api.NetworkClients
import com.example.okxkelly.data.api.OkxIntervals
import com.example.okxkelly.data.api.OkxHistoryStopReason
import com.example.okxkelly.data.api.loadOkxCandleHistory
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

private const val MAX_RECENT_TRADES = 500
private const val MAX_EQUITY_POINTS = 2_000
internal val HISTORY_LIMIT_OPTIONS = listOf(100, 300, 1_000, 5_000, 10_000)

data class SimulatorUiState(
    val instId: String = "BTC-USDT",
    val bar: String = "1m",
    val historyLimit: Int = 1_000,
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
    val totalClosedTrades: Int = 0,
    val totalWins: Int = 0,
    val totalLosses: Int = 0,
    val openTrade: Trade? = null,
    val liveCandles: List<Candle> = emptyList(),
    val isCalibrating: Boolean = false,
    val isAnalyzing: Boolean = false,
    val aiAnalysis: String? = null,
    val userNotice: String? = null,
    val showSettings: Boolean = false,
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
        val legacyGeminiKey = legacyPrefs.getString("gemini_api_key", null)
        val existingGeminiKey = prefs.getString("gemini_api_key", null)
        var migrationSucceeded = true
        if (!legacyGeminiKey.isNullOrBlank() && existingGeminiKey.isNullOrBlank()) {
            migrationSucceeded = prefs.edit().putString("gemini_api_key", legacyGeminiKey).commit()
        }
        // Retire unused OKX credentials and clear legacy storage only after Gemini migration succeeds.
        prefs.edit()
            .remove("okx_api_key")
            .remove("okx_api_secret")
            .remove("okx_api_passphrase")
            .commit()
        if (migrationSucceeded) legacyPrefs.edit().clear().commit()
    }
    private val _uiState = MutableStateFlow(
        SimulatorUiState(geminiApiKey = prefs.getString("gemini_api_key", "") ?: "")
    )
    val uiState: StateFlow<SimulatorUiState> = _uiState.asStateFlow()

    private var simulationJob: Job? = null
    private var calibrationJob: Job? = null
    private var analysisJob: Job? = null
    private var tradeCount = 0
    private var consecutiveApiFailures = 0
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private fun invalidateCalibration(transform: (SimulatorUiState) -> SimulatorUiState) {
        if (_uiState.value.isRunning) return
        val wasCalibrating = _uiState.value.isCalibrating
        if (wasCalibrating) calibrationJob?.cancel()
        _uiState.update { current ->
            transform(current).copy(
                metrics = null,
                liveCandles = emptyList(),
                isCalibrating = false,
                userNotice = if (wasCalibrating) {
                    "Cấu hình đã đổi; đã hủy lượt tải lịch sử cũ. Hãy chạy backtest lại."
                } else {
                    "Thông số đã đổi; kết quả cũ đã xóa. Hãy chạy Backtest OKX lại."
                }
            )
        }
    }
    fun updateInstId(newInstId: String) = invalidateCalibration { it.copy(instId = newInstId.trim()) }

    fun updateBar(newBar: String) {
        if (newBar in OkxIntervals.supportedBars) invalidateCalibration { it.copy(bar = newBar) }
    }

    fun updateHistoryLimit(limit: Int) {
        if (limit in HISTORY_LIMIT_OPTIONS) invalidateCalibration { it.copy(historyLimit = limit) }
    }

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

    fun saveSettings(geminiKey: String) {
        prefs.edit().putString("gemini_api_key", geminiKey.trim()).apply()
        _uiState.update {
            it.copy(
                geminiApiKey = geminiKey.trim(),
                showSettings = false,
                userNotice = "Đã lưu khóa Gemini trên thiết bị. Khóa dùng cho Gemini AI, không cần cho dữ liệu OKX."
            )
        }
    }
    fun autoCalibrate() {
        if (_uiState.value.isRunning || _uiState.value.isCalibrating) return
        calibrationJob = viewModelScope.launch {
            val state = _uiState.value
            _uiState.update {
                it.copy(
                    isCalibrating = true,
                    metrics = null,
                    liveCandles = emptyList(),
                    userNotice = "Đang tải ${state.historyLimit} nến lịch sử công khai từ OKX..."
                )
            }
            try {
                val history = loadOkxCandleHistory(
                    targetCount = state.historyLimit,
                    fetchPage = { limit, after ->
                        NetworkClients.okxService.getHistoryCandles(
                            instId = state.instId,
                            bar = state.bar,
                            limit = limit,
                            after = after
                        )
                    },
                    onProgress = { loaded, target, pages ->
                        _uiState.update {
                            it.copy(userNotice = "Đang tải lịch sử OKX: $loaded/$target nến đã xác nhận (trang $pages)...")
                        }
                    }
                )
                when (history.stopReason) {
                    OkxHistoryStopReason.TARGET_REACHED,
                    OkxHistoryStopReason.NO_MORE_DATA -> Unit
                    OkxHistoryStopReason.CURSOR_STALLED -> error("OKX cursor không tiến; dữ liệu lịch sử chưa đầy đủ nên không chạy backtest.")
                    OkxHistoryStopReason.PAGE_LIMIT_REACHED -> error("Đã chạm giới hạn an toàn ${history.pagesFetched} trang, mới nhận ${history.candles.size}/${state.historyLimit} nến; hãy giảm cỡ mẫu.")
                }
                val candles = KellyStrategy.normalizeCandles(history.candles)
                val latestState = _uiState.value
                if (latestState.instId != state.instId || latestState.bar != state.bar || latestState.historyLimit != state.historyLimit || latestState.tpPercentStr != state.tpPercentStr || latestState.slPercentStr != state.slPercentStr) {
                    _uiState.update { it.copy(isCalibrating = false, userNotice = "Cấu hình đã thay đổi trong lúc tải; kết quả cũ bị bỏ qua. Hãy chạy lại.") }
                    return@launch
                }
                _uiState.update { it.copy(liveCandles = candles) }

                if (candles.size < 50) {
                    _uiState.update {
                        it.copy(
                            isCalibrating = false,
                            userNotice = "Không đủ nến đã xác nhận để đánh giá (nhận ${candles.size}/${state.historyLimit}); cần tối thiểu 50."
                        )
                    }
                    return@launch
                }

                val tpPct = state.tpPercentStr.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.01..50.0 }
                    ?: error("Take profit phải nằm trong khoảng 0,01%–50%")
                val slPct = state.slPercentStr.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.01..50.0 }
                    ?: error("Stop loss phải nằm trong khoảng 0,01%–50%")
                val expectedInterval = OkxIntervals.intervalMillis(state.bar) ?: error("Khung nến không hợp lệ")
                val backtest = KellyStrategy.backtestDetailed(candles, tpPct / 100.0, slPct / 100.0, expectedInterval)
                val metrics = backtest.metrics
                val current = _uiState.value
                if (current.instId != state.instId || current.bar != state.bar || current.historyLimit != state.historyLimit || current.tpPercentStr != state.tpPercentStr || current.slPercentStr != state.slPercentStr) {
                    _uiState.update { it.copy(isCalibrating = false, metrics = null, userNotice = "Thông số đã đổi trong lúc hiệu chuẩn; hãy chạy lại.") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isCalibrating = false,
                        metrics = metrics,
                        userNotice = buildString {
                            if (metrics.totalTrades == 0) {
                                append("Backtest hoàn tất nhưng mẫu ${candles.size} nến không tạo lệnh đóng nào; Kelly=0 nên không thể chạy mô phỏng. Hãy thử khung thời gian khác hoặc chạy lại sau.")
                            } else {
                                append("Backtest hoàn tất từ ${metrics.totalTrades} lệnh đóng: WinRate ${(metrics.winRate * 100).toInt()}%, Kelly f* ${(metrics.kellyFraction * 100).toInt()}%.")
                                if (metrics.totalTrades < 30) append(" Mẫu dưới 30 lệnh nên ước lượng Kelly rất nhiễu; chỉ xem là tham khảo.")
                                if (backtest.openTrade != null) append(" Còn 1 lệnh chưa đóng ở cuối mẫu; lệnh này không nằm trong Kelly.")
                            }
                            append(" Mẫu dữ liệu: ${candles.size}/${state.historyLimit} nến.")
                            if (history.historyExhausted && candles.size < state.historyLimit) {
                                append(" OKX không trả đủ lịch sử yêu cầu; kết quả chỉ dùng số nến thực nhận.")
                            }
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCalibrating = false,
                        userNotice = "Backtest thất bại: ${e.localizedMessage ?: "không thể lấy hoặc xác thực dữ liệu OKX"}"
                    )
                }
            } finally {
                _uiState.update { if (it.isCalibrating) it.copy(isCalibrating = false) else it }
            }
        }
    }

    fun startSimulation() {
        val state = _uiState.value
        if (state.isRunning) return
        if (state.isCalibrating) {
            _uiState.update { it.copy(userNotice = "Đợi backtest tải và kiểm tra dữ liệu xong trước khi bắt đầu mô phỏng.") }
            return
        }
        val metrics = state.metrics
        val tpPct = state.tpPercentStr.toDoubleOrNull()
        val slPct = state.slPercentStr.toDoubleOrNull()
        if (state.startCapital <= 0.0 || !state.startCapital.isFinite() || tpPct == null || tpPct !in 0.01..50.0 || slPct == null || slPct !in 0.01..50.0 || !state.kellyMultiplier.isFinite() || state.kellyMultiplier <= 0.0 || state.kellyMultiplier > 1.0) {
            _uiState.update { it.copy(userNotice = "Vui lòng kiểm tra vốn, TP/SL (0,01%–50%) và hệ số Kelly trước khi chạy.") }
            return
        }
        if (metrics == null || !metrics.kellyFraction.isFinite() || metrics.kellyFraction !in 0.0000001..1.0) {
            _uiState.update {
                it.copy(userNotice = "Vui lòng Đánh giá chiến lược và đảm bảo Kelly > 0 trước khi chạy giả lập.")
            }
            return
        }

        val expectedIntervalMillis = OkxIntervals.intervalMillis(state.bar)
        if (expectedIntervalMillis == null) {
            _uiState.update { it.copy(userNotice = "Khung nến không được hỗ trợ.") }
            return
        }
        val pollingDelayMillis = OkxIntervals.livePollingDelayMillis(expectedIntervalMillis)
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
                totalClosedTrades = 0,
                totalWins = 0,
                totalLosses = 0,
                openTrade = null,
                userNotice = "Đang chạy giả lập với dữ liệu thị trường trực tiếp từ OKX..."
            )
        }

        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRunning) {
                processTick()
                delay(pollingDelayMillis)
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
            val interval = OkxIntervals.intervalMillis(state.bar) ?: error("Unsupported candle interval")
            val response = NetworkClients.okxService.getCandles(state.instId, state.bar, limit = 50)
            if (response.code != "0") error(response.msg ?: "OKX API error (${response.code})")
            val candles = KellyStrategy.normalizeCandles(
                response.data.mapNotNull { parseOkxCandle(it) }.filter { it.confirm }
            )
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
            if (!metrics.kellyFraction.isFinite() || metrics.kellyFraction !in 0.0000001..1.0) error("Kelly fraction is invalid")
            var openTrade = state.openTrade
            var capital = state.currentCapital
            val completedTrades = state.liveTrades.toMutableList()
            val history = state.capitalHistory.toMutableList()
            var totalClosedTrades = state.totalClosedTrades
            var totalWins = state.totalWins
            var totalLosses = state.totalLosses
            var shouldStop = false
            var arithmeticStopNotice: String? = null

            for (candle in pendingCandles) {
                val index = candles.indexOfFirst { it.timestamp == candle.timestamp }
                // A crossover is known at the previous candle close, so fill at this candle's open.
                // A position opened here is then exposed to this candle's full high/low range.
                if (openTrade == null && index >= KellyStrategy.MIN_ENTRY_INDEX && KellyStrategy.crossedAboveOversold(rsi, index - 1)) {
                    openTrade = Trade(type = "BUY", entryPrice = candle.open, entryTime = candle.timestamp)
                }
                val activeTrade = openTrade
                if (activeTrade != null) {
                    val realizedRatio = KellyStrategy.exitRatioOnCandle(activeTrade.entryPrice, candle, tp, sl)
                    if (realizedRatio != null) {
                        val won = realizedRatio > 0.0
                        val exit = activeTrade.entryPrice * (1.0 + realizedRatio)
                        val riskBudget = capital * metrics.kellyFraction * state.kellyMultiplier
                        val pnl = if (won) riskBudget * (tp / sl) else -riskBudget
                        val nextCapital = capital + pnl
                        if (!riskBudget.isFinite() || riskBudget <= 0.0 || !pnl.isFinite() || !nextCapital.isFinite()) {
                            shouldStop = true
                            arithmeticStopNotice = "Dừng an toàn: phép tính vốn vượt phạm vi số hợp lệ."
                            break
                        }
                        capital = nextCapital.coerceAtLeast(0.0)
                        tradeCount++
                        completedTrades.add(0, activeTrade.copy(exitPrice = exit, profitRatio = realizedRatio, won = won, exitTime = candle.timestamp))
                        if (completedTrades.size > MAX_RECENT_TRADES) completedTrades.removeAt(completedTrades.lastIndex)
                        totalClosedTrades++
                        if (won) totalWins++ else totalLosses++
                        history.add(CapitalPoint(time = timeFormat.format(Date()), capital = capital))
                        if (history.size > MAX_EQUITY_POINTS) history.removeAt(0)
                        openTrade = null
                        if (state.simMode == "single" || (state.simMode == "limited" && tradeCount >= state.maxTrades) || capital <= 0.0) {
                            shouldStop = true
                        }
                    }
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
                    totalClosedTrades = totalClosedTrades,
                    totalWins = totalWins,
                    totalLosses = totalLosses,
                    isRunning = if (shouldStop) false else it.isRunning,
                    userNotice = arithmeticStopNotice ?: if (shouldStop) "Đã hoàn thành phiên giả lập ($tradeCount lệnh)." else it.userNotice
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
        val effectiveGeminiKey = state.geminiApiKey.trim()

        if (effectiveGeminiKey.isBlank()) {
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

        if (_uiState.value.isAnalyzing) return
        analysisJob = viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, aiAnalysis = null, userNotice = null) }
            try {
                val totalTrades = state.totalClosedTrades
                val wins = state.totalWins
                val losses = state.totalLosses
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        userNotice = "Lỗi khi gọi Gemini AI: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            } finally {
                _uiState.update { if (it.isAnalyzing) it.copy(isAnalyzing = false) else it }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
        calibrationJob?.cancel()
        analysisJob?.cancel()
    }
}
