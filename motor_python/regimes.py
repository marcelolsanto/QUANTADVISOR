import sys
import json
import warnings
import numpy as np
import pandas as pd
from hmmlearn.hmm import GaussianHMM
from sklearn.preprocessing import StandardScaler
import redis
import logging

warnings.filterwarnings('ignore')

# =========================================================
# POOL GLOBAL DE CONEXÕES REDIS (Evita esgotamento de portas TCP)
# =========================================================
REDIS_POOL = redis.ConnectionPool(host='quant_redis', port=6379, db=0, decode_responses=True)
rdb_global = redis.Redis(connection_pool=REDIS_POOL)

def detectar_regime_mercado():
    """
    Sensor de Risco Sistêmico (HMM Multivariado)
    Consome o IBOV e o Dólar DIRETAMENTE DO REDIS.
    """
    try:
        ibov_str = rdb_global.get("hist:^BVSP")
        dolar_str = rdb_global.get("hist:BRL=X")

        if not ibov_str or not dolar_str:
            return {
                "sucesso": False, 
                "erro": "Benchmarks não encontrados no Redis.",
                "acionar_circuit_breaker": False,
                "regime_identificado": "DESCONHECIDO"
            }

        ibov_data = json.loads(ibov_str)
        dolar_data = json.loads(dolar_str)

        # Prevenção: Garante que estamos lidando com listas. 
        # (Se for um dict com chave 'close', adapte aqui: ibov_data = ibov_data['close'])
        if not isinstance(ibov_data, list) or not isinstance(dolar_data, list):
             return {"sucesso": False, "erro": "Formato de dados inválido no Redis. Esperava-se uma lista."}

        # Garante o mesmo tamanho para cruzar as matrizes
        tamanho_minimo = min(len(ibov_data), len(dolar_data))
        if tamanho_minimo < 50: # Reduzido para 50 para evitar falhas prematuras, mas ideal é >100
             return {"sucesso": False, "erro": "Histórico insuficiente para calibrar HMM."}

        df = pd.DataFrame({
            'BOVA11': ibov_data[-tamanho_minimo:],
            'USDBRL': dolar_data[-tamanho_minimo:]
        })

        # Engenharia de Features
        retorno_ibov = np.log(df['BOVA11'] / df['BOVA11'].shift(1))
        volatilidade_ibov = retorno_ibov.rolling(window=15).std()
        retorno_dolar = np.log(df['USDBRL'] / df['USDBRL'].shift(1))

        df_features = pd.DataFrame({
            'R_Ibov': retorno_ibov,
            'V_Ibov': volatilidade_ibov,
            'R_Dolar': retorno_dolar
        }).dropna()

        X = np.column_stack([df_features['R_Ibov'], df_features['V_Ibov'], df_features['R_Dolar']])
        
        scaler = StandardScaler()
        X_scaled = scaler.fit_transform(X)
        
        # HMM: Adicionado tratamento para não explodir caso a covariância fique singular
        try:
            modelo_hmm = GaussianHMM(n_components=3, covariance_type="full", n_iter=1000, random_state=42)
            modelo_hmm.fit(X_scaled)
        except ValueError as ve:
            logging.warning(f"HMM não convergiu (Possível matriz singular): {ve}")
            return {"sucesso": False, "erro": "HMM falhou na convergência.", "acionar_circuit_breaker": False, "regime_identificado": "DESCONHECIDO"}

        estados_ocultos = modelo_hmm.predict(X_scaled)
        estado_atual = int(estados_ocultos[-1])

        perfil_estados = {}
        for i in range(3):
            mascara = (estados_ocultos == i)
            
            if not mascara.any(): # Proteção contra estado vazio (evita NaN)
                perfil_estados[i] = {"retorno_ibov": 0.0, "risco_agregado": 0.0}
                continue

            # Lógica corrigida: Penaliza retornos negativos da bolsa E premia alta volatilidade
            r_ibov_mean = df_features['R_Ibov'][mascara].mean()
            v_ibov_mean = df_features['V_Ibov'][mascara].mean()
            
            # Novo Índice de Estresse (Volatilidade alta + Queda da Bolsa)
            risco_sistemico = float(v_ibov_mean - r_ibov_mean) 
            retorno_medio = float(r_ibov_mean * 252)
            
            perfil_estados[i] = {"retorno_ibov": retorno_medio, "risco_agregado": risco_sistemico}

        ordenado_por_risco = sorted(perfil_estados.items(), key=lambda x: x[1]['risco_agregado'])
        bear_id = ordenado_por_risco[2][0] 
        crab_id = ordenado_por_risco[1][0]

        if estado_atual == bear_id:
            regime, trava = "BEAR MARKET (Crise)", True
        elif estado_atual == crab_id:
            regime, trava = "CRAB MARKET (Indecisão)", False
        else:
            regime, trava = "BULL MARKET (Otimismo)", False

        return {
            "sucesso": True, 
            "estado_id": estado_atual, 
            "regime_identificado": regime,
            "acionar_circuit_breaker": trava,
            "metricas_regime_atual": {
                "retorno_ibov_esperado": round(perfil_estados[estado_atual]['retorno_ibov'] * 100, 2),
                "indice_estresse_agregado": round(perfil_estados[estado_atual]['risco_agregado'] * 1000, 2)
            }
        }

    except Exception as e:
        logging.error(f"Erro Crítico no Sensor HMM: {str(e)}")
        return {"sucesso": False, "erro": str(e), "acionar_circuit_breaker": False, "regime_identificado": "DESCONHECIDO"}