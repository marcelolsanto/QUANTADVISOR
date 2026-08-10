import os
import sys
import json
import logging
import warnings
import pandas as pd
import numpy as np
import scipy.stats as stats
from statsmodels.tsa.arima.model import ARIMA
from vies_temporal import aplicar_vies_temporal_intradiario
from arch import arch_model
from scipy.stats import t
import redis
import math

# Importa módulos internos
from normalizador import extrair_serie_padronizada
from regimes import detectar_regime_mercado

# =========================================================
# CONFIGURAÇÃO DE AMBIENTE E PATHS
# =========================================================
MOTOR_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, MOTOR_DIR)
warnings.filterwarnings('ignore')

BASE_DIR = os.path.dirname(MOTOR_DIR)
PASTA_RAW = os.path.join(BASE_DIR, 'coletor_go', 'raw_data')

# =========================================================
# 🚀 OTIMIZAÇÃO: CONNECTION POOLS GLOBAIS DO REDIS
# =========================================================
REDIS_POOL_DECODED = redis.ConnectionPool(host='quant_redis', port=6379, db=0, decode_responses=True)
REDIS_POOL_RAW = redis.ConnectionPool(host='quant_redis', port=6379, db=0)

rdb_global = redis.Redis(connection_pool=REDIS_POOL_DECODED)
rdb_grafico_global = redis.Redis(connection_pool=REDIS_POOL_RAW)

