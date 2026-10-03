import { Candle } from '../services/okx';

export interface Trade {
  type: 'BUY' | 'SELL'; // actually just simulating LONGs for simplicity
  entryPrice: number;
  exitPrice?: number;
  entryTime: number;
  exitTime?: number;
  profitRatio?: number; // e.g. 0.03 for 3% profit
  won?: boolean;
}

export interface SimMetrics {
  totalTrades: number;
  wins: number;
  losses: number;
  winRate: number;
  averageWinRatio: number; // Avg % won
  averageLossRatio: number; // Avg % lost
  b: number; // Reward to Risk ratio
  kellyFraction: number; // Suggested Kelly fraction
}

// Calculate RSI
export const calculateRSI = (candles: Candle[], periods = 14): number[] => {
  let gains = 0, losses = 0;
  const rsi = new Array(candles.length).fill(0);
  
  if (candles.length < periods + 1) return rsi;

  for (let i = 1; i <= periods; i++) {
    const change = candles[i].close - candles[i - 1].close;
    if (change > 0) gains += change;
    else losses -= change;
  }

  let avgGain = gains / periods;
  let avgLoss = losses / periods;
  
  if (avgLoss === 0) rsi[periods] = 100;
  else rsi[periods] = 100 - (100 / (1 + avgGain / avgLoss));

  for (let i = periods + 1; i < candles.length; i++) {
    const change = candles[i].close - candles[i - 1].close;
    const gain = change > 0 ? change : 0;
    const loss = change < 0 ? -change : 0;

    avgGain = (avgGain * (periods - 1) + gain) / periods;
    avgLoss = (avgLoss * (periods - 1) + loss) / periods;

    if (avgLoss === 0) rsi[i] = 100;
    else rsi[i] = 100 - (100 / (1 + avgGain / avgLoss));
  }

  return rsi;
};

// A backtest that runs over historical candles to find win rate (p)
// Strategy: Buy when RSI < 30, Exit at TP/SL
export const backtestStrategy = (
  candles: Candle[], 
  tpPercent: number = 0.02, // Take profit at +2%
  slPercent: number = 0.01  // Stop loss at -1%
): SimMetrics => {
  const rsi = calculateRSI(candles, 14);
  const trades: Trade[] = [];
  let openTrade: Trade | null = null;
  
  for (let i = 14; i < candles.length; i++) {
    const candle = candles[i];
    
    // Check if open trade needs to be closed
    if (openTrade) {
      const highestProfit = (candle.high - openTrade.entryPrice) / openTrade.entryPrice;
      const lowestDrop = (candle.low - openTrade.entryPrice) / openTrade.entryPrice;
      
      let closed = false;
      
      // Stop Loss Hit
      if (lowestDrop <= -slPercent) {
        openTrade.exitPrice = openTrade.entryPrice * (1 - slPercent);
        openTrade.profitRatio = -slPercent;
        openTrade.won = false;
        openTrade.exitTime = candle.timestamp;
        trades.push({...openTrade});
        openTrade = null;
        closed = true;
      }
      // Take Profit Hit (if SL wasn't hit first in the same candle, or we assume SL hits first defensively)
      else if (highestProfit >= tpPercent) {
        openTrade.exitPrice = openTrade.entryPrice * (1 + tpPercent);
        openTrade.profitRatio = tpPercent;
        openTrade.won = true;
        openTrade.exitTime = candle.timestamp;
        trades.push({...openTrade});
        openTrade = null;
        closed = true;
      }
    }
    
    // Check entry logic if no open trade
    if (!openTrade && rsi[i-1] !== 0 && rsi[i-1] < 30 && rsi[i] >= 30) {
      openTrade = {
        type: 'BUY',
        entryPrice: candle.close,
        entryTime: candle.timestamp
      };
    }
  }

  // Calculate metrics
  const completedTrades = trades;
  const wins = completedTrades.filter(t => t.won).length;
  const losses = completedTrades.length - wins;
  const winRate = completedTrades.length > 0 ? wins / completedTrades.length : 0;
  
  const totalWinRatio = completedTrades.filter(t => t.won).reduce((sum, t) => sum + (t.profitRatio || 0), 0);
  const averageWinRatio = wins > 0 ? totalWinRatio / wins : tpPercent;
  
  const totalLossRatio = completedTrades.filter(t => !t.won).reduce((sum, t) => sum + Math.abs(t.profitRatio || 0), 0);
  const averageLossRatio = losses > 0 ? totalLossRatio / losses : slPercent;
  
  const b = averageWinRatio / averageLossRatio;
  
  let kellyFraction = 0;
  if (completedTrades.length > 0 && b > 0) {
    kellyFraction = winRate - ((1 - winRate) / b);
  }

  return {
    totalTrades: completedTrades.length,
    wins,
    losses,
    winRate,
    averageWinRatio,
    averageLossRatio,
    b,
    // Clamp Kelly Fraction between 0% and 50% max to be realistic/safe in demo
    // Wait, let's allow it to go up to 100% but naturally clamp at 0.
    kellyFraction: Math.max(0, kellyFraction)
  };
};
