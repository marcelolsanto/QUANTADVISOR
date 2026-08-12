import sys
import json
import warnings
import urllib3
import numpy as np
import pandas as pd
import yfinance as yf
import requests
from montecarlo import calibrar_parametros_merton

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def converter_taxa_anual_para_mensal(taxa_anual):
    """Fórmula de Taxa Equivalente em Juros Compostos"""
    return ((1.0 + taxa_anual) ** (1.0 / 12.0)) - 1.0

def calcular_imposto_regressivo(meses, lucro):
    """Aplica a Tabela Regressiva de IR da Renda Fixa Brasileira"""
    if lucro <= 0: return 0.0
    dias = meses * 30
    if dias <= 180: return lucro * 0.225
    elif dias <= 360: return lucro * 0.20
    elif dias <= 720: return lucro * 0.175
    else: return lucro * 0.15

def obter_taxas_renda_fixa(selic_base, ipca_projetado=0.04):
    cdi = selic_base - 0.001
    taxas_anuais = {
        "selic": selic_base,
        "pre": 0.115,
        "ipca_proj": ipca_projetado,
        "ipca": ipca_projetado + 0.06,
        "cdi": cdi,
        "cdb_110": cdi * 1.10,
        "lci_95": cdi * 0.95
    }
    
    try:
        url = "https://www.tesourodireto.com.br/json/br/com/b3/tesourodireto/bonds/titulos.json"
        headers = {"User-Agent": "Mozilla/5.0"}
        resp = requests.get(url, headers=headers, timeout=5, verify=False)
        
        if resp.status_code == 200:
            titulos = resp.json().get('response', {}).get('TrsrBdTradgList', [])
            for t in titulos:
                nome = t['TrsrBd']['nm']
                taxa = float(t['TrsrBd']['anulInvstmtRate']) / 100.0
                if "Selic" in nome and taxas_anuais["selic"] == selic_base:
                    taxas_anuais["selic"] = selic_base + taxa
                elif "Prefixado" in nome and taxas_anuais["pre"] == 0.115:
                    taxas_anuais["pre"] = taxa
                elif "IPCA" in nome and taxas_anuais["ipca"] == (ipca_projetado + 0.06):
                    taxas_anuais["ipca"] = ((1.0 + taxa) * (1.0 + ipca_projetado)) - 1.0
    except:
        pass

    return taxas_anuais

def aplicar_descontos_acoes(valor_bruto, capital_base):
    """Calcula os custos de saída (B3 e IR) para os cenários de Monte Carlo"""
    lucro = valor_bruto - capital_base
    ir = lucro * 0.15 if lucro > 0 else 0
    taxa_b3 = valor_bruto * 0.0003
    return valor_bruto - ir - taxa_b3

def simular_monte_carlo_portfolio(valores_acoes, capital_base, horizonte_anos):
    """Calcula o retorno ponderado da carteira e simula 2.000 realidades paralelas"""
    tickers = list(valores_acoes.keys())
    dias_totais = horizonte_anos * 252
    simulacoes = 2000
    caminhos_preco = np.zeros((dias_totais + 1, simulacoes))
    caminhos_preco[0] = capital_base

    if not tickers:
        return caminhos_preco 
        
    tickers_yf = [f"{t}.SA" if any(c.isdigit() for c in t) and not t.endswith('.SA') else t for t in tickers]
    
    try:
        dados = yf.download(tickers_yf, period="1y", interval="1d", progress=False, threads=False)
        df = dados['Close'] if 'Close' in dados else dados['Adj Close']
        if isinstance(df, pd.Series): df = df.to_frame()
        df.columns = [str(col).replace('.SA', '') for col in df.columns]
        
        df = df.ffill().dropna()
        retornos_ativos = np.log(df / df.shift(1)).dropna()
        
        # Cria um índice único de retorno para toda a carteira baseado nos pesos atuais
        valor_total = sum(valores_acoes.values())
        pesos = {t: v/valor_total for t, v in valores_acoes.items()}
        
        retorno_portfolio = pd.Series(0.0, index=retornos_ativos.index)
        for t in tickers:
            if t in retornos_ativos.columns:
                retorno_portfolio += retornos_ativos[t] * pesos[t]
                
        # Calibra o Merton Jump-Diffusion para a CARTEIRA INTEIRA
        if len(retorno_portfolio) > 30:
            mu_dif, sigma_dif, lamb, mu_j, sigma_j = calibrar_parametros_merton(retorno_portfolio)
            dt = 1/252
            for t in range(1, dias_totais + 1):
                Z = np.random.normal(0, 1, simulacoes)
                drift = (mu_dif * 252) - (0.5 * (sigma_dif * np.sqrt(252))**2)
                difusao = sigma_dif * np.sqrt(252) * np.sqrt(dt) * Z
                
                N = np.random.poisson(lamb * dt, simulacoes)
                J = np.zeros(simulacoes)
                idx_saltos = np.where(N > 0)[0]
                for i in idx_saltos:
                    tamanho_saltos = np.random.normal(mu_j, sigma_j, N[i])
                    J[i] = np.sum(np.exp(tamanho_saltos) - 1)
                        
                caminhos_preco[t] = caminhos_preco[t-1] * np.exp(drift * dt + difusao) * (1 + J)
            return caminhos_preco
            
    except Exception as e:
        print(f"⚠️ Erro ao simular portfólio via Monte Carlo: {e}")
        pass
        
    # Fallback caso a API do Yahoo caia: assume um crescimento linear de 12% a.a.
    taxa_diaria = (1.0 + 0.12) ** (1/252) - 1.0
    for t in range(1, dias_totais + 1):
        caminhos_preco[t] = caminhos_preco[t-1] * (1 + taxa_diaria)
    return caminhos_preco

