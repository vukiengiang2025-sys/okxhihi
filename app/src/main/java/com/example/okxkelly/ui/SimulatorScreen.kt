package com.example.okxkelly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.okxkelly.ui.components.EquityChart
import com.example.okxkelly.ui.components.SettingsDialog
import com.example.okxkelly.ui.components.TradeLogItem
import com.example.okxkelly.ui.theme.BgDark
import com.example.okxkelly.ui.theme.BorderDark
import com.example.okxkelly.ui.theme.LimeAccent
import com.example.okxkelly.ui.theme.LossRed
import com.example.okxkelly.ui.theme.SurfaceDark
import com.example.okxkelly.ui.theme.SurfaceVariantDark
import com.example.okxkelly.ui.theme.TextMuted
import com.example.okxkelly.ui.theme.TextPrimary
import com.example.okxkelly.ui.theme.TextSecondary
import com.example.okxkelly.ui.theme.WinGreen
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulatorScreen(
    viewModel: SimulatorViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedBottomTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BgDark,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Section
            item {
                HeaderSection(
                    isRunning = state.isRunning,
                    isCalibrating = state.isCalibrating,
                    onOpenSettings = { viewModel.setShowSettings(true) },
                    onAutoCalibrate = { viewModel.autoCalibrate() },
                    onStartSimulation = { viewModel.startSimulation() },
                    onStopSimulation = { viewModel.stopSimulation() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // User Notice Banner (Toast / alert equivalent)
            item {
                AnimatedVisibility(
                    visible = state.userNotice != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    state.userNotice?.let { notice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
                                .border(1.dp, LimeAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = LimeAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = notice,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearNotice() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Đóng",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Parameters Card
            item {
                ParametersCard(
                    state = state,
                    onInstIdChange = { viewModel.updateInstId(it) },
                    onBarChange = { viewModel.updateBar(it) },
                    onCapitalChange = { viewModel.updateStartCapital(it) },
                    onTpChange = { viewModel.updateTpPercent(it) },
                    onSlChange = { viewModel.updateSlPercent(it) },
                    onKellyMultiplierChange = { viewModel.updateKellyMultiplier(it) },
                    onSimModeChange = { viewModel.updateSimMode(it) },
                    onMaxTradesChange = { viewModel.updateMaxTrades(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Kelly Metrics Stats Row
            item {
                KellyMetricsRow(
                    metrics = state.metrics,
                    startCapital = state.startCapital,
                    currentCapital = state.currentCapital
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Interactive Equity Chart
            item {
                EquityChart(
                    history = state.capitalHistory,
                    currentCapital = state.currentCapital,
                    startCapital = state.startCapital,
                    modifier = Modifier.height(300.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Section Tabs: Trade Log vs Gemini AI Evaluation
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedBottomTab,
                    containerColor = SurfaceDark,
                    contentColor = LimeAccent,
                    edgePadding = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                ) {
                    Tab(
                        selected = selectedBottomTab == 0,
                        onClick = { selectedBottomTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "NHẬT KÝ LỆNH",
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontSize = 12.sp,
                                    color = if (selectedBottomTab == 0) LimeAccent else TextSecondary
                                )
                                if (state.openTrade != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(LimeAccent, CircleShape)
                                    )
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedBottomTab == 1,
                        onClick = { selectedBottomTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (selectedBottomTab == 1) LimeAccent else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "GEMINI AI EVALUATION",
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontSize = 12.sp,
                                    color = if (selectedBottomTab == 1) LimeAccent else TextSecondary
                                )
                            }
                        }
                    )
                }
            }

            if (selectedBottomTab == 0) {
                // Trade Log Tab
                item {
                    TradeLogTabContent(
                        openTrade = state.openTrade,
                        liveTrades = state.liveTrades
                    )
                }
            } else {
                // Gemini AI Evaluation Tab
                item {
                    AiEvaluationTabContent(
                        isAnalyzing = state.isAnalyzing,
                        analysisText = state.aiAnalysis,
                        hasTrades = state.liveTrades.isNotEmpty(),
                        onRunAnalysis = { viewModel.runAIAnalysis() }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (state.showSettings) {
        SettingsDialog(
            initialOkxApiKey = state.okxApiKey,
            initialOkxSecret = state.okxSecret,
            initialOkxPassphrase = state.okxPassphrase,
            initialGeminiApiKey = state.geminiApiKey,
            onDismiss = { viewModel.setShowSettings(false) },
            onSave = { okxKey, okxSec, okxPass, geminiKey ->
                viewModel.saveSettings(okxKey, okxSec, okxPass, geminiKey)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderSection(
    isRunning: Boolean,
    isCalibrating: Boolean,
    onOpenSettings: () -> Unit,
    onAutoCalibrate: () -> Unit,
    onStartSimulation: () -> Unit,
    onStopSimulation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "QUANTITATIVE SIMULATOR v2.4",
                    color = TextMuted,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Kelly ",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic
                    )
                    Text(
                        text = "Quant",
                        color = LimeAccent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(44.dp)
                    .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Cài đặt API",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onAutoCalibrate,
                enabled = !isRunning && !isCalibrating,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("backtest_button")
            ) {
                if (isCalibrating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = LimeAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Đang tính...", fontSize = 11.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = LimeAccent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Đánh giá (Backtest)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!isRunning) {
                Button(
                    onClick = onStartSimulation,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeAccent,
                        contentColor = Color(0xFF000000)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("start_sim_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bắt đầu", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            } else {
                Button(
                    onClick = onStopSimulation,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LossRed,
                        contentColor = Color(0xFFFFFFFF)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("stop_sim_button")
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dừng Giả Lập", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ParametersCard(
    state: SimulatorUiState,
    onInstIdChange: (String) -> Unit,
    onBarChange: (String) -> Unit,
    onCapitalChange: (Double) -> Unit,
    onTpChange: (String) -> Unit,
    onSlChange: (String) -> Unit,
    onKellyMultiplierChange: (Double) -> Unit,
    onSimModeChange: (String) -> Unit,
    onMaxTradesChange: (Int) -> Unit
) {
    var expandedBar by remember { mutableStateOf(false) }
    var expandedMode by remember { mutableStateOf(false) }
    var expandedKelly by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "THAM SỐ CHIẾN LƯỢC",
            color = TextMuted,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Cặp & Khung thời gian
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.instId,
                onValueChange = onInstIdChange,
                label = { Text("Cặp Giao Dịch", fontSize = 11.sp) },
                enabled = !state.isRunning,
                singleLine = true,
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("input_inst_id"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeAccent,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(0.8f)) {
                OutlinedTextField(
                    value = state.bar,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Khung", fontSize = 11.sp) },
                    enabled = !state.isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.isRunning) { expandedBar = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LimeAccent,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                DropdownMenu(
                    expanded = expandedBar,
                    onDismissRequest = { expandedBar = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    listOf("1m", "5m", "15m", "1H").forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b, color = TextPrimary) },
                            onClick = {
                                onBarChange(b)
                                expandedBar = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Vốn ban đầu (USD)
        OutlinedTextField(
            value = if (state.startCapital > 0) state.startCapital.toInt().toString() else "",
            onValueChange = { str -> onCapitalChange(str.toDoubleOrNull() ?: 0.0) },
            label = { Text("Vốn Ban Đầu (A - USD)", fontSize = 11.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !state.isRunning,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_start_capital"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LimeAccent,
                unfocusedBorderColor = BorderDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // TP & SL
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.tpPercentStr,
                onValueChange = onTpChange,
                label = { Text("Take Profit (%)", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !state.isRunning,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_tp"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeAccent,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedTextField(
                value = state.slPercentStr,
                onValueChange = onSlChange,
                label = { Text("Stop Loss (%)", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !state.isRunning,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_sl"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeAccent,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chế độ chạy & N lệnh
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1.2f)) {
                val modeLabel = when (state.simMode) {
                    "single" -> "1 Lệnh"
                    "limited" -> "N Lệnh"
                    else -> "Liên Tục"
                }
                OutlinedTextField(
                    value = modeLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Chế Độ Chạy", fontSize = 11.sp) },
                    enabled = !state.isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.isRunning) { expandedMode = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LimeAccent,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                DropdownMenu(
                    expanded = expandedMode,
                    onDismissRequest = { expandedMode = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    listOf("continuous" to "Liên Tục", "single" to "1 Lệnh", "limited" to "N Lệnh").forEach { (m, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = TextPrimary) },
                            onClick = {
                                onSimModeChange(m)
                                expandedMode = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            OutlinedTextField(
                value = state.maxTrades.toString(),
                onValueChange = { onMaxTradesChange(it.toIntOrNull() ?: 1) },
                label = { Text("Số Lệnh (N)", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.isRunning && state.simMode == "limited",
                singleLine = true,
                modifier = Modifier
                    .weight(0.8f)
                    .testTag("input_max_trades"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeAccent,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mức độ Kelly Multiplier
        Box(modifier = Modifier.fillMaxWidth()) {
            val multLabel = when (state.kellyMultiplier) {
                0.25 -> "0.25x (Rất An Toàn)"
                1.0 -> "1.0x (Full Kelly - Rủi Ro)"
                else -> "0.5x (Half Kelly - Tốt Nhất)"
            }
            OutlinedTextField(
                value = multLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Mức Độ Kelly (Multiplier)", fontSize = 11.sp) },
                enabled = !state.isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isRunning) { expandedKelly = true },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LimeAccent,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            DropdownMenu(
                expanded = expandedKelly,
                onDismissRequest = { expandedKelly = false },
                modifier = Modifier.background(SurfaceDark)
            ) {
                listOf(
                    0.25 to "0.25x (Rất An Toàn)",
                    0.5 to "0.5x (Half Kelly - Tốt Nhất)",
                    1.0 to "1.0x (Full Kelly - Rủi Ro)"
                ).forEach { (v, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = TextPrimary) },
                        onClick = {
                            onKellyMultiplierChange(v)
                            expandedKelly = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KellyMetricsRow(
    metrics: com.example.okxkelly.data.model.SimMetrics?,
    startCapital: Double,
    currentCapital: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "WIN RATE", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (metrics != null) String.format(Locale.US, "%.1f%%", metrics.winRate * 100) else "--",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = "R/R (b)", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (metrics != null) String.format(Locale.US, "%.2f", metrics.b) else "--",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(modifier = Modifier.weight(1.2f)) {
            Text(text = "KELLY (f*)", color = LimeAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (metrics != null) String.format(Locale.US, "%.2f%%", metrics.kellyFraction * 100) else "--",
                color = LimeAccent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        val pnl = currentCapital - startCapital
        Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
            Text(text = "LÃI / LỖ", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(Locale.US, "%s$%.2f", if (pnl >= 0) "+" else "", pnl),
                color = if (pnl >= 0) WinGreen else LossRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun TradeLogTabContent(
    openTrade: com.example.okxkelly.data.model.Trade?,
    liveTrades: List<com.example.okxkelly.data.model.Trade>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .background(SurfaceDark, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .padding(16.dp)
    ) {
        if (openTrade != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LimeAccent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .border(1.dp, LimeAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(LimeAccent, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "Đang mở lệnh BUY @ $%.2f", openTrade.entryPrice),
                    color = LimeAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (liveTrades.isEmpty() && openTrade == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CHƯA CÓ LỆNH NÀO ĐƯỢC THỰC HIỆN",
                    color = TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                liveTrades.forEach { trade ->
                    TradeLogItem(trade = trade)
                }
            }
        }
    }
}

@Composable
private fun AiEvaluationTabContent(
    isAnalyzing: Boolean,
    analysisText: String?,
    hasTrades: Boolean,
    onRunAnalysis: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .background(SurfaceDark, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GEMINI AI ANALYSIS",
                color = LimeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Button(
                onClick = onRunAnalysis,
                enabled = !isAnalyzing && hasTrades,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LimeAccent,
                    contentColor = Color(0xFF000000),
                    disabledContainerColor = SurfaceVariantDark,
                    disabledContentColor = TextMuted
                ),
                modifier = Modifier.testTag("ai_analyze_button")
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Đang phân tích...", fontSize = 11.sp)
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Phân tích Kết quả", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = BorderDark)
        Spacer(modifier = Modifier.height(14.dp))

        if (analysisText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Khởi chạy giả lập để có dữ liệu giao dịch, sau đó nhấn 'Phân tích Kết quả' để nhận đánh giá định lượng chuyên sâu từ Gemini AI.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            Surface(
                color = SurfaceVariantDark.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
            ) {
                Text(
                    text = analysisText,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
