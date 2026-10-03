import React, { useState, useEffect, useRef } from 'react';
import { fetchCandles, fetchHistoryCandles, Candle } from '../services/okx';
import { backtestStrategy, SimMetrics, Trade, calculateRSI } from '../lib/strategy';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer } from 'recharts';
import { Play, Square, Settings, RefreshCw, TrendingUp, X, Sparkles, BrainCircuit } from 'lucide-react';
import { format } from 'date-fns';
import { GoogleGenAI } from '@google/genai';

export default function Simulator() {
  const [instId, setInstId] = useState('BTC-USDT');
  const [startCapital, setStartCapital] = useState(1000);
  const [tpPercentStr, setTpPercentStr] = useState('2');
  const [slPercentStr, setSlPercentStr] = useState('1');
  const [kellyMultiplier, setKellyMultiplier] = useState(0.5); // Fractional Kelly
  const [bar, setBar] = useState('1m');

  const [simMode, setSimMode] = useState<'continuous'|'single'|'limited'>('continuous');
  const [maxTrades, setMaxTrades] = useState(5);
  const tradeCountRef = useRef(0);
  const simModeRef = useRef(simMode);
  const maxTradesRef = useRef(maxTrades);

  const [showSettings, setShowSettings] = useState(false);
  const [apiKey, setApiKey] = useState(() => localStorage.getItem('okx_api_key') || '');
  const [apiSecret, setApiSecret] = useState(() => localStorage.getItem('okx_api_secret') || '');
  const [apiPassphrase, setApiPassphrase] = useState(() => localStorage.getItem('okx_api_passphrase') || '');
  const [geminiKey, setGeminiKey] = useState(() => localStorage.getItem('gemini_api_key') || '');
  
  const [aiAnalysis, setAiAnalysis] = useState<string | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);

  const [metrics, setMetrics] = useState<SimMetrics | null>(null);
  
  // Forward Simulator State
  const [isRunning, setIsRunning] = useState(false);
  const [currentCapital, setCurrentCapital] = useState(1000);
  const [historyCapital, setHistoryCapital] = useState<{time: string, capital: number}[]>([]);
  const [liveTrades, setLiveTrades] = useState<Trade[]>([]);
  const [liveCandles, setLiveCandles] = useState<Candle[]>([]);
  
  // Refs for tracking mutable loop state
  const openTradeRef = useRef<Trade | null>(null);
  const loopRef = useRef<any>(null);

  const saveSettings = () => {
    localStorage.setItem('okx_api_key', apiKey);
    localStorage.setItem('okx_api_secret', apiSecret);
    localStorage.setItem('okx_api_passphrase', apiPassphrase);
    localStorage.setItem('gemini_api_key', geminiKey);
    setShowSettings(false);
  };

  // Auto-Calibrate (Autonomous Improvement Feature)
  const autoCalibrate = async () => {
    try {
      // Fetch some historical data to backtest
      const hist = await fetchHistoryCandles(instId, bar, 100);
      if (hist.length < 50) {
        alert('Không đủ dữ liệu lịch sử để đánh giá');
        return;
      }
      
      const tp = parseFloat(tpPercentStr) / 100;
      const sl = parseFloat(slPercentStr) / 100;
      
      const results = backtestStrategy(hist, tp, sl);
      setMetrics(results);
    } catch (e) {
      console.error(e);
      alert('Lỗi khi lấy dữ liệu OKX');
    }
  };

  const startSimulation = () => {
    if (!metrics || metrics.kellyFraction <= 0) {
      alert('Vui lòng Đánh giá chiến lược và đảm bảo Kelly > 0 trước khi chạy giả lập thực tế.');
      return;
    }
    tradeCountRef.current = 0;
    setCurrentCapital(startCapital);
    setHistoryCapital([{ time: format(new Date(), 'HH:mm:ss'), capital: startCapital }]);
    setLiveTrades([]);
    openTradeRef.current = null;
    setIsRunning(true);
  };

  const stopSimulation = () => {
    setIsRunning(false);
    if (loopRef.current) clearInterval(loopRef.current);
  };

  const processLatestCandle = async () => {
    try {
      const candles = await fetchCandles(instId, bar, 50); // Get latest 50 to compute RSI
      if (candles.length < 15) return;
      
      setLiveCandles(candles);
      
      const latestCandle = candles[candles.length - 1];
      const rsi = calculateRSI(candles, 14);
      const currentRsi = rsi[rsi.length - 1];
      const prevRsi = rsi[rsi.length - 2];
      
      const tp = parseFloat(tpPercentStr) / 100;
      const sl = parseFloat(slPercentStr) / 100;
      const betSize = currentCapital * metrics!.kellyFraction * kellyMultiplier;
      
      if (openTradeRef.current) {
        let trade = openTradeRef.current;
        const highestProfit = (latestCandle.high - trade.entryPrice) / trade.entryPrice;
        const lowestDrop = (latestCandle.low - trade.entryPrice) / trade.entryPrice;
        
        let closed = false;
        if (lowestDrop <= -sl) {
          trade.exitPrice = trade.entryPrice * (1 - sl);
          trade.profitRatio = -sl;
          trade.won = false;
          trade.exitTime = Date.now();
          closed = true;
        } else if (highestProfit >= tp) {
          trade.exitPrice = trade.entryPrice * (1 + tp);
          trade.profitRatio = tp;
          trade.won = true;
          trade.exitTime = Date.now();
          closed = true;
        }
        
        if (closed) {
          tradeCountRef.current += 1;
          const pnl = trade.won ? betSize * tp : -betSize * sl;
          const newCapital = currentCapital + pnl;
          setCurrentCapital(newCapital);
          setHistoryCapital(prev => [...prev, { time: format(new Date(), 'HH:mm:ss'), capital: newCapital }]);
          setLiveTrades(prev => [trade, ...prev]);
          openTradeRef.current = null;
          
          if (simModeRef.current === 'single' || (simModeRef.current === 'limited' && tradeCountRef.current >= maxTradesRef.current)) {
            setIsRunning(false);
          }
        }
      } else {
        // RSI < 30 cross up -> BUY
        if (prevRsi < 30 && currentRsi >= 30) {
          openTradeRef.current = {
            type: 'BUY',
            entryPrice: latestCandle.close,
            entryTime: Date.now()
          };
        }
      }
    } catch (e) {
      console.error(e);
    }
  };

  const runAIAnalysis = async () => {
    if (!geminiKey) {
      alert("Vui lòng nhập Gemini API Key trong Cài đặt");
      setShowSettings(true);
      return;
    }
    if (liveTrades.length === 0) {
      alert("Cần có dữ liệu giao dịch để AI đánh giá. Hãy chạy giả lập trước.");
      return;
    }
    
    setIsAnalyzing(true);
    setAiAnalysis(null);
    try {
      const ai = new GoogleGenAI({ apiKey: geminiKey });
      
      const payload = {
        capitalStart: startCapital,
        capitalEnd: currentCapital,
        pnl: currentCapital - startCapital,
        totalTrades: liveTrades.length,
        wins: liveTrades.filter(t => t.won).length,
        losses: liveTrades.filter(t => !t.won).length,
        winRate: liveTrades.length > 0 ? (liveTrades.filter(t => t.won).length / liveTrades.length) * 100 : 0,
        kellyMultiplierUsed: kellyMultiplier,
      };

      const prompt = `Bạn là chuyên gia phân tích định lượng (Quantitative Analyst). Hãy đánh giá kết quả của chiến lược giao dịch tự động dựa trên công thức Kelly:
      
Thông số giả lập:
- Vốn ban đầu: $${payload.capitalStart}
- Vốn hiện tại: $${payload.capitalEnd.toFixed(2)}
- Tổng số lệnh: ${payload.totalTrades}
- Lệnh Thắng / Thua: ${payload.wins} / ${payload.losses}
- Tỷ lệ Thắng (Win Rate): ${payload.winRate.toFixed(2)}%
- Hệ số Kelly đã dùng: ${payload.kellyMultiplierUsed}x

Yêu cầu:
1. Đánh giá mức độ hiệu quả của phương pháp Kelly trong đợt giả lập này. (Công thức có phát huy tác dụng bảo vệ vốn / tối ưu lợi nhuận không?)
2. Nhận xét về Tỷ lệ Thắng và Mức Rủi Ro.
3. Đề xuất cải tiến (ví dụ: nên đổi khung thời gian, hay giảm/tăng hệ số Kelly, điều chỉnh Take Profit / Stop Loss).
Trả lời ngắn gọn, súc tích, mang tính chuyên môn cao, chia làm 3 đoạn văn nhỏ tương ứng 3 yêu cầu, viết bằng tiếng Việt.`;

      const response = await ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents: prompt
      });

      setAiAnalysis(response.text);
    } catch (error: any) {
      console.error(error);
      alert("Lỗi khi kết nối Gemini API: " + error.message);
    } finally {
      setIsAnalyzing(false);
    }
  };

  useEffect(() => {
    simModeRef.current = simMode;
    maxTradesRef.current = maxTrades;
  }, [simMode, maxTrades]);

  useEffect(() => {
    if (isRunning) {
      // In real scenarios, bar could be 1m, interval 5-10s.
      // For simulator speed, we'll poll every 3 seconds
      loopRef.current = setInterval(processLatestCandle, 3000);
    } else {
      if (loopRef.current) clearInterval(loopRef.current);
    }
    return () => {
      if (loopRef.current) clearInterval(loopRef.current);
    }
  }, [isRunning, currentCapital, metrics]);

  return (
    <div className="min-h-screen bg-[#0A0A0B] text-white p-6 md:p-8 font-sans border-8 border-[#1A1A1C] flex flex-col">
      <div className="max-w-[1400px] mx-auto w-full flex-1 space-y-8">
        
        <header className="flex justify-between items-end pb-8 border-b border-white/10">
          <div className="flex flex-col">
            <span className="text-[10px] uppercase tracking-[0.3em] text-white/50 mb-1">Quantitative Simulator v2.4</span>
            <h1 className="text-5xl font-serif italic tracking-tighter leading-none">
              Kelly <span className="text-[#D4FF00]">Quant</span>
            </h1>
          </div>
          <div className="flex gap-4">
             <button onClick={() => setShowSettings(true)} className="px-4 py-3 border border-white/10 text-white/60 hover:bg-white/5 hover:text-white rounded-none text-[10px] uppercase tracking-widest flex items-center gap-2 transition-colors">
               <Settings size={14} /> Cài đặt API
             </button>
             <button onClick={autoCalibrate} className="px-4 py-3 border border-white/10 text-white/60 hover:bg-white/5 hover:text-white rounded-none text-[10px] uppercase tracking-widest flex items-center gap-2 transition-colors">
               <RefreshCw size={14} /> Đánh giá (Backtest)
             </button>
             {!isRunning ? (
               <button onClick={startSimulation} className="px-6 py-3 bg-[#D4FF00] text-black rounded-none text-[10px] font-bold uppercase tracking-widest flex items-center gap-2 hover:bg-[#c2e600] transition-colors">
                 <Play size={14} /> Bắt đầu Giả lập
               </button>
             ) : (
               <button onClick={stopSimulation} className="px-6 py-3 bg-white/10 text-white rounded-none text-[10px] font-bold uppercase tracking-widest flex items-center gap-2 hover:bg-white/20 transition-colors">
                 <Square size={14} /> Dừng
               </button>
             )}
          </div>
        </header>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          
          {/* Cấu hình */}
          <div className="lg:col-span-3 bg-white/[0.02] p-8 border border-white/5 flex flex-col">
            <h2 className="text-[11px] uppercase tracking-[0.2em] text-white/30 mb-8">Tham số</h2>
            
            <div className="space-y-6">
              <div>
                <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">Cặp Giao Dịch (OKX)</label>
                <input disabled={isRunning} value={instId} onChange={e => setInstId(e.target.value)} className="w-full bg-white/5 border border-white/10 rounded-none px-3 py-2 text-sm font-mono text-white focus:outline-none focus:border-[#D4FF00] transition-colors" />
              </div>
              <div>
                <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">Khung Thời Gian</label>
                <select disabled={isRunning} value={bar} onChange={e => setBar(e.target.value)} className="w-full bg-white/5 border border-white/10 rounded-none px-3 py-2 text-sm text-white focus:outline-none focus:border-[#D4FF00] transition-colors">
                  <option value="1m" className="bg-[#0A0A0B]">1 Phút (1m)</option>
                  <option value="5m" className="bg-[#0A0A0B]">5 Phút (5m)</option>
                  <option value="15m" className="bg-[#0A0A0B]">15 Phút (15m)</option>
                </select>
              </div>
              <div>
                <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">Vốn Ban Đầu (A - USD)</label>
                <input disabled={isRunning} type="number" value={startCapital} onChange={e => setStartCapital(Number(e.target.value))} className="w-full bg-white/5 border border-white/10 rounded-none px-3 py-2 text-sm font-mono text-white focus:outline-none focus:border-[#D4FF00] transition-colors" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">Take Profit (%)</label>
                  <input disabled={isRunning} type="number" value={tpPercentStr} onChange={e => setTpPercentStr(e.target.value)} className="w-full bg-white/5 border border-white/10 rounded-none px-3 py-2 text-sm font-mono text-white focus:outline-none focus:border-[#D4FF00] transition-colors" />
                </div>
                <div>
                  <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">Stop Loss (%)</label>
                  <input disabled={isRunning} type="number" value={slPercentStr} onChange={e => setSlPercentStr(e.target.value)} className="w-full bg-white/5 border border-white/10 rounded-none px-3 py-2 text-sm font-mono text-white focus:outline-none focus:border-[#D4FF00] transition-colors" />
                </div>
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">Chế Độ Chạy</label>
                  <select disabled={isRunning} value={simMode} onChange={e => setSimMode(e.target.value as any)} className="w-full bg-white/5 border border-white/10 rounded-none px-3 py-2 text-sm text-white focus:outline-none focus:border-[#D4FF00] transition-colors">
                    <option value="continuous" className="bg-[#0A0A0B]">Liên Tục</option>
                    <option value="single" className="bg-[#0A0A0B]">1 Lệnh</option>
                    <option value="limited" className="bg-[#0A0A0B]">N Lệnh</option>
                  </select>
                </div>
                <div>
                  <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">Số Lệnh (N)</label>
                  <input disabled={isRunning || simMode !== 'limited'} type="number" value={maxTrades} onChange={e => setMaxTrades(Number(e.target.value))} className="w-full bg-white/5 border border-white/10 rounded-none px-3 py-2 text-sm font-mono text-white focus:outline-none focus:border-[#D4FF00] transition-colors disabled:opacity-30" />
                </div>
              </div>
              
              <div>
                <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">Mức Độ Kelly (Multiplier)</label>
                <select disabled={isRunning} value={kellyMultiplier} onChange={e => setKellyMultiplier(Number(e.target.value))} className="w-full bg-white/5 border border-white/10 rounded-none px-3 py-2 text-sm text-white focus:outline-none focus:border-[#D4FF00] transition-colors">
                  <option value={0.25} className="bg-[#0A0A0B]">0.25x (Rất An Toàn)</option>
                  <option value={0.5} className="bg-[#0A0A0B]">0.5x (Half Kelly - Tốt Nhất)</option>
                  <option value={1} className="bg-[#0A0A0B]">1.0x (Full Kelly - Rủi Ro)</option>
                </select>
              </div>

            </div>
          </div>

          <div className="lg:col-span-9 space-y-8 flex flex-col">
            
            {/* Kelly Stats */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-8 pb-4 border-b border-white/10">
              <div>
                <span className="text-[10px] uppercase text-white/40 block mb-2">Win Rate (Quá Khứ)</span>
                <span className="text-3xl font-serif italic text-white">
                  {metrics ? `${(metrics.winRate * 100).toFixed(1)}%` : '--'}
                </span>
              </div>
              <div>
                <span className="text-[10px] uppercase text-white/40 block mb-2">Tỷ Lệ R/R (b)</span>
                <span className="text-3xl font-serif italic text-white">
                  {metrics ? metrics.b.toFixed(2) : '--'}
                </span>
              </div>
              <div>
                <span className="text-[10px] uppercase text-[#D4FF00] font-bold tracking-widest block mb-2">Phân bổ Kelly (f*)</span>
                <span className="text-4xl font-serif italic text-[#D4FF00] leading-none block mt-1">
                  {metrics ? `${(metrics.kellyFraction * 100).toFixed(2)}%` : '--'}
                </span>
              </div>
              <div>
                <span className="text-[10px] uppercase text-white/40 block mb-2">Lãi / Lỗ Hiện Tại</span>
                <span className={`text-4xl font-light tracking-tighter block mt-1 ${currentCapital >= startCapital ? 'text-emerald-400' : 'text-rose-400'}`}>
                  ${(currentCapital - startCapital).toFixed(2)}
                </span>
              </div>
            </div>

            {/* Chart */}
            <div className="bg-[#0E0E0F] p-8 border border-white/5 h-[350px] flex flex-col">
               <div className="flex justify-between items-start mb-6">
                 <div>
                   <h3 className="text-5xl font-light leading-none mb-2 font-mono">${currentCapital.toFixed(2)}</h3>
                   <div className="flex gap-4 items-center">
                     <span className="text-[10px] uppercase tracking-[0.2em] text-white/40">Current Simulated Equity (A)</span>
                   </div>
                 </div>
               </div>
               
               <div className="flex-1 min-h-0">
                 {historyCapital.length > 0 ? (
                   <ResponsiveContainer width="100%" height="100%">
                     <LineChart data={historyCapital}>
                       <CartesianGrid strokeDasharray="3 3" stroke="#ffffff10" />
                       <XAxis dataKey="time" stroke="#ffffff40" fontSize={10} tickMargin={10} />
                       <YAxis domain={['auto', 'auto']} stroke="#ffffff40" fontSize={10} tickFormatter={(val) => `$${val}`} width={60} />
                       <RechartsTooltip 
                         contentStyle={{backgroundColor: '#0E0E0F', borderColor: '#ffffff10', borderRadius: '0px', color: '#fff'}}
                         itemStyle={{color: '#D4FF00', fontWeight: 600, fontFamily: 'monospace'}}
                       />
                       <Line type="monotone" dataKey="capital" stroke="#D4FF00" strokeWidth={2} dot={{r: 0}} activeDot={{r: 4, fill: "#D4FF00"}} />
                     </LineChart>
                   </ResponsiveContainer>
                 ) : (
                   <div className="w-full h-full flex items-center justify-center text-white/30 text-xs uppercase tracking-widest font-mono">
                     Waiting for Simulation Data...
                   </div>
                 )}
               </div>
            </div>

            {/* Trade Logs & AI */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8 flex-1 min-h-[300px]">
              <div className="bg-[#0E0E0F] border border-white/5 overflow-hidden flex flex-col h-full">
                 <div className="px-8 py-5 border-b border-white/5 flex justify-between items-center">
                   <h3 className="text-[11px] uppercase tracking-[0.2em] text-white/30">Nhật Ký Giao Dịch</h3>
                   {openTradeRef.current && (
                     <span className="text-[10px] uppercase tracking-widest text-[#D4FF00] flex items-center gap-2">
                       <div className="w-2 h-2 bg-[#D4FF00] rounded-full animate-pulse"></div>
                       Đang mở Lệnh BUY @ {openTradeRef.current.entryPrice.toFixed(2)}
                     </span>
                   )}
                 </div>
                 <div className="overflow-y-auto flex-1">
                    {liveTrades.length === 0 ? (
                      <div className="p-8 justify-center flex text-white/30 text-[10px] uppercase tracking-widest font-mono">No Trades Executed Yet</div>
                    ) : (
                      <table className="w-full text-left text-sm text-white/80">
                        <thead className="text-[10px] uppercase tracking-[0.2em] text-white/40 bg-white/5 sticky top-0">
                          <tr>
                            <th className="px-5 py-4 font-normal">Loại</th>
                            <th className="px-5 py-4 font-normal">Vào</th>
                            <th className="px-5 py-4 font-normal">Ra</th>
                            <th className="px-5 py-4 font-normal">KQ</th>
                            <th className="px-5 py-4 font-normal text-right">TT</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-white/5">
                          {liveTrades.map((t, idx) => (
                            <tr key={idx} className="hover:bg-white/[0.02] transition-colors text-xs">
                              <td className="px-5 py-3 font-semibold text-white">{t.type}</td>
                              <td className="px-5 py-3 font-mono opacity-60">{t.entryPrice.toFixed(2)}</td>
                              <td className="px-5 py-3 font-mono opacity-60">{t.exitPrice?.toFixed(2)}</td>
                              <td className={`px-5 py-3 font-mono ${t.won ? 'text-emerald-400' : 'text-rose-400'}`}>
                                {((t.profitRatio || 0) * 100).toFixed(1)}%
                              </td>
                              <td className="px-5 py-3 text-right">
                                 {t.won ? (
                                   <span className="text-[10px] text-emerald-400 font-bold">W</span>
                                 ) : (
                                   <span className="text-[10px] text-rose-400 font-bold">L</span>
                                 )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                 </div>
              </div>

              {/* AI Analysis Panel */}
              <div className="bg-[#0E0E0F] border border-white/5 p-6 md:p-8 flex flex-col h-full overflow-hidden">
                 <div className="flex justify-between items-center mb-6">
                   <h3 className="text-[11px] uppercase tracking-[0.2em] text-[#D4FF00] flex items-center gap-2">
                     <BrainCircuit size={16} /> Gemini AI Evaluation
                   </h3>
                   <button onClick={runAIAnalysis} disabled={isAnalyzing || liveTrades.length === 0} className="px-4 py-2 border border-[#D4FF00]/30 text-[#D4FF00] hover:bg-[#D4FF00] hover:text-black transition-colors rounded-none text-[10px] uppercase tracking-widest disabled:opacity-30 disabled:cursor-not-allowed flex items-center gap-2">
                     {isAnalyzing ? <RefreshCw size={14} className="animate-spin" /> : <Sparkles size={14} />} 
                     {isAnalyzing ? 'Đang phân tích...' : 'Phân tích Kết quả'}
                   </button>
                 </div>
                 
                 <div className="flex-1 min-h-0 overflow-y-auto">
                   {!aiAnalysis ? (
                     <div className="h-full flex items-center justify-center text-white/30 text-[10px] uppercase tracking-widest font-mono text-center px-8">
                       Chạy phân tích sau khi dừng giả lập để đánh giá hiệu quả chiến lược Kelly.
                     </div>
                   ) : (
                     <div className="space-y-4 text-sm text-white/70 leading-relaxed font-serif whitespace-pre-line">
                       {aiAnalysis}
                     </div>
                   )}
                 </div>
              </div>
            </div>

          </div>
        </div>
      </div>

      {showSettings && (
        <div className="fixed inset-0 bg-black/80 flex items-center justify-center p-4 z-50 backdrop-blur-sm">
          <div className="bg-[#1A1A1C] border border-white/10 p-8 max-w-md w-full shadow-2xl relative">
            <button onClick={() => setShowSettings(false)} className="absolute top-4 right-4 text-white/50 hover:text-white transition-colors">
              <X size={20} />
            </button>
            <h2 className="text-xl font-serif italic mb-6 text-white tracking-tight">API Configuration</h2>
            
            <div className="space-y-5">
              <div>
                <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">OKX API Key</label>
                <input type="password" value={apiKey} onChange={e => setApiKey(e.target.value)} placeholder="Tùy chọn (Public Data nếu trống)" className="w-full bg-white/5 border border-white/10 rounded-none px-4 py-3 text-sm font-mono text-white focus:outline-none focus:border-[#D4FF00] transition-colors placeholder:text-white/20" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">OKX Secret</label>
                  <input type="password" value={apiSecret} onChange={e => setApiSecret(e.target.value)} placeholder="***" className="w-full bg-white/5 border border-white/10 rounded-none px-4 py-3 text-sm font-mono text-white focus:outline-none focus:border-[#D4FF00] transition-colors placeholder:text-white/20" />
                </div>
                <div>
                  <label className="text-[10px] uppercase tracking-widest text-white/40 block mb-2">OKX Passphrase</label>
                  <input type="password" value={apiPassphrase} onChange={e => setApiPassphrase(e.target.value)} placeholder="***" className="w-full bg-white/5 border border-white/10 rounded-none px-4 py-3 text-sm font-mono text-white focus:outline-none focus:border-[#D4FF00] transition-colors placeholder:text-white/20" />
                </div>
              </div>
              <hr className="border-white/5 my-2" />
              <div>
                <label className="text-[10px] uppercase tracking-widest text-[#D4FF00]/60 block mb-2">Gemini API Key</label>
                <input type="password" value={geminiKey} onChange={e => setGeminiKey(e.target.value)} placeholder="Bắt buộc để chạy AI Evaluation" className="w-full bg-white/5 border border-white/10 rounded-none px-4 py-3 text-sm font-mono text-white focus:outline-none focus:border-[#D4FF00] transition-colors placeholder:text-white/20" />
              </div>
            </div>
            
            <div className="mt-8 flex justify-end gap-3">
              <button onClick={() => setShowSettings(false)} className="px-6 py-3 border border-white/10 text-white/60 hover:text-white transition-colors text-[10px] uppercase font-bold tracking-widest">
                Hủy
              </button>
              <button onClick={saveSettings} className="px-6 py-3 bg-[#D4FF00] text-black hover:bg-[#c2e600] transition-colors text-[10px] uppercase font-bold tracking-widest">
                Lưu Cài Đặt
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
