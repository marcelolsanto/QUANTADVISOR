import os
import warnings
import numpy as np
import pandas as pd
import gymnasium as gym
from gymnasium import spaces
from stable_baselines3 import PPO
import redis
import json

warnings.filterwarnings('ignore')

# =====================================================================
# POOL GLOBAIS DE REDE (Performance HFT)
# =====================================================================
REDIS_POOL = redis.ConnectionPool(host='quant_redis', port=6379, db=0, decode_responses=True)
rdb_global = redis.Redis(connection_pool=REDIS_POOL)

CACHE_MODELOS_PPO = {}

class SharpeTradingEnv(gym.Env):
    """
    Ambiente de Simulação Institucional com Impacto de Mercado Não-Linear.
    """
    def __init__(self, df, taxa_selic=0.145):
        super(SharpeTradingEnv, self).__init__()
        self.df = df
        self.taxa_selic_diaria = taxa_selic / 252
        self.current_step = 0
        self.ultima_acao = 0 
        
        self.action_space = spaces.Discrete(3) 
        self.observation_space = spaces.Box(low=-np.inf, high=np.inf, shape=(10,), dtype=np.float32)

    def reset(self, seed=None, options=None):
        super().reset(seed=seed)
        self.current_step = 0
        self.ultima_acao = 0
        return self._get_obs(), {}

    def _get_obs(self):
        row = self.df.iloc[self.current_step]
        obs = [
            row['Z_Score'], 
            row['Vol_20d'] * 100, 
            row['Slippage_Estimado'] * 1000, 
            row['Sentimento'],
            self.taxa_selic_diaria * 252 * 100, 
            row['Proxy_Crise'] * 100,
            row['Divida_EBITDA'],
            row['Margem_Liquida'] * 100,
            row['Tendencia'],
            row['Momentum_6m'] * 100
        ]
        return np.array(obs, dtype=np.float32)

    def step(self, action):
        self.current_step += 1
        done = self.current_step >= len(self.df) - 1
        if done:
            return self._get_obs(), 0.0, done, False, {}

        row_atual = self.df.iloc[self.current_step]
        retorno_futuro = row_atual['Retorno']
        volatilidade = row_atual['Vol_20d'] if pd.notna(row_atual['Vol_20d']) and row_atual['Vol_20d'] > 0 else 0.01
        
        slippage = row_atual['Slippage_Estimado']
        custo = slippage if action != self.ultima_acao else 0.0
        self.ultima_acao = action

        retorno_portfolio = 0
        if action == 1:   
            retorno_portfolio = retorno_futuro - custo
        elif action == 2: 
            retorno_portfolio = -retorno_futuro - custo 
        elif action == 0: 
            retorno_portfolio = self.taxa_selic_diaria
            
        sharpe_reward = (retorno_portfolio - self.taxa_selic_diaria) / volatilidade
        
        if retorno_portfolio < -0.02: 
            sharpe_reward -= 2.0 
            
        # =====================================================================
        # 🧠 CORREÇÃO: MATRIZ DE RECOMPENSA BALANCEADA (LONG vs SHORT)
        # =====================================================================
        if action == 1:  # LONG
            if row_atual['Sentimento'] > 0.30: sharpe_reward += 0.5 
            elif row_atual['Sentimento'] < -0.40: sharpe_reward -= 1.5 
            
            if row_atual['Divida_EBITDA'] > 3.0: sharpe_reward -= 2.0 
            if row_atual['Margem_Liquida'] > 0.15: sharpe_reward += 1.0 
            if row_atual['Margem_Liquida'] < 0: sharpe_reward -= 1.0 
            if row_atual['Tendencia'] == 0.0: sharpe_reward -= 1.5
            if row_atual['Momentum_6m'] < 0.0: sharpe_reward -= 1.0

        elif action == 2:  # SHORT
            # No short, as regras fundamentais são espelhadas para manter a balança do aprendizado
            if row_atual['Sentimento'] < -0.40: sharpe_reward += 0.5 
            elif row_atual['Sentimento'] > 0.30: sharpe_reward -= 1.5 
            
            if row_atual['Divida_EBITDA'] > 3.0: sharpe_reward += 1.0 # Premia short em lixo
            if row_atual['Margem_Liquida'] > 0.15: sharpe_reward -= 1.5 # Penaliza short em empresa boa
            if row_atual['Tendencia'] == 1.0: sharpe_reward -= 1.5 # Não opere contra a maré
            if row_atual['Momentum_6m'] > 0.0: sharpe_reward -= 1.0

        sharpe_reward = max(-5.0, min(sharpe_reward, 5.0))
        return self._get_obs(), float(sharpe_reward), done, False, {}

