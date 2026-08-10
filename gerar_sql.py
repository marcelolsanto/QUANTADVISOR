import json
import yfinance as yf
import time

# 1. Suas posições exatas da carteira
posicoes_json = """[
  {"usuario_id": 4, "ticker": "NGRD3", "quantidade_total": 2, "preco_medio": "34.1500"},
  {"usuario_id": 4, "ticker": "CXSE3", "quantidade_total": 7, "preco_medio": "19.4400"},
  {"usuario_id": 4, "ticker": "XPLG11", "quantidade_total": 8, "preco_medio": "89.0500"},
  {"usuario_id": 3, "ticker": "HGLG11", "quantidade_total": 1, "preco_medio": "150.5000"},
  {"usuario_id": 3, "ticker": "LOGN3", "quantidade_total": 1, "preco_medio": "26.7500"},
  {"usuario_id": 3, "ticker": "NGRD3", "quantidade_total": 1, "preco_medio": "34.1500"},
  {"usuario_id": 2, "ticker": "LOGN3", "quantidade_total": 1, "preco_medio": "26.7500"},
  {"usuario_id": 1, "ticker": "BBSE3", "quantidade_total": 5, "preco_medio": "38.6300"},
  {"usuario_id": 1, "ticker": "NGRD3", "quantidade_total": 2, "preco_medio": "34.1500"},
  {"usuario_id": 5, "ticker": "HGLG11", "quantidade_total": 2, "preco_medio": "150.5000"},
  {"usuario_id": 5, "ticker": "XPLG11", "quantidade_total": 2, "preco_medio": "89.0500"},
  {"usuario_id": 4, "ticker": "BBSE3", "quantidade_total": 2, "preco_medio": "38.2700"},
  {"usuario_id": 2, "ticker": "HGLG11", "quantidade_total": 2, "preco_medio": "150.5000"},
  {"usuario_id": 5, "ticker": "ALZR11", "quantidade_total": 35, "preco_medio": "9.7900"},
  {"usuario_id": 3, "ticker": "XPLG11", "quantidade_total": 2, "preco_medio": "90.8500"},
  {"usuario_id": 3, "ticker": "KNCA11", "quantidade_total": 2, "preco_medio": "90.3800"},
  {"usuario_id": 3, "ticker": "CXSE3", "quantidade_total": 2, "preco_medio": "19.6050"},
  {"usuario_id": 3, "ticker": "ALZR11", "quantidade_total": 32, "preco_medio": "9.7743"},
  {"usuario_id": 2, "ticker": "XPLG11", "quantidade_total": 2, "preco_medio": "90.8450"},
  {"usuario_id": 2, "ticker": "KNCA11", "quantidade_total": 2, "preco_medio": "90.3900"},
  {"usuario_id": 2, "ticker": "ALZR11", "quantidade_total": 35, "preco_medio": "9.8152"},
  {"usuario_id": 1, "ticker": "BEES3", "quantidade_total": 39, "preco_medio": "8.5610"},
  {"usuario_id": 1, "ticker": "BEES4", "quantidade_total": 44, "preco_medio": "8.5722"},
  {"usuario_id": 5, "ticker": "KNRI11", "quantidade_total": 1, "preco_medio": "149.4000"},
  {"usuario_id": 5, "ticker": "CBAV3", "quantidade_total": 3, "preco_medio": "10.7700"},
  {"usuario_id": 3, "ticker": "KNRI11", "quantidade_total": 1, "preco_medio": "149.4000"},
  {"usuario_id": 3, "ticker": "CBAV3", "quantidade_total": 3, "preco_medio": "10.7700"},
  {"usuario_id": 2, "ticker": "TTEN3", "quantidade_total": 5, "preco_medio": "13.8000"},
  {"usuario_id": 1, "ticker": "CBAV3", "quantidade_total": 2, "preco_medio": "10.7700"}
]"""

# 2. Saldos em caixa extraídos do dia 26/06 (do seu banco de dados)
saldos_26_06 = {
    1: 1.51,  # Gestor
    2: 2.04,  # Marcelo
    3: 5.84,  # Arrojado
    4: 6.48,  # Moderado
    5: 3.61   # Erickson
}

posicoes = json.loads(posicoes_json)
tickers_unicos = ["ALZR11", "BBSE3", "BEES3", "BEES4", "CBAV3", "CXSE3", "HGLG11", "KNCA11", "KNRI11", "LOGN3", "NGRD3", "TTEN3", "XPLG11"]
precos_fechamento = {}

print("📡 Extraindo histórico de preços do dia 26/06/2026...\n")

for ticker in tickers_unicos:
    ticker_yf = ticker + ".SA"
    try:
        # Busca focado na janela de 26/06
        dados = yf.Ticker(ticker_yf).history(start="2026-06-26", end="2026-06-27")
        if not dados.empty:
            preco = float(dados['Close'].iloc[0])
            precos_fechamento[ticker] = preco
            print(f"✅ {ticker}: R$ {preco:.2f}")
        else:
            print(f"⚠️ {ticker}: Sem dados para 26/06. Usando Custo de Aquisição (P&L = 0 para este ativo).")
            precos_fechamento[ticker] = 0.0
    except Exception as e:
        print(f"❌ Erro {ticker}: {e}")
        precos_fechamento[ticker] = 0.0
        
    time.sleep(1.5) # Pausa anti-bloqueio

# 3. Matemática Financeira
clientes = {1: {'custo': 0, 'mtm': 0}, 2: {'custo': 0, 'mtm': 0}, 3: {'custo': 0, 'mtm': 0}, 4: {'custo': 0, 'mtm': 0}, 5: {'custo': 0, 'mtm': 0}}

for p in posicoes:
    uid = p["usuario_id"]
    qtd = float(p["quantidade_total"])
    pm = float(p["preco_medio"])
    
    # Se o YFinance falhar, assume o Preço Médio para não distorcer o patrimônio
    preco_hoje = precos_fechamento.get(p["ticker"], pm)
    if preco_hoje == 0.0:
        preco_hoje = pm
        
    clientes[uid]['custo'] += qtd * pm
    clientes[uid]['mtm'] += qtd * preco_hoje

# 4. Geração do SQL
print("\n🚀 SQL PRONTO PARA O DIA 26/06/2026:\n")
sql = "INSERT INTO historico_patrimonial (usuario_id, data_fechamento, saldo_caixa, valor_acoes, patrimonio_total, lucro_diario) VALUES\n"

linhas = []
for uid, dados in clientes.items():
    caixa = saldos_26_06.get(uid, 0.00)
    valor_acoes = dados['mtm']
    patrimonio = caixa + valor_acoes
    lucro = valor_acoes - dados['custo']
    linhas.append(f"({uid}, '2026-06-26', {caixa:.2f}, {valor_acoes:.2f}, {patrimonio:.2f}, {lucro:.2f})")

sql += ",\n".join(linhas)
sql += "\nON CONFLICT (usuario_id, data_fechamento)\nDO UPDATE SET\n    saldo_caixa = EXCLUDED.saldo_caixa,\n    valor_acoes = EXCLUDED.valor_acoes,\n    patrimonio_total = EXCLUDED.patrimonio_total,\n    lucro_diario = EXCLUDED.lucro_diario;"

print(sql)