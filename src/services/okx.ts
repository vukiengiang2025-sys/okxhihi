// src/services/okx.ts
import axios from 'axios';

export interface Candle {
  timestamp: number;
  open: number;
  high: number;
  low: number;
  close: number;
  vol: number;
  volCcy: number;
  volCcyQuote: number;
  confirm: boolean;
}

const parseCandle = (data: string[]): Candle => {
  return {
    timestamp: parseInt(data[0], 10),
    open: parseFloat(data[1]),
    high: parseFloat(data[2]),
    low: parseFloat(data[3]),
    close: parseFloat(data[4]),
    vol: parseFloat(data[5]),
    volCcy: parseFloat(data[6]),
    volCcyQuote: parseFloat(data[7]),
    confirm: data[8] === '1',
  };
};

export const fetchCandles = async (instId: string, bar: string, limit: number = 300): Promise<Candle[]> => {
  const res = await axios.get('/api/okx/candles', {
    params: { instId, bar, limit }
  });
  if (res.data && res.data.data) {
    // OKX returns newest first. We reverse to oldest first.
    return res.data.data.map(parseCandle).reverse();
  }
  return [];
};

export const fetchHistoryCandles = async (instId: string, bar: string, limit: number = 300): Promise<Candle[]> => {
  const res = await axios.get('/api/okx/history-candles', {
    params: { instId, bar, limit }
  });
  if (res.data && res.data.data) {
    return res.data.data.map(parseCandle).reverse();
  }
  return [];
};