def processar_pipeline_quantitativo(ticker: str, fonte: str, dados_crus, taxa_selic_atual: float = 0.1450) -> dict:
    """
    Motor Matemático Puro (Stateless):
    Processa a econometria avançada de forma atômica para um único ativo.
    """
    # 1. Normalização: Extrai o DataFrame completo (Preço + Volume)
    df_mercado = extrair_serie_padronizada(ticker, fonte, dados_crus).copy()
    
    if df_mercado.empty:
        return {"sucesso": False, "motivo": "Dados insuficientes ou inválidos"}

    serie_precos_completa = df_mercado['Close']
    serie_volume_completa = df_mercado['Volume']

    # Salva histórico completo para o Gráfico do React
    historico_completo = serie_precos_completa.tolist()
    
    preco_atual = float(serie_precos_completa.iloc[-1])

    # 🚀 INJEÇÃO DO LIVE TICK (Tempo Real)
    if fonte == "YAHOO" and isinstance(dados_crus, dict):
        try:
            meta = dados_crus.get('chart', {}).get('result', [{}])[0].get('meta', {})
            preco_real_time = meta.get('regularMarketPrice')
            
            if preco_real_time and preco_real_time > 0:
                preco_atual = float(preco_real_time)
                serie_precos_completa.iloc[-1] = preco_atual 
        except Exception:
            pass

    serie_precos_50d = serie_precos_completa.tail(50)
    serie_precos_126d = serie_precos_completa.tail(126)
    
    retornos_log_126d = np.log(serie_precos_126d / serie_precos_126d.shift(1)).dropna()
    retornos_log_126d = retornos_log_126d.clip(lower=-0.20, upper=0.20)

    # 2. Detecção de Regime de Mercado (HMM) e Monitor de Crise
    trava_seguranca = False
    regime_atual = "DESCONHECIDO"
    try:
        info_regime = detectar_regime_mercado()
        if info_regime.get("sucesso"):
            trava_seguranca = info_regime.get("acionar_circuit_breaker", False)
            regime_atual = info_regime.get("regime_identificado", "DESCONHECIDO")
    except Exception as e:
        logging.error(f"⚠️ Erro no HMM para {ticker}: {str(e)}")

    # 3. Ajuste Econométrico ARIMA + EGARCH
    try:
        modelo_arima = ARIMA(retornos_log_126d, order=(1, 0, 0)).fit()
        mu_diario = float(modelo_arima.forecast(steps=1).iloc[0])

        retornos_escalados = retornos_log_126d * 100
        modelo_egarch = arch_model(retornos_escalados, vol='EGARCH', p=1, o=1, q=1, dist='t', rescale=False).fit(disp='off', show_warning=False)
        
        sigma_diario = float(np.sqrt(modelo_egarch.forecast(horizon=1).variance.iloc[-1, 0])) / 100.0
        graus_liberdade = modelo_egarch.params.get('nu', 5.0)

        volatilidade_historica = float(retornos_log_126d.std())
        
        if pd.isna(sigma_diario) or sigma_diario > (volatilidade_historica * 3.0):
            sigma_diario = volatilidade_historica
            graus_liberdade = 5.0 
            
        elif graus_liberdade <= 2.1:
            return {"sucesso": False, "motivo": "Ativo radioativo: Risco de cauda incalculável"}

    except Exception:
        mu_diario = float(retornos_log_126d.mean())
        sigma_diario = float(retornos_log_126d.std())
        graus_liberdade = 5.0

    # 4. Cálculos de Risco de Cauda (Expected Shortfall / CVaR 99%)
    alpha = 0.01

    var_t = t.ppf(alpha, df=graus_liberdade)
    fator_correcao = np.sqrt((graus_liberdade - 2) / graus_liberdade)
    var_diario = mu_diario + sigma_diario * var_t * fator_correcao

    pdf_t = t.pdf(var_t, df=graus_liberdade)
    cvar_diario = mu_diario - sigma_diario * (pdf_t / alpha) * ((graus_liberdade + var_t**2) / (graus_liberdade - 1)) * fator_correcao

    if pd.isna(cvar_diario) or cvar_diario > 0:
        cvar_diario = var_diario if not pd.isna(var_diario) else -0.05

    var_diario = float(cvar_diario)

    lambda_default = 0.015
    prob_inadimplencia = 1.0 - stats.poisson.pmf(0, mu=lambda_default)
    retorno_ajustado_rf = taxa_selic_atual * (1.0 - prob_inadimplencia)

    z_score = float((preco_atual - serie_precos_50d.mean()) / serie_precos_50d.std()) if serie_precos_50d.std() > 0 else 0.0
    sigma_anual = sigma_diario * np.sqrt(252)
    mu_anual = mu_diario * 252

    # ====================================================================
    # 🌪️ CÁLCULO DE GAPS SISTÊMICOS (MERTON JUMP-DIFFUSION)
    # ====================================================================
    try:
        from montecarlo import calibrar_parametros_merton
        retornos_totais_log = np.log(serie_precos_completa / serie_precos_completa.shift(1)).dropna()
        if len(retornos_totais_log) > 50:
            _, _, freq_gaps_ano, impacto_medio_gap, _ = calibrar_parametros_merton(retornos_totais_log)
        else:
            freq_gaps_ano, impacto_medio_gap = 0.0, 0.0
    except Exception:
        freq_gaps_ano, impacto_medio_gap = 0.0, 0.0

    # ====================================================================
    # 🏢 ANÁLISE FUNDAMENTALISTA (Filtro de Ruína + Ancoragem de Valor)
    # ====================================================================
    alerta_fundamentalista = False
    motivo_alerta = ""
    ancora_de_valor = False
    multiplicador_valor = 1.0
    
    try:
        fund_str = rdb_global.get(f"fund:{ticker}")
        if fund_str:
            fund_json = json.loads(fund_str)
            dados_fin = fund_json.get('quoteSummary', {}).get('result', [{}])[0]
            financeiro = dados_fin.get('financialData', {})
            estatisticas = dados_fin.get('defaultKeyStatistics', {})
            
            divida_ebitda = financeiro.get('totalDebt', {}).get('raw', 0) / financeiro.get('ebitda', {}).get('raw', 1) if financeiro.get('ebitda', {}).get('raw', 1) != 0 else 0
            margem_liquida = financeiro.get('profitMargins', {}).get('raw', 0)
            pl_atual = financeiro.get('trailingPE', {}).get('raw', 0)
            pvp_atual = estatisticas.get('priceToBook', {}).get('raw', 0)
            
            if divida_ebitda > 3.5:
                alerta_fundamentalista = True
                motivo_alerta = f"Dívida tóxica ({divida_ebitda:.1f}x EBITDA)"
            elif margem_liquida < -0.10:
                alerta_fundamentalista = True
                motivo_alerta = f"Queima de caixa severa (Margem {margem_liquida*100:.1f}%)"
            
            if 0 < pl_atual < 10 and 0 < pvp_atual < 1.2 and not alerta_fundamentalista:
                ancora_de_valor = True
                multiplicador_valor = 1.5  
            elif pl_atual > 30 or pvp_atual > 4.0:
                multiplicador_valor = 0.5  
    except Exception:
        pass

    # ====================================================================
    # 🧠 INTEGRAÇÃO COM IA PREDITIVA (LSTM T+1)
    # ====================================================================
    previsao_lstm = None
    volume_atual = float(serie_volume_completa.iloc[-1])
    try:
        from lstm_predictor import prever_lstm_rapido
        
        # Puxa os dados macro de referência para a rede neural do Redis
        ibov_str = rdb_global.get("hist:^BVSP")
        dolar_str = rdb_global.get("hist:BRL=X")
        ibov_atual = json.loads(ibov_str)[-1] if ibov_str else 120000.0
        dolar_atual = json.loads(dolar_str)[-1] if dolar_str else 5.0
        
        res_lstm = prever_lstm_rapido(
            ticker=ticker,
            historico_precos=historico_completo,
            historico_volumes=serie_volume_completa.tolist(),
            volume_atual=volume_atual,
            ibov_atual=ibov_atual,
            dolar_atual=dolar_atual,
            selic_atual=taxa_selic_atual,
            lookback=20
        )
        if res_lstm and res_lstm.get("sucesso"):
            previsao_lstm = res_lstm
    except Exception as e:
        logging.warning(f"⚠️ LSTM Indisponível para {ticker}: {e}")

    # =================================================================
    # 🧲 CÁLCULO DE EXPECTATIVA E MICROESTRUTURA (VWAP)
    # =================================================================
    dias_tendencia = min(200, len(serie_precos_completa))
    sma_longa = serie_precos_completa.tail(dias_tendencia).mean()
    tendencia_de_alta = preco_atual > sma_longa

    dias_momentum = min(126, len(serie_precos_completa))
    preco_6_meses_atras = serie_precos_completa.iloc[-dias_momentum]
    momentum_6m = (preco_atual / preco_6_meses_atras) - 1.0

    mu_ajustado = mu_anual 
    if tendencia_de_alta:
        mu_ajustado += (abs(min(0, z_score)) * 0.05) + (momentum_6m * 0.5)
    else:
        mu_ajustado -= abs(momentum_6m * 0.8)

    if sigma_anual > 0:
        kelly_bruto = (mu_ajustado - taxa_selic_atual) / (sigma_anual ** 2)
        kelly_fracao = max(0.0, min(kelly_bruto, 1.0))
    else:
        kelly_fracao = 0.0

    df_mercado['Preco_Volume'] = df_mercado['Close'] * df_mercado['Volume']
    df_mercado['VWAP'] = df_mercado['Preco_Volume'].cumsum() / df_mercado['Volume'].cumsum()
    vwap_atual = float(df_mercado['VWAP'].iloc[-1])
    
    distancia_vwap = (preco_atual / vwap_atual) - 1.0 if vwap_atual > 0 else 0.0

    volume_mean = serie_volume_completa.tail(20).mean()
    volume_std = serie_volume_completa.tail(20).std()
    volume_zscore = float((serie_volume_completa.iloc[-1] - volume_mean) / volume_std) if volume_std > 0 else 0.0

    # ====================================================================
    # 💰 MÓDULO DE TESOURARIA MULTIMOEDA
    # ====================================================================
    if isinstance(dados_crus, dict):
        choque_liquidez = dados_crus.get('choque_liquidez_calculado_antes', 1.0) 
        # O Golang deve passar os saldos separados no JSON
        saldo_usd = float(dados_crus.get('saldo_usd', 0.0))
        saldo_brl = float(dados_crus.get('saldo_brl', 0.0))
    else:
        choque_liquidez = 1.0
        saldo_usd = 0.0
        saldo_brl = 0.0

    # Identificação da Moeda e Bolsa do Ativo
    eh_b3 = ticker.endswith(".SA") or ticker.endswith(".SAO")
    moeda_ativo = "BRL" if eh_b3 else "USD"
    saldo_disponivel = saldo_brl if eh_b3 else saldo_usd

    custo_friccao_base = 0.0003
    spread_dinamico = custo_friccao_base * choque_liquidez

    # Injetado novos parâmetros para o TIME STOP (Dinheiro Morto)
    perfis = {
        "Agressivo":   {"dt": 1/252, "dias_var": 1,   "tolerancia": 0.05, "peso_z_compra": 1.2, "peso_z_venda": 0.5, "stop_loss": -3.0, "teto_var": 0.25, "alvo_lucro": 0.03, "dias_time_stop": 3, "tolerancia_estagnacao": 0.005},
        "Conservador": {"dt": 3/12,  "dias_var": 63,  "tolerancia": 0.10, "peso_z_compra": 2.5, "peso_z_venda": 1.0, "stop_loss": -2.0, "teto_var": 0.10, "alvo_lucro": 0.06, "dias_time_stop": 40, "tolerancia_estagnacao": 0.02},
        "Moderado":    {"dt": 6/12,  "dias_var": 126, "tolerancia": 0.15, "peso_z_compra": 2.0, "peso_z_venda": 1.5, "stop_loss": -2.5, "teto_var": 0.15, "alvo_lucro": 0.12, "dias_time_stop": 60, "tolerancia_estagnacao": 0.04},
        "Arrojado":    {"dt": 12/12, "dias_var": 252, "tolerancia": 0.20, "peso_z_compra": 1.5, "peso_z_venda": 2.5, "stop_loss": -3.5, "teto_var": 0.20, "alvo_lucro": 0.20, "dias_time_stop": 90, "tolerancia_estagnacao": 0.05}
    }

    # Resultado da Predição LSTM para injetar nos profiles
    variacao_preditiva = (previsao_lstm["variacao_projetada_perc"] / 100.0) if previsao_lstm else 0.0
    sinal_lstm = previsao_lstm["sinal_rede_neural"] if previsao_lstm else "NEUTRO"

    decisoes_perfil = {}
    for nome_perfil, params in perfis.items():
        u_p = np.exp(sigma_anual * np.sqrt(params["dt"]))
        d_p = 1.0 / u_p
        prob_alta_p = (np.exp(mu_anual * params["dt"]) - d_p) / (u_p - d_p)
        prob_alta_p = max(0.0, min(1.0, prob_alta_p))

        retorno_esperado_p = (prob_alta_p * u_p + (1.0 - prob_alta_p) * d_p) - 1.0
        fator_anualizacao = 1 / params["dt"]
        premio_de_risco_p = (retorno_esperado_p * fator_anualizacao) - retorno_ajustado_rf
        var_horizonte = abs(var_diario * np.sqrt(params["dias_var"]))

        decisao = "NEUTRO"
        
        peso_z_compra = params["peso_z_compra"]
        peso_z_venda = params["peso_z_venda"]
        
        if trava_seguranca:
            peso_z_venda = params["peso_z_venda"] * 0.5
            peso_z_compra = params["peso_z_compra"] * 1.5

        gatilho_vwap = (distancia_vwap < -0.015) and (volume_zscore < -1.0)
        gatilho_capitulacao = (distancia_vwap < -0.025) and (volume_zscore > 2.0)
        tendencia_forte_alta = (z_score > 0) and (volume_zscore > 1.2) 
        
        lucro_estimado_posicao = mu_ajustado 
        meta_lucro_perfil = spread_dinamico + params["alvo_lucro"] 
        lucro_minimo_sobrevivencia = spread_dinamico * 1.20        

        # ⏳ LÓGICA DO TIME STOP (DINHEIRO MORTO)
        dias_time_stop = params["dias_time_stop"]
        if len(serie_precos_completa) > dias_time_stop:
            preco_passado = serie_precos_completa.iloc[-dias_time_stop]
            retorno_estagnacao = (preco_atual / preco_passado) - 1.0
            is_dinheiro_morto = abs(retorno_estagnacao) < params["tolerancia_estagnacao"]
        else:
            is_dinheiro_morto = False

        if nome_perfil == "Agressivo":
            # 🛒 1. COMPRA PREDITIVA OU ESTATÍSTICA
            if variacao_preditiva >= params["alvo_lucro"]:
                decisao = "COMPRA FORTE"
                logging.info(f"🧠 [PREDIÇÃO IA] {ticker} deve subir {variacao_preditiva*100:.2f}%. Entrando forte (Agressivo).")
            elif (premio_de_risco_p > (var_horizonte * params["tolerancia"])) or (z_score < -peso_z_compra) or gatilho_vwap or gatilho_capitulacao:
                decisao = "COMPRA FORTE"
                if gatilho_capitulacao:
                    kelly_fracao = min(kelly_fracao * 1.5, 1.0) 
                    
            # ==========================================
            # 🎯 2. TAKE PROFIT (O Loop Fracionado)
            # ==========================================
            elif (lucro_estimado_posicao >= meta_lucro_perfil) or ((0.0 <= z_score <= peso_z_venda) and (lucro_estimado_posicao > lucro_minimo_sobrevivencia)):
                decisao = "REALIZACAO_PARCIAL"
                logging.info(f"🎯 [SCALE-OUT] {ticker} no lucro! Sinalizando realização parcial para o Golang.")
                
            # ==========================================
            # ⏳ 3. TIME STOP (Liquidação Atômica)
            # ==========================================
            elif is_dinheiro_morto:
                decisao = "LIQUIDACAO_TOTAL"
                logging.info(f"⏳ [TIME STOP] {ticker} estagnou. Ejetando 100% da posição.")

            # ==========================================
            # 🛑 4. STOP LOSS (Liquidação Atômica)
            # ==========================================
            elif z_score < params["stop_loss"] or (sinal_lstm.startswith("VENDER") and variacao_preditiva <= -0.02):
                decisao = "LIQUIDACAO_TOTAL"
                logging.info(f"🛑 [STOP ESTRUTURAL] A tese de {ticker} falhou. Ejetando 100% da posição.")
                    
        else:
            # Demais Perfis (Conservador, Moderado, Arrojado)
            if variacao_preditiva >= params["alvo_lucro"]:
                decisao = "COMPRA FORTE"
            elif (premio_de_risco_p > (var_horizonte * params["tolerancia"])) or (z_score < -peso_z_compra) or gatilho_capitulacao or (ancora_de_valor and z_score < -0.5):
                decisao = "COMPRA FORTE"
                
            # Saídas
            elif (lucro_estimado_posicao >= meta_lucro_perfil) or (z_score > peso_z_venda) or (z_score < params["stop_loss"]):
                if tendencia_forte_alta and z_score > 0 and lucro_estimado_posicao < meta_lucro_perfil and variacao_preditiva > 0:
                    decisao = "NEUTRO" # Deixa o lucro correr, fluxo positivo.
                else:
                    decisao = "ALERTA DE VENDA"
            
            elif is_dinheiro_morto:
                decisao = "ALERTA DE VENDA"
                logging.info(f"⏳ [TIME STOP] Desfazendo posição de {ticker} no perfil {nome_perfil} por ineficiência temporal.")
            
            elif sinal_lstm.startswith("VENDER") and variacao_preditiva <= -0.05:
                decisao = "ALERTA DE VENDA"

        # ====================================================================
        # 🛡️ VETOS INSTITUCIONAIS (HARD STOPS)
        # ====================================================================
        # ⛔ NOVO: Veto de Tesouraria Multimoeda
        if saldo_disponivel <= 0 and decisao == "COMPRA FORTE":
            decisao = "NEUTRO"
            logging.info(f"💰 [VETO DE TESOURARIA] Compra de {ticker} abortada para {nome_perfil}. Saldo zerado na moeda exigida ({moeda_ativo}).")
        
        if spread_dinamico >= (params["alvo_lucro"] * 0.80) and decisao == "COMPRA FORTE":
            decisao = "NEUTRO"
            
        if alerta_fundamentalista and decisao == "COMPRA FORTE":
            decisao = "NEUTRO"
            logging.info(f"🛡️ [CRO] Compra de {ticker} VETADA. Motivo: {motivo_alerta}")

        if abs(var_diario) > params["teto_var"] and decisao == "COMPRA FORTE":
            decisao = "NEUTRO"
            logging.info(f"🛡️ [VETO DE CAUDA] Compra de {ticker} VETADA para {nome_perfil}. VaR superou {params['teto_var']*100:.0f}%.")

        if freq_gaps_ano >= 12.0 and impacto_medio_gap <= -0.04 and decisao == "COMPRA FORTE":
            decisao = "NEUTRO"

        if trava_seguranca and decisao == "COMPRA FORTE" and nome_perfil in ["Conservador", "Moderado"]:
            decisao = "ALERTA DE VENDA"

        decisoes_perfil[nome_perfil] = decisao
    
    # ====================================================================
    # ⏰ 6.5 APLICAÇÃO DO VIÉS TEMPORAL INTRADIÁRIO
    # ====================================================================
    sinal_base = decisoes_perfil.get("Agressivo", "NEUTRO")
    _, multiplicador_global = aplicar_vies_temporal_intradiario(ticker, z_score, sinal_base, 1.0)
    
    kelly_fracao = min(kelly_fracao * multiplicador_global * multiplicador_valor, 1.0)

    for perfil, sinal_atual in decisoes_perfil.items():
        novo_sinal, _ = aplicar_vies_temporal_intradiario(ticker, z_score, sinal_atual, 1.0)
        decisoes_perfil[perfil] = novo_sinal

    # =================================================================
    # 7. Empacotamento do Retorno e Gravação do Gráfico
    # =================================================================
    ticker_limpo = ticker.replace(".SA", "").replace(".SAO", "")
    sinal_mestre_db = json.dumps(decisoes_perfil)

    historico_seguro = []
    for preco in historico_completo:
        try:
            valor = float(preco)
            historico_seguro.append(round(valor, 2) if not math.isnan(valor) else 0.0)
        except:
            historico_seguro.append(0.0)

    try:
        rdb_grafico_global.set(f"historico_treino:{ticker_limpo}", json.dumps(historico_seguro))
    except Exception as e:
        logging.error(f"⚠️ Erro ao injetar gráfico no Redis para {ticker_limpo}: {e}")

    return {
        "sucesso": True,
        "ticker": ticker_limpo,
        "preco_atual": round(preco_atual, 2),
        "z_score": round(z_score, 2),
        "risco_var": round(var_diario * 100, 2),
        "distancia_vwap_perc": round(distancia_vwap * 100, 2),
        "volume_zscore": round(volume_zscore, 2), 
        "kelly_recomendado": round(float(kelly_fracao), 4),
        "sinal": sinal_mestre_db,
        "sinais_perfil": decisoes_perfil,
        "classe": "ACAO",
        "fonte": fonte,
        "historico_precos": historico_seguro 
    }