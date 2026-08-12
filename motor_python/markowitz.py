import sys
import json
import os
import warnings
import numpy as np
import pandas as pd
import scipy.optimize as sco
import yfinance as yf
from sklearn.covariance import LedoitWolf
import redis

warnings.filterwarnings('ignore')

def obter_matriz_precos(tickers):
    """Baixa o histórico em lote e neutraliza o risco cambial (USD -> BRL)."""
    tickers_busca = []
    ativos_usd = []
    
    for t in tickers:
        tem_numero = any(char.isdigit() for char in t)
        if tem_numero:
            ticker_sa = f"{t}.SA" if not t.endswith('.SA') else t
            tickers_busca.append(ticker_sa)
        else:
            tickers_busca.append(t)
            ativos_usd.append(t)

    if ativos_usd:
        tickers_busca.append("USDBRL=X")

    try:
        dados = yf.download(tickers_busca, period="1y", interval="1d", progress=False, threads=False)

        if 'Close' in dados:
            df_fechamento = dados['Close']
        elif 'Adj Close' in dados:
            df_fechamento = dados['Adj Close']
        else:
            return None

        # 🚀 CORREÇÃO 3: Recupera o nome do ativo se for uma Series (1 único ativo retornado)
        if isinstance(df_fechamento, pd.Series):
            nome_coluna = tickers_busca[0] if tickers_busca else "Ativo"
            df_fechamento = df_fechamento.to_frame(name=nome_coluna)

        # 🚀 CORREÇÃO DE DEPRECIAÇÃO: Pandas ffill e dropna
        df_fechamento = df_fechamento.ffill(limit=5)
        df_fechamento.dropna(inplace=True)

        if ativos_usd and "USDBRL=X" in df_fechamento.columns:
            serie_dolar = df_fechamento["USDBRL=X"]
            for ativo in ativos_usd:
                if ativo in df_fechamento.columns:
                    df_fechamento[ativo] = df_fechamento[ativo] * serie_dolar
            
            df_fechamento.drop(columns=["USDBRL=X"], inplace=True)

        df_fechamento.columns = [col.replace('.SA', '') for col in df_fechamento.columns]

        return df_fechamento
    except Exception as e:
        print(f"Erro ao baixar e converter dados no Markowitz: {e}")
        return None

def estatisticas_portfolio(pesos, retornos_medios, matriz_covariancia, taxa_livre_risco=0.105):
    retorno_esperado = np.sum(retornos_medios * pesos) * 252
    
    # 🚀 CORREÇÃO 4: Uso do .values para garantir álgebra linear pura no Numpy
    volatilidade_esperada = np.sqrt(
        np.dot(pesos.T, np.dot(matriz_covariancia.values * 252, pesos))
    )
    
    sharpe_ratio = (retorno_esperado - taxa_livre_risco) / volatilidade_esperada if volatilidade_esperada > 0 else 0
    return np.array([retorno_esperado, volatilidade_esperada, sharpe_ratio])

