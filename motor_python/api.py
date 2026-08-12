import os
import sys
import logging
import uvicorn
import time

os.environ["HTTP_PROXY"] = ""
os.environ["HTTPS_PROXY"] = ""
os.environ["NO_PROXY"] = "*"

from typing import List, Dict
from fastapi import FastAPI, HTTPException, BackgroundTasks, APIRouter
from pydantic import BaseModel
import concurrent.futures

# Imports das lógicas de negócio
from llm_agent import consultar_cro_sintetico
from markowitz import otimizar_markowitz
from projecao import calcular_projecao
from correlacao import analisar_correlacao
from backtest import QuantBacktester
from main import processar_pipeline_quantitativo
from nlp_engine import analisar_sentimento_finbert
import redis
import json

from lstm_predictor import prever_lstm_rapido
from rl_trader import decidir_rl_rapido

import pandas as pd
import numpy as np
import riskfolio as rp

router = APIRouter()

MOTOR_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, MOTOR_DIR)

ARQUIVO_LOG = os.path.join(MOTOR_DIR, 'auditoria_matematica.log')

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S',
    handlers=[
        logging.FileHandler(ARQUIVO_LOG, mode='a', encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger("QuantAdvisor")
logger.info("🚀 [SISTEMA] Motor Quantitativo Iniciado. Arquitetura Stateless Ativa.")

tags_metadata = [
    {
        "name": "Inteligência Artificial & Preditivo",
        "description": "Incerência de redes neurais LSTM, Q-Learning (RL Trader) e análise de sentimento FinBERT.",
    },
    {
        "name": "Econometria & Otimização de Risco",
        "description": "Hierarchical Risk Parity (HRP), otimização Markowitz e matrizes de covariância.",
    },
    {
        "name": "Processos Estocásticos & Monte Carlo",
        "description": "Simulações de trajetórias de preços via Merton Jump-Diffusion e detecção de Regimes HMM.",
    },
    {
        "name": "Streaming & Ingestão",
        "description": "Pipelines atômicos de ingestão e fallback de dados em memória RAM (Redis).",
    },
]

app = FastAPI(
    title="QuantAdvisor Financial Engineering Engine",
    description="""
    ## 🏛️ Motor Matemático & Econométrico QuantAdvisor
    Microsserviço de alta velocidade para **gestão de portfólio, inteligência preditiva e análise de cauda estocástica**.
    
    ### Principais Recursos:
    * **Hierarchical Risk Parity (HRP)** via `riskfolio-lib` com Ponderação de Sentimento FinBERT.
    * **Simulação Merton Jump-Diffusion** para captura de choques e eventos de cauda (*Fat Tails*).
    * **Redes Neurais PyTorch (LSTM)** para projeção intradiária D+1.
    * **Agentes de Reinforcement Learning (Q-Trader)** para decisões atômicas de compra/venda.
    * **Classificação de Regime de Mercado (HMM)**: identificação de tendências Bull, Bear e Lateral.
    """,
    version="3.2.0-Production",
    openapi_tags=tags_metadata,
    docs_url="/docs",
    redoc_url="/redoc"
)

redis_pool = redis.ConnectionPool(host='quant_redis', port=6379, db=0, decode_responses=True)
rdb = redis.Redis(connection_pool=redis_pool)

class LstmRequest(BaseModel):
    ticker: str
    volume_atual: float = 0.0
    ibov_atual: float = 176641.00
    dolar_atual: float = 5.07
    selic_atual: float = 0.1415

class MarkowitzRequest(BaseModel):
    tickers: List[str]
    valores: Dict[str, float]
    caixa_livre: float
    perfil: str
    sentimentos_ia: Dict[str, float] = {}
    todos_tickers_disponiveis: List[str]

class ProjecaoRequest(BaseModel):
    valores_acoes: Dict[str, float]
    caixa_livre: float = 0.0
    caixa_usd: float = 0.0
    taxa_selic: float
    
class CenarioRequest(BaseModel):
    cenario: str

class RiscoRequest(BaseModel):
    tickers: List[str]

class NlpRequest(BaseModel):
    textos: List[str]

class RlRequest(BaseModel):
    ticker: str
    preco_atual: float
    z_score: float
    sentimento_nlp: float = 0.0
    choque_liquidez: float = 1.0  
    vol_intraday: float = 0.0

class TreinoBatchRequest(BaseModel):
    tickers: List[str]

class PayloadOtimizacao(BaseModel):
    usuario_id: int
    tickers: List[str]
    quantidades: Dict[str, float] = {} 
    valores: Dict[str, float] = {}    
    caixa_livre: float = 0.0
    caixa_usd: float = 0.0
    perfil: str = "Conservador"

@app.post("/streaming/ingestao/{ticker}/{fonte}")
def receber_dados_go(ticker: str, fonte: str, selic: float = 0.1450):
    try:
        chave_raw = f"raw:{fonte}:{ticker}"
        dados_crus_str = rdb.get(chave_raw)
        
        if not dados_crus_str:
            logging.error(f"⚠️ [API] Payload bruto de {ticker} não encontrado no Redis.")
            return {"sucesso": False, "erro": "Payload não encontrado na memória RAM."}
            
        dados_crus = json.loads(dados_crus_str)
        
    except Exception as e:
        logging.error(f"❌ [API] Erro ao ler Redis para {ticker}: {e}")
        return {"sucesso": False, "erro": str(e)}

    resultado = processar_pipeline_quantitativo(ticker, fonte, dados_crus, selic)
    return resultado

def treinar_ativo_worker(ticker: str):
    import logging
    from rl_trader import treinar_e_salvar_agente_rl
    from lstm_predictor import treinar_e_salvar_lstm
    import yfinance as yf

    try:
        logging.info(f"⚙️ [ROBÔ] Iniciando o estudo do ativo: {ticker}...")
        
        treinar_e_salvar_agente_rl(ticker)
        
        tem_numero = any(char.isdigit() for char in ticker)
        ticker_sa = f"{ticker}.SA" if tem_numero and not ticker.endswith('.SA') else ticker
        
        df = yf.download(ticker_sa, period="1y", interval="1d", progress=False, threads=False)
        time.sleep(2) 
        
        if not df.empty and len(df) >= 100:
            fechamentos = df['Close'].dropna().values.flatten().tolist()
            # 🚀 CORREÇÃO HFT: Extrai também o vetor de Volume
            volumes = df['Volume'].dropna().values.flatten().tolist() if 'Volume' in df else [1.0] * len(fechamentos)
            treinar_e_salvar_lstm(ticker, fechamentos, volumes)
            
        logging.info(f"✅ [ROBÔ] Treinamento de {ticker} 100% concluído!")
        return ticker, True
        
    except Exception as e:
        logging.error(f"❌ [ROBÔ] Falha ao treinar {ticker}: {e}")
        return ticker, False

def worker_treinamento_lote(tickers: List[str]):
    import logging
    
    nucleos_disponiveis = os.cpu_count() or 8
    qtd_robos = min(4, nucleos_disponiveis)
    
    logging.info(f"🔥 [MLOps] Container Docker detectou {nucleos_disponiveis} CPUs.")
    logging.info(f"🔥 [MLOps] Invocando {qtd_robos} Robôs Paralelos para treinar {len(tickers)} ativos...")

    with concurrent.futures.ProcessPoolExecutor(max_workers=qtd_robos) as executor:
        resultados = list(executor.map(treinar_ativo_worker, tickers))
        
    sucessos = sum(1 for r in resultados if r[1])
    logging.info(f"🏁 [MLOps] Batalhão concluído! {sucessos}/{len(tickers)} ativos treinados.")

@app.post("/treinar/batch")
def api_treinar_batch(req: TreinoBatchRequest, background_tasks: BackgroundTasks):
    background_tasks.add_task(worker_treinamento_lote, req.tickers)
    return {
        "sucesso": True, 
        "mensagem": f"Treinamento de {len(req.tickers)} ativos enfileirado em background."
    }

@app.post("/nlp/sentimento")
def api_sentimento(req: NlpRequest):
    score = analisar_sentimento_finbert(req.textos)
    return {"sucesso": True, "score_finbert": score}

@app.post("/risco")
def api_risco(req: RiscoRequest):
    resultado = analisar_correlacao(req.tickers)
    if not resultado or not resultado.get("sucesso"):
        raise HTTPException(status_code=500, detail="Erro na álgebra linear de correlação")
    return resultado

@app.get("/backtest/{ticker}")
def api_backtest(ticker: str):
    motor = QuantBacktester()
    df_dados = motor.carregar_dados(ticker)
    if df_dados is not None and not df_dados.empty:
        df_resultado, operacoes = motor.executar_estrategia(df_dados)
        resultado = motor.calcular_metricas(df_resultado, operacoes)
        if not resultado.get("sucesso"):
            raise HTTPException(status_code=400, detail=resultado.get("erro"))
        return resultado
    raise HTTPException(status_code=404, detail="Dados históricos indisponíveis para este ativo.")

@app.get("/montecarlo/{ticker}")
def api_montecarlo(ticker: str):
    from montecarlo import simular_monte_carlo
    resultado = simular_monte_carlo(ticker)
    if not resultado.get("sucesso"):
        raise HTTPException(status_code=400, detail=resultado.get("erro"))
    return resultado

@app.post("/markowitz")
def api_markowitz(req: MarkowitzRequest):
    resultado = otimizar_markowitz(
        req.tickers, 
        req.valores, 
        req.caixa_livre, 
        req.perfil,
        req.todos_tickers_disponiveis,
        req.sentimentos_ia
    )
    if not resultado.get("sucesso"):
        raise HTTPException(status_code=400, detail=resultado.get("erro"))
    return resultado

@app.post("/projecao")
def api_projecao(req: ProjecaoRequest):
    resultado = calcular_projecao(req.valores_acoes, req.caixa_livre, req.taxa_selic)
    if not resultado.get("sucesso"):
        raise HTTPException(status_code=400, detail=resultado.get("erro"))
    return resultado

@app.post("/agente/causalidade")
def api_inferencia_causal(req: CenarioRequest):
    resultado = consultar_cro_sintetico(req.cenario)
    if not resultado.get("sucesso"):
        raise HTTPException(status_code=500, detail=resultado.get("erro"))
    return resultado

CACHE_REGIME_HMM = None
ULTIMA_ATUALIZACAO_HMM = 0
TEMPO_DE_VIDA_CACHE_SEGUNDOS = 900  

def obter_risco_sistemico_cachead():
    global CACHE_REGIME_HMM, ULTIMA_ATUALIZACAO_HMM
    agora = time.time()
    
    if CACHE_REGIME_HMM is None or (agora - ULTIMA_ATUALIZACAO_HMM) > TEMPO_DE_VIDA_CACHE_SEGUNDOS:
        from regimes import detectar_regime_mercado
        
        logging.info("🔄 [HMM CACHE] Atualizando termômetros sistêmicos (IBOV/Dólar) via Yahoo...")
        info_regime = detectar_regime_mercado()
        
        if info_regime and info_regime.get("sucesso"):
            risco_bruto = info_regime.get("metricas_regime_atual", {}).get("indice_estresse_agregado", 20.0)
            risco_convertido = risco_bruto / 100.0
            
            CACHE_REGIME_HMM = risco_convertido
            ULTIMA_ATUALIZACAO_HMM = agora
            
            regime_texto = info_regime.get('regime_identificado', 'DESCONHECIDO')
            logging.info(f"✅ [HMM CACHE] Risco atualizado para: {risco_convertido:.4f} | Regime: {regime_texto}")
            
            rdb.set("regime_mercado_atual", regime_texto)
        else:
            if CACHE_REGIME_HMM is None:
                CACHE_REGIME_HMM = 0.20
            logging.warning(f"⚠️ [HMM CACHE] Falha ao atualizar. Mantendo risco em: {CACHE_REGIME_HMM}")
            
    return CACHE_REGIME_HMM

@app.post("/rl/decidir")
def api_rl_decidir(req: RlRequest):
    risco_sistemico_hmm = obter_risco_sistemico_cachead()

    resultado = decidir_rl_rapido(
        ticker=req.ticker,
        preco_atual=req.preco_atual,
        z_score=req.z_score,
        sentimento_nlp=req.sentimento_nlp,
        choque_liquidez=req.choque_liquidez,
        vol_intraday=req.vol_intraday,
        selic_atual=0.1450,
        risco_hmm=risco_sistemico_hmm
    )
    
    if not resultado.get("sucesso"):
        logging.info(f"⚠️ [RL Fallback] IA não treinada para {req.ticker}. Forçando sinal NEUTRO.")
        return {
            "sucesso": True, 
            "ticker": req.ticker, 
            "acao_rl": 0,
            "sinal_ia": "NEUTRO", 
            "z_score_base": req.z_score,
            "motivo": "Modelo RL pendente de treino. Usando matemática de base."
        }
        
    return resultado

@app.post("/lstm")
def api_lstm(req: LstmRequest):
    try:
        rdb_con = redis.Redis(host='quant_redis', port=6379, db=0, decode_responses=True)
        hist_str = rdb_con.get(f"hist:{req.ticker}")
    except Exception:
        rdb_con = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)
        hist_str = rdb_con.get(f"hist:{req.ticker}")
        
    if not hist_str:
        raise HTTPException(status_code=400, detail=f"Histórico do ativo {req.ticker} não foi encontrado no Redis.")
        
    historico_precos = json.loads(hist_str)
    
    # 🚀 CORREÇÃO HFT: Cria um array neutro de volumes caso a Ingestão em background não tenha sido disparada
    historico_volumes = [req.volume_atual if req.volume_atual > 0 else 1.0] * len(historico_precos)

    resultado = prever_lstm_rapido(
        ticker=req.ticker, 
        historico_precos=historico_precos,
        historico_volumes=historico_volumes,
        volume_atual=req.volume_atual,
        ibov_atual=req.ibov_atual,
        dolar_atual=req.dolar_atual,
        selic_atual=req.selic_atual
    )
    
    if not resultado.get("sucesso"):
        logging.warning(f"⚠️ [LSTM] Erro na Rede Neural para {req.ticker}: {resultado.get('erro')}")
        raise HTTPException(status_code=400, detail=resultado.get("erro"))
        
    return resultado

def construir_matriz_precos_redis(tickers: List[str]) -> pd.DataFrame:
    try:
        rdb_con = redis.Redis(host='quant_redis', port=6379, db=0, decode_responses=True)
        rdb_con.ping()
    except:
        rdb_con = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)

    dados_consolidados = {}
    tickers_em_falta = []

    for ticker in tickers:
        tem_numero = any(char.isdigit() for char in ticker)
        ticker_busca = f"{ticker}.SA" if tem_numero and not ticker.endswith('.SA') else ticker
        
        valor_historico = rdb_con.get(f"hist:{ticker_busca}")

        if not valor_historico:
            valor_historico = rdb_con.get(f"hist:{ticker}")

        if not valor_historico:
            tickers_em_falta.append(ticker_busca)
            continue

        try:
            historico_dict = json.loads(valor_historico)
            serie = pd.Series(historico_dict)
            serie.index = pd.to_datetime(serie.index)
            dados_consolidados[ticker_busca] = serie
        except Exception:
            tickers_em_falta.append(ticker_busca)

    if tickers_em_falta:
        raise ValueError(f"CACHE_MISS:{','.join(tickers_em_falta)}")

    df_precos = pd.DataFrame(dados_consolidados)
    df_precos.ffill(inplace=True)
    df_precos.dropna(inplace=True)
    
    return df_precos