def extrair_fundamentos_redis(ticker):
    divida_ebitda = 0.0
    margem_liquida = 0.0
    try:
        fund_str = rdb_global.get(f"fund:{ticker}")
        
        if fund_str:
            fund_json = json.loads(fund_str)
            dados_fin = fund_json.get('quoteSummary', {}).get('result', [{}])[0]
            financeiro = dados_fin.get('financialData', {})
            
            ebitda = financeiro.get('ebitda', {}).get('raw', 1)
            total_debt = financeiro.get('totalDebt', {}).get('raw', 0)
            
            divida_ebitda = total_debt / ebitda if ebitda and ebitda != 0 else 0
            margem_liquida = financeiro.get('profitMargins', {}).get('raw', 0)
    except Exception:
        pass
        
    return divida_ebitda, margem_liquida

def extrair_tendencia_redis(ticker, preco_atual):
    tendencia = 1.0
    momentum_6m = 0.0
    try:
        hist_str = rdb_global.get(f"hist:{ticker}")
        if hist_str:
            fechamentos = json.loads(hist_str)
            fechamentos = [float(p) for p in fechamentos if p is not None and p > 0]
            
            if len(fechamentos) >= 200:
                sma_200 = sum(fechamentos[-200:]) / 200.0
                tendencia = 1.0 if preco_atual > sma_200 else 0.0
                
            if len(fechamentos) >= 126:
                preco_6_meses = fechamentos[-126]
                momentum_6m = (preco_atual / preco_6_meses) - 1.0 if preco_6_meses > 0 else 0.0
    except Exception:
        pass
        
    return tendencia, momentum_6m


def decidir_rl_rapido(ticker: str, preco_atual: float, z_score: float, sentimento_nlp: float, 
                      choque_liquidez: float, vol_intraday: float, 
                      vol_atual: float = 0.02, selic_atual: float = 0.1450, risco_hmm: float = 0.20):
    
    if choque_liquidez < 0.5 or vol_intraday < 0.01:
        return {
            "sucesso": True, "ticker": ticker, "acao_rl": 0,
            "sinal_ia": "NEUTRO", "z_score_base": z_score,
            "motivo": f"Fora de combate: Liquidez {choque_liquidez:.2f}x | Vol {vol_intraday*100:.2f}%"
        }

    global CACHE_MODELOS_PPO
    try:
        if ticker not in CACHE_MODELOS_PPO:
            caminho_modelo = f"modelos_salvos/ppo_{ticker}.zip"
            if not os.path.exists(caminho_modelo):
                return {"sucesso": False, "erro": "Agente RL não treinado."}
            # 🚀 Força o load em CPU para evitar OutOfMemory da placa de vídeo se armazenar 400 ativos no cache
            CACHE_MODELOS_PPO[ticker] = PPO.load(caminho_modelo, device="cpu")

        modelo_ppo = CACHE_MODELOS_PPO[ticker]
        
        divida_ebitda, margem_liquida = extrair_fundamentos_redis(ticker)
        tendencia, momentum_6m = extrair_tendencia_redis(ticker, preco_atual)
        
        slippage_hoje = 0.0003 + (0.1 * vol_atual * np.sqrt(1.0 / choque_liquidez))
        
        estado_hoje = np.array([
            z_score, 
            vol_atual * 100, 
            slippage_hoje * 1000, 
            sentimento_nlp, 
            selic_atual * 100, 
            risco_hmm * 100,
            divida_ebitda,
            margem_liquida * 100,
            tendencia,
            momentum_6m * 100
        ], dtype=np.float32)
        
        acao, _ = modelo_ppo.predict(estado_hoje, deterministic=True)
        mapa_acoes = {0: "NEUTRO", 1: "COMPRA FORTE", 2: "ALERTA DE VENDA"}
        
        return {
            "sucesso": True, "ticker": ticker, "acao_rl": int(acao),
            "sinal_ia": mapa_acoes[int(acao)], "z_score_base": z_score
        }
        
    except Exception as e:
        return {"sucesso": False, "erro": f"Erro RL: {str(e)}"}
    

