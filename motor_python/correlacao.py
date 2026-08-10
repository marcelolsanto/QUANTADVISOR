import io
import base64
import json
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import seaborn as sns
import redis

def construir_matriz_redis(tickers_ativos):
    """Monta a matriz de preços lendo DIRETAMENTE da Memória RAM (Redis), sem internet."""
    try:
        rdb = redis.Redis(host='quant_redis', port=6379, db=0, decode_responses=True)
        rdb.ping()
    except:
        rdb = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)

    if not tickers_ativos:
        return None

    tickers_ativos = list(set(tickers_ativos))
    dados_dict = {}

    # O segredo do alinhamento: Cortamos exatamente os últimos 40 dias de todos os ativos
    tamanho_corte = 40 

    for t in tickers_ativos:
        # Verifica as duas chaves possíveis no Redis (com ou sem .SA)
        tem_numero = any(char.isdigit() for char in t)
        ticker_sa = f"{t}.SA" if tem_numero and not t.endswith('.SA') else t
        
        hist_str = rdb.get(f"hist:{ticker_sa}") or rdb.get(f"hist:{t}")
        
        if hist_str:
            try:
                precos = json.loads(hist_str)
                precos_validos = [float(p) for p in precos if p is not None and p > 0]
                
                if len(precos_validos) >= tamanho_corte:
                    # Captura exatamente a mesma fatia de tempo
                    dados_dict[t] = precos_validos[-tamanho_corte:]
            except:
                pass

    df = pd.DataFrame(dados_dict)
    
    if df.empty or df.shape[1] < 2:
        return None
        
    return df

def analisar_correlacao(tickers):
    """Função de álgebra linear blindada contra falhas (Crash-Free)."""
    df_precos = construir_matriz_redis(tickers)
    
    # 🛡️ BLINDAGEM MÁXIMA: Se não houver dados, geramos uma imagem vazia 
    # e devolvemos sucesso=True para não quebrar a API do FastAPI (Erro 500)
    if df_precos is None:
        plt.figure(figsize=(10, 8))
        plt.text(0.5, 0.5, 'Aguardando Ingestão de Dados na Memória...', ha='center', va='center', color='#94a3b8', fontsize=14)
        plt.gca().set_facecolor('#0f172a')
        img_buffer = io.BytesIO()
        plt.savefig(img_buffer, format='png', bbox_inches='tight', dpi=100, facecolor='#0f172a')
        plt.close()
        return {
            "sucesso": True, 
            "ativos_analisados": 0,
            "alertas_concentracao": [],
            "oportunidades_hedge": [],
            "heatmap_base64": base64.b64encode(img_buffer.getvalue()).decode('utf-8')
        }

    # Cálculo Matemático de Pearson
    retornos = np.log(df_precos / df_precos.shift(1)).dropna()
    matriz_corr = retornos.corr(method='pearson')

    # Previne NaNs de quebrarem o JSON do React
    matriz_corr = matriz_corr.fillna(0)

    # Extrai o triângulo superior da matriz
    matriz_tri_sup = matriz_corr.where(np.triu(np.ones(matriz_corr.shape), k=1).astype(bool))
    pares = matriz_tri_sup.stack().sort_values()

    # Previne erros caso a carteira seja muito pequena (ex: só 2 ativos)
    top_correlacionados = pares.tail(5) if len(pares) >= 5 else pares
    top_descorrelacionados = pares.head(5) if len(pares) >= 5 else pares

    alertas_concentracao = [{"ativo1": p[0], "ativo2": p[1], "correlacao": float(round(v, 2))} for p, v in top_correlacionados.iloc[::-1].items()]
    oportunidades_hedge = [{"ativo1": p[0], "ativo2": p[1], "correlacao": float(round(v, 2))} for p, v in top_descorrelacionados.items()]

    # Geração do Heatmap
    plt.figure(figsize=(10, 8))
    mask = np.triu(np.ones_like(matriz_corr, dtype=bool))
    sns.heatmap(
        matriz_corr, mask=mask, cmap='RdYlGn_r', center=0,
        # Se a carteira tiver poucos ativos (<=10), exibe os números dentro do quadrado
        annot=True if df_precos.shape[1] <= 10 else False, 
        fmt=".2f", linewidths=0.5, cbar_kws={"shrink": .8}
    )
    plt.title('Mapa de Calor de Risco Sistêmico (Pearson)', fontsize=14, pad=15)
    plt.xticks(rotation=45, ha='right', fontsize=9)
    plt.yticks(rotation=0, fontsize=9)
    plt.tight_layout()

    img_buffer = io.BytesIO()
    plt.savefig(img_buffer, format='png', bbox_inches='tight', dpi=100)
    plt.close()

    return {
        "sucesso": True,
        "ativos_analisados": df_precos.shape[1],
        "alertas_concentracao": alertas_concentracao,
        "oportunidades_hedge": oportunidades_hedge,
        "heatmap_base64": base64.b64encode(img_buffer.getvalue()).decode('utf-8')
    }