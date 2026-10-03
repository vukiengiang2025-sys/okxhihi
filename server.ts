import express from 'express';
import { createServer as createViteServer } from 'vite';
import path from 'path';
import axios from 'axios';

async function startServer() {
  const app = express();
  const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 3000;

  app.use(express.json());

  // Proxy endpoint for OKX APIs to avoid CORS
  app.get('/api/okx/candles', async (req, res) => {
    try {
      const { instId, bar, limit } = req.query;
      const response = await axios.get('https://www.okx.com/api/v5/market/candles', {
        params: {
          instId: instId || 'BTC-USDT',
          bar: bar || '1m',
          limit: limit || '300',
        },
      });
      res.json(response.data);
    } catch (error: any) {
      console.error('OKX API Error:', error.response?.data || error.message);
      res.status(500).json({ error: 'Failed to fetch data from OKX' });
    }
  });

  app.get('/api/okx/history-candles', async (req, res) => {
    try {
      const { instId, bar, limit, after } = req.query;
      const response = await axios.get('https://www.okx.com/api/v5/market/history-candles', {
        params: {
          instId: instId || 'BTC-USDT',
          bar: bar || '1m',
          limit: limit || '300',
          after
        },
      });
      res.json(response.data);
    } catch (error: any) {
      console.error('OKX API Error:', error.response?.data || error.message);
      res.status(500).json({ error: 'Failed to fetch history data from OKX' });
    }
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    // app.get('*', ...) requires an index.html file to exist in output.
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