@app.post("/api/otimizar/hrp_nlp")
def otimizar_carteira_hrp_nlp(payload: PayloadOtimizacao):
    try:
        rdb_local = rdb
        ativos_usuario = payload.tickers
        
        if len(ativos_usuario) < 5:
            ativos_set = list(rdb_local.smembers("ativos_set"))
            candidatos = []
            for t in ativos_set:
                data_str = rdb_local.get(f"ticker:{t}")
                if data_str:
                    try:
                        d = json.loads(data_str)
                        candidatos.append((t, d.get("z_score", 0)))
                    except:
                        pass
            
            candidatos.sort(key=lambda x: x[1])
            top_10 = [c[0] for c in candidatos[:10]]
            
            if not top_10:
                top_10 = ["PETR4", "VALE3", "ITUB4", "WEGE3", "BBAS3", "ELET3", "B3SA3", "SUZB3", "RENT3"]
                
            universo_alvo = list(set(ativos_usuario + top_10))
        else:
            universo_alvo = ativos_usuario

        if len(universo_alvo) < 3:
            raise HTTPException(status_code=400, detail="Sem ativos no mercado para otimizar. Atualize o sistema.")

        try:
            df_precos = construir_matriz_precos_redis(universo_alvo)
        except ValueError:
            from markowitz import obter_matriz_precos
            df_precos = obter_matriz_precos(universo_alvo)
            if df_precos is None or df_precos.empty:
                raise HTTPException(status_code=500, detail="Fábrica de dados temporariamente offline.")

        retornos = df_precos.pct_change().dropna()

        if retornos.empty or len(retornos.columns) < 2:
             raise HTTPException(status_code=400, detail="Histórico insuficiente para cruzamento de matriz.")

        port = rp.HCPortfolio(returns=retornos)
        pesos_otimizados = port.optimization(model='HRP', codependence='pearson', rm='MV', rf=0.0, linkage='single', leaf_order=True)
        pesos_dict = {k.replace('.SA', ''): v for k, v in pesos_otimizados['weights'].to_dict().items()}

        pesos_ajustados = {}
        for ativo, peso_base in pesos_dict.items():
            score_nlp_str = rdb_local.get(f"nlp:{ativo}")
            score_nlp = float(score_nlp_str) if score_nlp_str else 0.0
            multiplicador = max(0.1, 1.0 + score_nlp) 
            pesos_ajustados[ativo] = peso_base * multiplicador

        soma_total = sum(pesos_ajustados.values())
        pesos_finais = {ativo: (peso / soma_total) for ativo, peso in pesos_ajustados.items()}
        alocacao_limpa = {ativo: round(peso * 100, 2) for ativo, peso in pesos_finais.items() if peso > 0.005}

        precos_atuais = df_precos.ffill().iloc[-1].to_dict()
        
        valor_acoes_mercado = 0.0
        qtd_real_possuida = {} 

        for ativo in payload.tickers:
            ticker_yf = f"{ativo}.SA" if any(c.isdigit() for c in ativo) and not ativo.endswith('.SA') else ativo
            preco_hoje = precos_atuais.get(ticker_yf, 0.0)
            if pd.isna(preco_hoje): preco_hoje = 0.0
            
            qtd = payload.quantidades.get(ativo, 0.0)
            if qtd == 0 and payload.valores.get(ativo, 0.0) > 0:
                qtd = payload.valores.get(ativo, 0.0) / (preco_hoje if preco_hoje > 0 else 1)
            
            qtd_real_possuida[ativo] = qtd
            valor_acoes_mercado += (qtd * preco_hoje)

        patrimonio_total = valor_acoes_mercado + payload.caixa_livre
        if pd.isna(patrimonio_total) or patrimonio_total <= 0:
            patrimonio_total = 1000.0 
            
        alocacao_ideal_detalhada = []
        receita_rebalanceamento = []

        for ativo, perc in alocacao_limpa.items():
            ticker_yf = f"{ativo}.SA" if any(c.isdigit() for c in ativo) and not ativo.endswith('.SA') else ativo
            preco_hoje = precos_atuais.get(ticker_yf, 0.0)
            if pd.isna(preco_hoje): preco_hoje = 0.0

            valor_ideal_brl = float(patrimonio_total * (perc / 100.0))
            qtd_possuida = qtd_real_possuida.get(ativo, 0.0)
            valor_atual_brl = float(qtd_possuida * preco_hoje)
            
            alocacao_ideal_detalhada.append({
                "ativo": ativo,
                "peso_ideal_perc": float(perc),
                "valor_ideal_brl": round(valor_ideal_brl, 2)
            })

            diferenca = valor_ideal_brl - valor_atual_brl
            if abs(diferenca) > 10.0 and not pd.isna(diferenca):
                receita_rebalanceamento.append({
                    "ativo": ativo,
                    "acao": "COMPRAR" if diferenca > 0 else "VENDER",
                    "valor_brl": round(abs(diferenca), 2),
                    "is_novo": (qtd_possuida == 0)
                })

        mu_anual = retornos.mean() * 252
        cov_anual = retornos.cov() * 252
        ativos_yf = retornos.columns.tolist()
        w_atual = np.zeros(len(ativos_yf))
        w_ideal = np.zeros(len(ativos_yf))

        for i, ativo_yf in enumerate(ativos_yf):
            ativo_limpo = ativo_yf.replace('.SA', '')
            if patrimonio_total > 0:
                qtd_possuida = qtd_real_possuida.get(ativo_limpo, 0.0)
                preco_hoje = precos_atuais.get(ativo_yf, 0.0)
                w_atual[i] = (qtd_possuida * preco_hoje) / patrimonio_total
            w_ideal[i] = pesos_finais.get(ativo_limpo, 0.0)

        vol_atual = np.sqrt(np.dot(w_atual.T, np.dot(cov_anual, w_atual)))
        ret_atual = np.dot(w_atual, mu_anual)
        sharpe_atual = ret_atual / vol_atual if vol_atual > 0 else 0

        vol_ideal = np.sqrt(np.dot(w_ideal.T, np.dot(cov_anual, w_ideal)))
        ret_ideal = np.dot(w_ideal, mu_anual)
        sharpe_ideal = ret_ideal / vol_ideal if vol_ideal > 0 else 0

        return {
            "sucesso": True,
            "metodo": "HRP + NLP Sentiment Tilting",
            "alocacao_ideal": alocacao_ideal_detalhada,
            "receita_rebalanceamento": receita_rebalanceamento,
            "metricas_atuais": {
                "retorno_anual": round(ret_atual * 100, 2), 
                "risco_anual": round(vol_atual * 100, 2), 
                "sharpe": round(sharpe_atual, 2)
            },
            "metricas_otimizadas": {
                "retorno_anual": round(ret_ideal * 100, 2), 
                "risco_anual": round(vol_ideal * 100, 2), 
                "sharpe": round(sharpe_ideal, 2)
            }
        }

    except HTTPException as h:
        raise h
    except Exception as e:
        import traceback
        logging.error(f"Erro interno Markowitz: {traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=f"Motor encontrou uma anomalia na matriz de risco.")

class FallbackPayload(BaseModel):
    chave: str
    dados: str

@app.post("/streaming/fallback_redis")
def injetar_redis_fallback(payload: FallbackPayload):
    try:
        rdb.setex(payload.chave, 900, payload.dados)
        logging.info(f"🆘 [FALLBACK PYTHON] Dados da chave {payload.chave} injetados no Redis!")
        return {"sucesso": True}
        
    except Exception as e:
        logging.error(f"❌ [FALLBACK PYTHON] Falha ao salvar no Redis: {e}")
        raise HTTPException(status_code=500, detail=str(e))