def obter_retorno_historico_acoes(tickers):
    retornos = {}
    if not tickers: return retornos
    tickers_yf = []
    for t in tickers:
        tem_numero = any(char.isdigit() for char in t)
        if tem_numero and not t.endswith('.SA'):
            tickers_yf.append(f"{t}.SA")
        else:
            tickers_yf.append(t)
    try:
        dados = yf.download(tickers_yf, period="1y", interval="1d", progress=False, threads=False)
        if 'Close' in dados: df = dados['Close']
        elif 'Adj Close' in dados: df = dados['Adj Close']
        else: return retornos
        if isinstance(df, pd.Series): df = df.to_frame()
        df.columns = [str(col).replace('.SA', '') for col in df.columns]
        for t in tickers:
            t_limpo = t.replace('.SA', '')
            if t_limpo in df.columns:
                serie = df[t_limpo].dropna()
                if len(serie) > 10:
                    r_log = np.log(serie / serie.shift(1)).dropna()
                    retornos[t_limpo] = float(r_log.mean() * 252)
    except: pass
    return retornos

def calcular_projecao(valores_acoes, caixa_livre, taxa_selic_anual, horizonte_anos=10):
    tickers = list(valores_acoes.keys())
    valor_total_acoes = sum(valores_acoes.values())
    patrimonio_inicial = valor_total_acoes + caixa_livre

    if patrimonio_inicial == 0: 
        return {"sucesso": False, "erro": "Sua conta está zerada. Adicione saldo para simular."}

    # 1. Taxas Brutas Anuais
    retornos_hist = obter_retorno_historico_acoes(tickers)
    taxa_rv_anual = 0.12 
    if valor_total_acoes > 0 and retornos_hist:
        retorno_pond = 0.0
        for t, val in valores_acoes.items():
            retorno_pond += retornos_hist.get(t, taxa_rv_anual) * (val / valor_total_acoes)
        taxa_rv_anual = retorno_pond

    taxas_rf_anuais = obter_taxas_renda_fixa(taxa_selic_anual)
    ipca_anual = taxas_rf_anuais["ipca_proj"]

    # Converte para taxas mensais
    selic_mensal = converter_taxa_anual_para_mensal(taxas_rf_anuais["selic"])
    cdb_mensal = converter_taxa_anual_para_mensal(taxas_rf_anuais["cdb_110"])
    lci_mensal = converter_taxa_anual_para_mensal(taxas_rf_anuais["lci_95"])
    ipca_mensal = converter_taxa_anual_para_mensal(taxas_rf_anuais["ipca"])
    pre_mensal = converter_taxa_anual_para_mensal(taxas_rf_anuais["pre"])
    inflacao_mensal = converter_taxa_anual_para_mensal(ipca_anual)

    # 👇 CORREÇÃO: O Capital Base agora é o montante total investido nas ações!
    capital_base = float(valor_total_acoes)
    usou_caixa_ficticio = False
    
    if capital_base <= 0:
        # Se o cliente não tem ações (0.00), fazemos o fallback para simular o Caixa Livre dele
        if float(caixa_livre) > 0:
            capital_base = float(caixa_livre)
        else:
            # Se não tem ações e nem caixa, simula com R$ 1.000 fictícios
            capital_base = 1000.0 
            usou_caixa_ficticio = True

    # 🧠 EXECUTA O MOTOR ESTOCÁSTICO ANTES DO LOOP (A partir daqui o código segue normal...)
    caminhos_acoes = simular_monte_carlo_portfolio(valores_acoes, capital_base, horizonte_anos)
    p05 = np.percentile(caminhos_acoes, 5, axis=1)   # Cenário Caótico
    p50 = np.percentile(caminhos_acoes, 50, axis=1)  # Cenário Provável
    p95 = np.percentile(caminhos_acoes, 95, axis=1)  # Cenário Eufórico

    cap_selic = capital_base
    cap_cdb = capital_base
    cap_lci = capital_base
    cap_ipca = capital_base
    cap_pre = capital_base

    dados_mensais = []
    dados_anuais = []

    snapshot_inicial = {
        "mes_absoluto": 0, "ano": 0, "mes": 0,
        "alocacao_acoes_pessimista": round(capital_base, 2),
        "alocacao_acoes_provavel": round(capital_base, 2),
        "alocacao_acoes_otimista": round(capital_base, 2),
        "alocacao_selic": round(capital_base, 2), "alocacao_cdb": round(capital_base, 2),
        "alocacao_lci": round(capital_base, 2), "alocacao_ipca": round(capital_base, 2),
        "alocacao_pre": round(capital_base, 2),
        "alocacao_acoes_pessimista_real": round(capital_base, 2),
        "alocacao_acoes_provavel_real": round(capital_base, 2),
        "alocacao_acoes_otimista_real": round(capital_base, 2),
        "alocacao_selic_real": round(capital_base, 2), "alocacao_cdb_real": round(capital_base, 2),
        "alocacao_lci_real": round(capital_base, 2), "alocacao_ipca_real": round(capital_base, 2),
        "alocacao_pre_real": round(capital_base, 2)
    }
    dados_mensais.append(snapshot_inicial)
    dados_anuais.append(snapshot_inicial)

    meses_totais = horizonte_anos * 12
    for i in range(1, meses_totais + 1):
        cap_selic *= (1 + selic_mensal)
        cap_cdb *= (1 + cdb_mensal)
        cap_lci *= (1 + lci_mensal)
        cap_ipca *= (1 + ipca_mensal)
        cap_pre *= (1 + pre_mensal)

        # -------------------------------------------------------------
        # 🛡️ 1º CORTE: EXTRAÇÃO DO MONTE CARLO E DEDUÇÃO DE IMPOSTOS
        # -------------------------------------------------------------
        dia_idx = min(i * 21, horizonte_anos * 252) # Aproximação de 21 pregões por mês
        
        liq_acoes_pessimista = aplicar_descontos_acoes(p05[dia_idx], capital_base)
        liq_acoes_provavel = aplicar_descontos_acoes(p50[dia_idx], capital_base)
        liq_acoes_otimista = aplicar_descontos_acoes(p95[dia_idx], capital_base)

        liq_cdb = cap_cdb - calcular_imposto_regressivo(i, cap_cdb - capital_base)
        liq_lci = cap_lci

        taxa_b3_pro_rata = (0.002 / 12) * i
        liq_selic = cap_selic - calcular_imposto_regressivo(i, cap_selic - capital_base) - (cap_selic * taxa_b3_pro_rata)
        liq_pre = cap_pre - calcular_imposto_regressivo(i, cap_pre - capital_base) - (cap_pre * taxa_b3_pro_rata)
        liq_ipca = cap_ipca - calcular_imposto_regressivo(i, cap_ipca - capital_base) - (cap_ipca * taxa_b3_pro_rata)

        # -------------------------------------------------------------
        # 🛡️ 2º CORTE: EQUAÇÃO DE FISHER (PODER DE COMPRA REAL)
        # -------------------------------------------------------------
        fator_inflacao = (1.0 + inflacao_mensal) ** i
        
        real_acoes_pessimista = liq_acoes_pessimista / fator_inflacao
        real_acoes_provavel = liq_acoes_provavel / fator_inflacao
        real_acoes_otimista = liq_acoes_otimista / fator_inflacao
        
        real_selic = liq_selic / fator_inflacao
        real_cdb = liq_cdb / fator_inflacao
        real_lci = liq_lci / fator_inflacao
        real_ipca = liq_ipca / fator_inflacao
        real_pre = liq_pre / fator_inflacao

        snap = {
            "mes_absoluto": i,
            "ano": (i - 1) // 12 + 1,
            "mes": ((i - 1) % 12) + 1,
            "alocacao_acoes_pessimista": round(liq_acoes_pessimista, 2),
            "alocacao_acoes_provavel": round(liq_acoes_provavel, 2),
            "alocacao_acoes_otimista": round(liq_acoes_otimista, 2),
            "alocacao_selic": round(liq_selic, 2),
            "alocacao_cdb": round(liq_cdb, 2), 
            "alocacao_lci": round(liq_lci, 2),
            "alocacao_ipca": round(liq_ipca, 2), 
            "alocacao_pre": round(liq_pre, 2),
            "alocacao_acoes_pessimista_real": round(real_acoes_pessimista, 2),
            "alocacao_acoes_provavel_real": round(real_acoes_provavel, 2),
            "alocacao_acoes_otimista_real": round(real_acoes_otimista, 2),
            "alocacao_selic_real": round(real_selic, 2), 
            "alocacao_cdb_real": round(real_cdb, 2),
            "alocacao_lci_real": round(real_lci, 2), 
            "alocacao_ipca_real": round(real_ipca, 2),
            "alocacao_pre_real": round(real_pre, 2)
        }
        
        if i <= 12:
            dados_mensais.append(snap)
            
        if snap["mes"] == 12:
            dados_anuais.append(snap)

    # 4. Cálculo das Taxas para o Gráfico de Barras (Use o Provável para o comparativo estático)
    def calc_taxa(valor_final):
        return ((valor_final / capital_base) - 1) * 100

    comparativo_nominal = [
        {"ativo": "LCI 95%", "taxa_anual": round(calc_taxa(dados_anuais[0]["alocacao_lci"]), 2), "grupo": "Isento"},
        {"ativo": "Tesouro Selic", "taxa_anual": round(calc_taxa(dados_anuais[0]["alocacao_selic"]), 2), "grupo": "Soberano"},
        {"ativo": "CDB 110%", "taxa_anual": round(calc_taxa(dados_anuais[0]["alocacao_cdb"]), 2), "grupo": "Bancário"},
        {"ativo": "IPCA+ (Nominal)", "taxa_anual": round(calc_taxa(dados_anuais[0]["alocacao_ipca"]), 2), "grupo": "Soberano"},
        {"ativo": "Sua Carteira", "taxa_anual": round(calc_taxa(dados_anuais[0]["alocacao_acoes_provavel"]), 2), "grupo": "Misto"}
    ]
    comparativo_nominal.sort(key=lambda x: x["taxa_anual"])

    comparativo_real = [
        {"ativo": "LCI 95%", "taxa_anual": round(calc_taxa(dados_anuais[0]["alocacao_lci_real"]), 2), "grupo": "Isento"},
        {"ativo": "Tesouro Selic", "taxa_anual": round(calc_taxa(dados_anuais[0]["alocacao_selic_real"]), 2), "grupo": "Soberano"},
        {"ativo": "CDB 110%", "taxa_anual": round(calc_taxa(dados_anuais[0]["alocacao_cdb_real"]), 2), "grupo": "Bancário"},
        {"ativo": "IPCA+ (Real)", "taxa_anual": round(calc_taxa(dados_anuais[0]["alocacao_ipca_real"]), 2), "grupo": "Soberano"},
        {"ativo": "Sua Carteira", "taxa_anual": round(calc_taxa(dados_anuais[0]["alocacao_acoes_provavel_real"]), 2), "grupo": "Misto"}
    ]
    comparativo_real.sort(key=lambda x: x["taxa_anual"])

    return {
        "sucesso": True,
        "composicao_atual": {
            "caixa_livre": round(caixa_livre, 2), "acoes_custodia": round(valor_total_acoes, 2),
            "total_patrimonio": round(patrimonio_inicial, 2), "capital_simulado": round(capital_base, 2),
            "usou_caixa_ficticio": usou_caixa_ficticio
        },
        "taxas_aplicadas": {
            "acoes_ano": round(taxa_rv_anual * 100, 2) if 'taxa_rv_anual' in locals() else 12.0, 
            "selic_ano": round(taxas_rf_anuais["selic"] * 100, 2),
            "cdb_ano": round(taxas_rf_anuais["cdb_110"] * 100, 2), 
            "lci_ano": round(taxas_rf_anuais["lci_95"] * 100, 2),
            "ipca_ano": round(taxas_rf_anuais["ipca"] * 100, 2), 
            "pre_ano": round(taxas_rf_anuais["pre"] * 100, 2)
        },
        "comparativo_taxas_barras": comparativo_nominal,
        "comparativo_taxas_barras_real": comparativo_real,
        "projecao_mensal": dados_mensais,
        "projecao_anual": dados_anuais
    }

if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.stdout.write(json.dumps({"sucesso": False, "erro": "Payload ausente"}) + '\n')
        sys.exit(1)
    try:
        dados_input = json.loads(sys.argv[1])
        valores_acoes = dados_input.get("valores_acoes", {})
        caixa_livre = dados_input.get("caixa_livre", 0.0)
        taxa_selic = dados_input.get("taxa_selic", 0.1050)

        resultado = calcular_projecao(valores_acoes, caixa_livre, taxa_selic)
        sys.stdout.write(json.dumps(resultado) + '\n')
    except Exception as e:
        sys.stdout.write(json.dumps({"sucesso": False, "erro": str(e)}) + '\n')