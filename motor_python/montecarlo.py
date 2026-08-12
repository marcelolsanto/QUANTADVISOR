import sys
import json
import os
import warnings
import numpy as np
import pandas as pd
import yfinance as yf


def calibrar_parametros_merton(retornos):
    """
    Identifica os dias 'normais' (Difusão) e os dias extremos (Saltos/Gaps)
    para calibrar os parâmetros do modelo Merton Jump-Diffusion.
    """
    mu_total = retornos.mean()
    std_total = retornos.std()

    # Consideramos um "salto" qualquer dia onde o retorno foi maior que 2.5 desvios padrões da média
    limite_salto = 2.5 * std_total

    retornos_normais = retornos[abs(retornos - mu_total) <= limite_salto]
    retornos_saltos = retornos[abs(retornos - mu_total) > limite_salto]

    # Parâmetros da Difusão (Dia a dia normal)
    mu_difusao = retornos_normais.mean() if len(retornos_normais) > 0 else mu_total
    sigma_difusao = retornos_normais.std() if len(
        retornos_normais) > 0 else std_total

    # Parâmetros de Salto (Processo de Poisson)
    # Frequência anualizada (lambda)
    frequencia_saltos_diaria = len(retornos_saltos) / len(retornos)
    lamb_anualizado = frequencia_saltos_diaria * 252

    # Média e Volatilidade do tamanho do Salto
    if len(retornos_saltos) > 0:
        mu_j = retornos_saltos.mean()
        sigma_j = retornos_saltos.std()
        if pd.isna(sigma_j):
            sigma_j = 0.05
    else:
        mu_j = 0.0
        sigma_j = 0.05
        lamb_anualizado = 0.0

    return mu_difusao, sigma_difusao, lamb_anualizado, mu_j, sigma_j


def simular_monte_carlo(ticker, dias_projecao=252, simulacoes=2000):
    # Inteligência Geográfica: Tem número = Brasil (.SA). Só letras = EUA.
    tem_numero = any(char.isdigit() for char in ticker)
    ticker_busca = f"{ticker}.SA" if tem_numero and not ticker.endswith('.SA') else ticker

    try:
        ativo_yf = yf.Ticker(ticker_busca)
        df = ativo_yf.history(period="1y", interval="1d")

        # LOG DE DEBUG: Vamos ver quantos dias de dados estamos pegando
        print(f"DEBUG: Ticker {ticker_busca} retornou {len(df)} dias de dados.")

        if df.empty or len(df) < 30: # Reduzi de 50 para 30 para dar mais margem
            return {"sucesso": False, "erro": f"Histórico insuficiente ({len(df)} dias)."}

        precos = df['Close'].dropna()
        preco_atual = float(precos.iloc[-1])
        retornos_log = np.log(precos / precos.shift(1)).dropna()

        # Verifica se há dados suficientes para o modelo Merton
        if len(retornos_log) < 10:
             return {"sucesso": False, "erro": "Dados de retorno insuficientes."}

        # 1. Calibração do Modelo
        mu_difusao, sigma_difusao, lamb, mu_j, sigma_j = calibrar_parametros_merton(retornos_log)

        dt = 1/252  # Passo de tempo diário

        caminhos_preco = np.zeros((dias_projecao, simulacoes))
        caminhos_preco[0] = preco_atual

        # O Motor Estocástico
        for t in range(1, dias_projecao):
            # Parte 1: Difusão Padrão (Movimento Browniano)
            Z = np.random.normal(0, 1, simulacoes)
            drift = (mu_difusao * 252) - \
                (0.5 * (sigma_difusao * np.sqrt(252))**2)
            difusao = sigma_difusao * np.sqrt(252) * np.sqrt(dt) * Z

            # Parte 2: O Componente de Saltos (Gaps)
            N = np.random.poisson(lamb * dt, simulacoes)

            J = np.zeros(simulacoes)
            idx_saltos = np.where(N > 0)[0]
            for i in idx_saltos:
                tamanho_saltos = np.random.normal(mu_j, sigma_j, N[i])
                J[i] = np.sum(np.exp(tamanho_saltos) - 1)

            # Atualiza o Preço
            caminhos_preco[t] = caminhos_preco[t-1] * \
                np.exp(drift * dt + difusao) * (1 + J)

        # Extração dos Cenários (Percentis) para o Gráfico
        p05 = np.percentile(caminhos_preco, 5, axis=1)
        p50 = np.percentile(caminhos_preco, 50, axis=1)
        p95 = np.percentile(caminhos_preco, 95, axis=1)

        dados_grafico = []
        for dia in range(dias_projecao):
            dados_grafico.append({
                "dia": dia + 1,
                "pessimista": round(float(p05[dia]), 2),
                "provavel": round(float(p50[dia]), 2),
                "otimista": round(float(p95[dia]), 2)
            })

        # Informações de Risco Extremo Detectadas
        frequencia_saltos = len([x for x in retornos_log if abs(
            x - retornos_log.mean()) > 2.5 * retornos_log.std()])

        return {
            "sucesso": True,
            "preco_base": preco_atual,
            "modelo_utilizado": "Merton Jump-Diffusion",
            "risco_estrutural": {
                "frequencia_gaps_ano": round(lamb, 2),
                "impacto_medio_gap": f"{round(mu_j * 100, 2)}%"
            },
            "projecao_1_ano": {
                "pessimista": dados_grafico[-1]["pessimista"],
                "provavel": dados_grafico[-1]["provavel"],
                "otimista": dados_grafico[-1]["otimista"]
            },
            "grafico": dados_grafico
        }

    except Exception as e:
        # AQUI O LOG VAI MOSTRAR O ERRO REAL
        print(f"CRITICAL ERROR MonteCarlo {ticker}: {str(e)}")
        return {"sucesso": False, "erro": f"Erro matemático: {str(e)}"}


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.stdout.write(json.dumps(
            {"sucesso": False, "erro": "Ticker não informado"}) + '\n')
        sys.exit(1)

    ticker_alvo = sys.argv[1]
    resultado = simular_monte_carlo(ticker_alvo)
    sys.stdout.write(json.dumps(resultado) + '\n')
