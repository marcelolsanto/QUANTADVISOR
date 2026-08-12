// ==============================================================================
// CLIENTE API DA PLATAFORMA QUANTADVISOR (Go Engine & Python AI Broker)
// ==============================================================================

export interface SignalHFT {
  strategy: string;
  action: 'SHORT_SPREAD' | 'LONG_SPREAD' | 'CLOSE_POSITION' | 'NEUTRO';
  asset_a: string;
  asset_b: string;
  target_qty: number;
  price?: number;
  timestamp: string;
}

export interface BuyingPowerData {
  buying_power: number;
  currency: string;
  timestamp: string;
}

export interface TWAPExecution {
  order_id: string;
  symbol: string;
  side: 'COMPRA' | 'VENDA';
  qty: number;
  price: number;
  slice_index: number;
  total_slices: number;
  status: 'EXECUTADO' | 'PENDENTE' | 'ERRO';
  timestamp: string;
}

export interface PosicaoCarteira {
  ticker: string;
  quantidade: number;
  preco_medio: number;
  cotacao_atual: number;
  z_score_atual: number;
  lucro_prejuizo_financeiro: number;
}

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

/**
 * Busca o saldo / poder de compra sincronizado no Redis (hft:wallet:buying_power)
 */
export async function getBuyingPower(): Promise<BuyingPowerData> {
  try {
    const res = await fetch(`${API_BASE_URL}/wallet/buying-power`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (err) {
    console.warn('⚠️ [API CLIENT] Falha ao conectar ao Go Engine. Retornando fallback mock de saldo:', err);
    return {
      buying_power: 100000.0,
      currency: 'USD',
      timestamp: new Date().toISOString()
    };
  }
}

/**
 * Busca os ultimos sinais HFT de Pairs Trading e Reciclagem de Capital
 */
export async function getSinaisHFT(): Promise<SignalHFT[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/hft/signals`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (err) {
    console.warn('⚠️ [API CLIENT] Falha ao buscar sinais HFT. Retornando mock:', err);
    return [
      {
        strategy: 'pairs_trading',
        action: 'SHORT_SPREAD',
        asset_a: 'AAPL',
        asset_b: 'MSFT',
        target_qty: 1000,
        price: 185.50,
        timestamp: new Date().toISOString()
      },
      {
        strategy: 'capital_recycling',
        action: 'CLOSE_POSITION',
        asset_a: 'VALE3',
        asset_b: '',
        target_qty: 200,
        price: 62.50,
        timestamp: new Date(Date.now() - 300000).toISOString()
      }
    ];
  }
}

/**
 * Busca o historico de execuções de ordens fracionadas pelo TWAP Engine
 */
export async function getExecucoesTWAP(): Promise<TWAPExecution[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/hft/twap-history`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (err) {
    console.warn('⚠️ [API CLIENT] Falha ao buscar historico TWAP. Retornando mock:', err);
    return [
      {
        order_id: 'alpaca-ordem-101',
        symbol: 'AAPL',
        side: 'VENDA',
        qty: 200,
        price: 185.50,
        slice_index: 1,
        total_slices: 5,
        status: 'EXECUTADO',
        timestamp: new Date().toISOString()
      },
      {
        order_id: 'alpaca-ordem-102',
        symbol: 'AAPL',
        side: 'VENDA',
        qty: 200,
        price: 185.45,
        slice_index: 2,
        total_slices: 5,
        status: 'EXECUTADO',
        timestamp: new Date(Date.now() - 12000).toISOString()
      }
    ];
  }
}