def obter_candidatos_diversificacao(tickers_atuais, perfil_risco, todos_tickers_disponiveis, taxa_livre_risco=0.105):
    """
    Filtra e pontua novos candidatos à diversificação com base no perfil de risco
    e na descorrelação com a carteira atual.
    """
    if not todos_tickers_disponiveis:
        # Fallback inteligente adaptado: se tem .SA nos atuais, usa BR, senão usa US
        is_mercado_us = any('.' not in t and not t.isdigit() for t in tickers_atuais) if tickers_atuais else False
        if is_mercado_us:
            todos_tickers_disponiveis = tickers_atuais + ["AAPL", "MSFT", "GOOGL"]
        else:
            todos_tickers_disponiveis = tickers_atuais + ["PETR4", "VALE3", "ITUB4"]

    df_mercado = obter_matriz_precos(todos_tickers_disponiveis)

    if df_mercado is None or df_mercado.empty:
        return []

    retornos_diarios = np.log(df_mercado / df_mercado.shift(1)).dropna()
    retornos_anuais = retornos_diarios.mean() * 252
    vols_anuais = retornos_diarios.std() * np.sqrt(252)

    matriz_corr = retornos_diarios.corr(method='pearson')

    # Valores padrão iniciais
    limite_volatilidade = 0.99
    retorno_minimo = 0.05

    # 🚀 CORREÇÃO 1: Adicionado o perfil Agressivo que estava faltando
    if perfil_risco == "Conservador":
        limite_volatilidade = 0.20
        retorno_minimo = 0.12 
    elif perfil_risco == "Moderado":
        limite_volatilidade = 0.30
        retorno_minimo = 0.15 
    elif perfil_risco == "Arrojado":
        limite_volatilidade = 0.40
        retorno_minimo = 0.20 
    elif perfil_risco == "Agressivo":
        limite_volatilidade = 0.50 
        retorno_minimo = 0.25 

    candidatos = []
    for ticker in df_mercado.columns:
        if ticker in tickers_atuais:
            continue

        # 🛡️ FILTRA CORRELAÇÃO EXCESSIVA COM A CARTEIRA ATUAL
        if len(tickers_atuais) > 0:
            correlacoes_com_carteira = [matriz_corr.loc[ticker, t_atual] for t_atual in tickers_atuais if t_atual in matriz_corr.columns]
            if correlacoes_com_carteira:
                max_corr = max(correlacoes_com_carteira)
                if max_corr > 0.70:
                    continue

        vol = vols_anuais.get(ticker, 1.0)
        retorno = retornos_anuais.get(ticker, 0.0)

        if vol > limite_volatilidade or retorno < retorno_minimo:
            continue
            
        # 🚀 CORREÇÃO 2: Sharpe agora usa a taxa livre de risco dinâmica passada por parâmetro
        sharpe = (retorno - taxa_livre_risco) / vol if vol > 0 else 0
        candidatos.append((ticker, sharpe, retorno))

    candidatos.sort(key=lambda x: (x[1], x[2]), reverse=True)

    # Se a peneira foi forte demais e sobrou pouco, pega os mais seguros por volatilidade
    if len(candidatos) < 2:
        candidatos_seguros = []
        for ticker in df_mercado.columns:
            if ticker not in tickers_atuais:
                candidatos_seguros.append((ticker, vols_anuais.get(ticker, 1.0)))
        candidatos_seguros.sort(key=lambda x: x[1])
        return [c[0] for c in candidatos_seguros[:8]]

    return [c[0] for c in candidatos[:8]]

def aplicar_black_litterman(retornos_historicos_medios, matriz_covariancia, visoes_ia):
    if not visoes_ia:
        return retornos_historicos_medios
        
    tau = 0.05
    ativos = retornos_historicos_medios.index
    
    # 🚀 CORREÇÃO 1: Isola apenas as visões de ativos que realmente estão no portfólio otimizável
    visoes_validas = {k: v for k, v in visoes_ia.items() if k in ativos}
    if not visoes_validas:
        return retornos_historicos_medios

    N = len(ativos)
    K = len(visoes_validas)
    Pi = retornos_historicos_medios.values
    P = np.zeros((K, N))
    Q = np.zeros(K)
    Omega = np.zeros((K, K))
    
    for i, (ticker, previsao) in enumerate(visoes_validas.items()):
        idx = ativos.get_loc(ticker)
        P[i, idx] = 1.0
        Q[i] = previsao
        # A incerteza da visão cresce com a volatilidade intrínseca do ativo
        Omega[i, i] = matriz_covariancia.iloc[idx, idx] * tau
            
    # Matemática bayesiana pura protegida por pseudo-inversa (Moore-Penrose) contra matrizes singulares
    inv_tau_cov = np.linalg.pinv(tau * matriz_covariancia.values)
    inv_omega = np.linalg.pinv(Omega)
    
    termo1 = np.linalg.pinv(inv_tau_cov + np.dot(np.dot(P.T, inv_omega), P))
    termo2 = np.dot(inv_tau_cov, Pi) + np.dot(np.dot(P.T, inv_omega), Q)
    
    return pd.Series(np.dot(termo1, termo2), index=ativos)