def treinar_e_salvar_agente_rl(ticker: str, fechamentos: list = None):
    """Treina o PPO com dados macroeconômicos e de Liquidez usando APENAS a RAM."""
    os.makedirs("modelos_salvos", exist_ok=True)
    global CACHE_MODELOS_PPO
    
    try:
        if not fechamentos:
            hist_str = rdb_global.get(f"hist:{ticker}")
            if hist_str:
                fechos_brutos = json.loads(hist_str)
                fechamentos = [float(p) for p in fechos_brutos if p is not None and p > 0]
        
        if not fechamentos or len(fechamentos) < 50:
            return {"sucesso": False, "erro": "Dados insuficientes na RAM."}
            
        df = pd.DataFrame({'Preco': fechamentos})
        
        df['Volume'] = 5000000.0 / df['Preco']
        
        df['Retorno'] = df['Preco'].pct_change()
        df['Vol_20d'] = df['Retorno'].rolling(window=20).std()
        df['SMA_50'] = df['Preco'].rolling(window=50).mean()
        df['STD_50'] = df['Preco'].rolling(window=50).std()
        df['Z_Score'] = (df['Preco'] - df['SMA_50']) / df['STD_50']
        
        lote_institucional_base = 50000.0 
        df['Volume_Financeiro'] = df['Preco'] * df['Volume']
        df['ADV_20d'] = df['Volume_Financeiro'].rolling(window=20).mean()
        
        df['Slippage_Estimado'] = 0.0003 + (0.1 * df['Vol_20d'] * np.sqrt(lote_institucional_base / (df['ADV_20d'] + 1.0)))
        
        df['Sentimento'] = df['Retorno'].rolling(window=10).mean().fillna(0) * 15
        df['Sentimento'] = df['Sentimento'].clip(-1.0, 1.0)
        df['Proxy_Crise'] = df['Retorno'].rolling(window=50).std() * np.sqrt(252)
        df['SMA_200'] = df['Preco'].rolling(window=200).mean()
        df['Tendencia'] = np.where(df['Preco'] > df['SMA_200'], 1.0, 0.0)
        df['Momentum_6m'] = df['Preco'].pct_change(periods=126).fillna(0)
        
        divida_ebitda, margem_liquida = extrair_fundamentos_redis(ticker)
        df['Divida_EBITDA'] = divida_ebitda
        df['Margem_Liquida'] = margem_liquida
        
        df.dropna(inplace=True)
        if len(df) < 50:
            return {"sucesso": False, "erro": "Dados limpos insuficientes."}
            
        env = SharpeTradingEnv(df)
        modelo_ppo = PPO("MlpPolicy", env, verbose=0, learning_rate=0.003)
        modelo_ppo.learn(total_timesteps=8000)
        
        # O SB3 anexa o .zip automaticamente no save
        modelo_ppo.save(f"modelos_salvos/ppo_{ticker}")
        
        # 🚀 CORREÇÃO: Limpa o cérebro velho do cache se o agente foi treinado
        if ticker in CACHE_MODELOS_PPO:
            del CACHE_MODELOS_PPO[ticker]
            
        return {"sucesso": True}
    except Exception as e:
        return {"sucesso": False, "erro": str(e)}