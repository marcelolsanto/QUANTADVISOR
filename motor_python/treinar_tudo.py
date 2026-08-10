import os
import logging
import concurrent.futures
import json
import redis
import torch
import requests
import traceback
import yfinance as yf

os.environ["HTTP_PROXY"] = ""
os.environ["HTTPS_PROXY"] = ""
os.environ["NO_PROXY"] = "*"

from rl_trader import treinar_e_salvar_agente_rl
from lstm_predictor import treinar_e_salvar_lstm

torch.set_num_threads(15)
logging.basicConfig(level=logging.INFO, format='%(message)s')

rdb = redis.Redis(host='quant_redis', port=6379, db=0, decode_responses=True)

def extrair_dolar_banco_central(redis_db):
    logging.info("🏛️ [MAESTRO] Iniciando extração do histórico do Dólar...")
    fechamentos_dolar = []
    
    try:
        url = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.1/dados/ultimos/252?formato=json"
        resposta = requests.get(url, timeout=10)
        resposta.raise_for_status()
        dados = resposta.json()
        fechamentos_dolar = [float(dia['valor']) for dia in dados]
        origem = "Banco Central (SGS)"
    except Exception as e:
        logging.warning(f"⚠️ [MAESTRO] Falha no BCB ({e}). Acionando Contingência 1: AwesomeAPI...")
        try:
            url_awesome = "https://economia.awesomeapi.com.br/json/daily/USD-BRL/252"
            resposta = requests.get(url_awesome, timeout=10)
            resposta.raise_for_status()
            dados = resposta.json()
            fechamentos_dolar = [float(dia['ask']) for dia in reversed(dados)]
            origem = "AwesomeAPI"
        except Exception as e2:
            logging.warning(f"⚠️ [MAESTRO] Falha na AwesomeAPI ({e2}). Acionando Contingência 2: Yahoo Finance...")
            try:
                df = yf.download("BRL=X", period="1y", interval="1d", progress=False, threads=False)
                if not df.empty:
                    if isinstance(df.columns, pd.MultiIndex):
                        coluna = df['Close']['BRL=X']
                    else:
                        coluna = df['Close']
                    fechamentos_dolar = coluna.dropna().values.flatten().tolist()
                    origem = "Yahoo Finance"
                else:
                    raise ValueError("Yahoo Finance retornou dataframe vazio.")
            except Exception as e3:
                logging.error(f"❌ [MAESTRO] Falha crítica em TODAS as fontes de Dólar. Erro final: {e3}")
                return 
                
    if fechamentos_dolar:
        redis_db.set("hist:BRL=X", json.dumps(fechamentos_dolar))
        logging.info(f"✅ [MAESTRO] Dólar ({origem}) salvo na RAM! Última cotação: R$ {fechamentos_dolar[-1]:.4f}")

def extrair_ibovespa_maestro(redis_db):
    logging.info("📊 [MAESTRO] Baixando Ibovespa (Yahoo) apenas UMA VEZ...")
    try:
        df = yf.download("^BVSP", period="1y", interval="1d", progress=False, threads=False)
        if not df.empty:
            fechamentos_ibov = df['Close'].dropna().values.flatten().tolist()
            redis_db.set("hist:^BVSP", json.dumps(fechamentos_ibov))
            logging.info(f"✅ [MAESTRO] Ibovespa salvo na RAM! Último ponto: {fechamentos_ibov[-1]:.0f}")
    except Exception as e:
        logging.error(f"❌ [MAESTRO] Erro ao buscar Ibovespa: {e}")

# 🚀 NOVO: Lê os dois vetores (Preço e Volume) da RAM
def extrair_dados_memoria_ram(ticker):
    try:
        hist_json = rdb.get(f"hist:{ticker}")
        vol_json = rdb.get(f"vol:{ticker}")
        
        fechamentos = []
        volumes = []
        
        if hist_json:
            fechamentos = [float(p) for p in json.loads(hist_json) if p is not None and p > 0.0]
            
        if vol_json:
            volumes = [float(v) for v in json.loads(vol_json) if v is not None and v > 0.0]
        else:
            volumes = [1.0] * len(fechamentos)
            
        return fechamentos, volumes
    except Exception as e:
        logging.error(f"Erro ao ler {ticker} do Redis: {e}")
    return [], []

def processar_calculo_pesado_ia(dados_payload):
    # 🚀 NOVO: Desempacota as três variáveis e repassa para a LSTM
    ticker, fechamentos, volumes = dados_payload
    try:
        treinar_e_salvar_agente_rl(ticker, fechamentos)
        treinar_e_salvar_lstm(ticker, fechamentos, volumes) 
        logging.info(f"   ✅ [ROBÔ] Modelo LSTM e Scalers treinados para: {ticker}")
        return True
    except Exception as e:
        logging.error(f"❌ [PROCESSADOR] Erro no cálculo de {ticker}:")
        logging.error(traceback.format_exc())
        return False

def iniciar_treinamento_paralelo_otimizado():
    os.makedirs("modelos_salvos", exist_ok=True)
    fila_treinamento = []
    
    logging.info(f"📡 Fase 1: Sincronizando com a Memória Partilhada do Golang (Redis)...")
    
    tickers_no_redis = rdb.smembers("ativos_set")
    lista_ativos = list(tickers_no_redis)
    
    if not lista_ativos:
        logging.warning("⚠️ O Redis está vazio! O Golang não baixou os dados ou a chave 'ativos_set' expirou.")
        return

    logging.info(f"Encontrados {len(lista_ativos)} ativos prontos para treino.")

    for i, ticker in enumerate(lista_ativos):
        fechamentos, volumes = extrair_dados_memoria_ram(ticker)
        
        if len(fechamentos) >= 100:
            fila_treinamento.append((ticker, fechamentos, volumes))
        else:
            logging.warning(f"   ⚠️ Histórico insuficiente para {ticker} ({len(fechamentos)} dias).")

    nucleos_seguros = min(12, os.cpu_count() or 12)
    
    logging.info("\n🌍 Preparando métricas macroeconômicas na RAM...")
    extrair_dolar_banco_central(rdb)
    extrair_ibovespa_maestro(rdb)

    logging.info(f"\n🚀 Fase 2: Ativando {nucleos_seguros} Robôs Paralelos na memória RAM...")
    
    with concurrent.futures.ProcessPoolExecutor(max_workers=nucleos_seguros) as executor:
        resultados = list(executor.map(processar_calculo_pesado_ia, fila_treinamento))
        
    sucessos = sum(1 for r in resultados if r)
    logging.info(f"\n🏁 [MLOps] Pipeline finalizado! {sucessos}/{len(fila_treinamento)} modelos atualizados.")

if __name__ == "__main__":
    iniciar_treinamento_paralelo_otimizado()