def otimizar_markowitz(tickers_atuais, valores_atuais, caixa_livre, perfil_risco, todos_tickers_disponiveis, sentimentos_ia=None):
    if sentimentos_ia is None: 
        sentimentos_ia = {}
        
    patrimonio_total = sum(valores_atuais.values()) + caixa_livre
    if patrimonio_total <= 0:
        return {"sucesso": False, "erro": "A conta não possui saldo ou ações."}
    
    novos_tickers = obter_candidatos_diversificacao(tickers_atuais, perfil_risco, todos_tickers_disponiveis)
    universo_alvo = list(set(tickers_atuais + novos_tickers))

    df_precos = obter_matriz_precos(universo_alvo)
    if df_precos is None or df_precos.shape[1] < 2:
        return {"sucesso": False, "erro": "Dados de mercado insuficientes."}

    retornos_diarios = np.log(df_precos / df_precos.shift(1)).dropna()
    retornos_medios = retornos_diarios.mean()
    vols_anuais = retornos_diarios.std() * np.sqrt(252)

    # =========================================================================
    # 🧠 INTEGRAÇÃO COM CORRELACAO.PY (FILTRO DE RISCO SISTÊMICO E HEDGE)
    # =========================================================================
    try:
        from correlacao import analisar_correlacao
        
        # O Robô chama o correlacao.py para diagnosticar a lista de ativos alvo
        dados_correlacao = analisar_correlacao(universo_alvo)
        
        if dados_correlacao and dados_correlacao.get("sucesso"):
            alertas = dados_correlacao.get("alertas_concentracao", [])
            hedges = dados_correlacao.get("oportunidades_hedge", [])
            
            # 1. PENALIZAÇÃO DE CONCENTRAÇÃO: Se a correlação for > 0.75, corta a expectativa de lucro em 15%
            for alerta in alertas:
                a1, a2, corr = alerta['ativo1'], alerta['ativo2'], alerta['correlacao']
                if corr > 0.75:
                    if a1 in retornos_medios: retornos_medios[a1] *= 0.85
                    if a2 in retornos_medios: retornos_medios[a2] *= 0.85
            
            # 2. PRÊMIO DE HEDGE: Se a correlação for < 0.20 (proteção), sobe a expectativa de lucro em 15%
            for hedge in hedges:
                a1, a2, corr = hedge['ativo1'], hedge['ativo2'], hedge['correlacao']
                if corr < 0.20:
                    if a1 in retornos_medios: retornos_medios[a1] *= 1.15
                    if a2 in retornos_medios: retornos_medios[a2] *= 1.15
                    
            print("🛡️ [MARKOWITZ] correlacao.py injetado com sucesso! Pesos ajustados por Risco Sistêmico e Hedge.")
    except Exception as e:
        print(f"⚠️ Erro ao integrar correlação no Markowitz: {e}")
    # =========================================================================

    lw = LedoitWolf()
    matriz_encolhida = lw.fit(retornos_diarios).covariance_
    matriz_covariancia = pd.DataFrame(
        matriz_encolhida, index=df_precos.columns, columns=df_precos.columns)

    try:
        visoes_quantitativas = {}
        fator_confianca_nlp = 0.15 
        
        for ticker in df_precos.columns:
            score_nlp = sentimentos_ia.get(ticker, 0.0) 
            
            if abs(score_nlp) > 0.15: 
                volatilidade_ativo = vols_anuais.get(ticker, 0.25)
                visoes_quantitativas[ticker] = (score_nlp * volatilidade_ativo) * fator_confianca_nlp
                
        retornos_medios = aplicar_black_litterman(
            retornos_medios, matriz_covariancia, visoes_quantitativas)
    except Exception as e:
        print(f"⚠️ Erro ao aplicar Visões Bayesianas: {e}")

    num_ativos = len(df_precos.columns)
    pesos_atuais = np.array(
        [valores_atuais.get(t, 0) / patrimonio_total for t in df_precos.columns])
    stats_atuais = estatisticas_portfolio(
        pesos_atuais, retornos_medios, matriz_covariancia)

    def min_sharpe_ratio(pesos):
        return -estatisticas_portfolio(pesos, retornos_medios, matriz_covariancia)[2]

    restricoes = ({'type': 'eq', 'fun': lambda x: np.sum(x) - 1})
    
    # 🚀 CORREÇÃO 2: Teto dinâmico. Se não houver ativos suficientes para somar 100% (ex: 2 ativos = 80%), o limite sobe.
    teto_alocacao = 1.0 if num_ativos < 3 else 0.40
    limites = tuple((0.0, teto_alocacao) for _ in range(num_ativos))

    resultado_otimizacao = sco.minimize(min_sharpe_ratio, num_ativos * [1./num_ativos],
                                        method='SLSQP', bounds=limites, constraints=restricoes)

    pesos_ideais = resultado_otimizacao.x
    stats_ideais = estatisticas_portfolio(
        pesos_ideais, retornos_medios, matriz_covariancia)

    alocacao_ideal = []
    ordens_rebalanceamento = []

    for i, ticker in enumerate(df_precos.columns):
        peso_ideal = pesos_ideais[i]
        valor_ideal = patrimonio_total * peso_ideal
        valor_atual = valores_atuais.get(ticker, 0)
        peso_atual = valor_atual / patrimonio_total if patrimonio_total > 0 else 0
        diferenca_valor = valor_ideal - valor_atual
        desvio_percentual = abs(peso_ideal - peso_atual)
        is_novo_bool = ticker not in tickers_atuais
        volatilidade_ativo = vols_anuais.get(ticker, 0.25)
        banda_dinamica = max(0.03, min(volatilidade_ativo * 0.25, 0.15))

        if peso_ideal > 0.01 or valor_atual > 0:
            alocacao_ideal.append({
                "ativo": ticker,
                "peso_ideal_perc": round(float(peso_ideal * 100), 2),
                "valor_ideal_brl": round(float(valor_ideal), 2),
                "is_novo": bool(is_novo_bool and peso_ideal > 0.01)
            })

        if desvio_percentual >= banda_dinamica and abs(diferenca_valor) > 50:
            tipo_ordem = "COMPRAR" if diferenca_valor > 0 else "VENDER"
            
            if tipo_ordem == "VENDER" and valor_atual == 0:
                continue

            ordens_rebalanceamento.append({
                "ativo": ticker,
                "acao": tipo_ordem,
                "valor_brl": round(float(abs(diferenca_valor)), 2),
                "is_novo": bool(is_novo_bool and tipo_ordem == "COMPRAR")
            })

    return {
        "sucesso": True,
        "metricas_atuais": {
            "retorno_anual": round(float(stats_atuais[0] * 100), 2),
            "risco_anual": round(float(stats_atuais[1] * 100), 2),
            "sharpe": round(float(stats_atuais[2]), 2)
        },
        "metricas_otimizadas": {
            "retorno_anual": round(float(stats_ideais[0] * 100), 2),
            "risco_anual": round(float(stats_ideais[1] * 100), 2),
            "sharpe": round(float(stats_ideais[2]), 2)
        },
        "alocacao_ideal": sorted(alocacao_ideal, key=lambda x: x['peso_ideal_perc'], reverse=True),
        "receita_rebalanceamento": ordens_rebalanceamento
    }