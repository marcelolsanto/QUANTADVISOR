import requests
import time

API_URL = "http://coletor_go:8080/api/ordem"
USUARIO_ID = 9  # O ID que aparece no seu PDF

def enviar_ordem(tipo, ticker, quantidade, preco):
    payload = {
        "usuario_id": USUARIO_ID,
        "ticker": ticker,
        "tipo_ordem": tipo,
        "quantidade": quantidade,
        "preco": preco
    }
    print(f"🤖 Tentando: {tipo} de {quantidade} {ticker} a R$ {preco:.2f}...")
    try:
        resposta = requests.post(API_URL, json=payload)
        if resposta.status_code in [200, 201]:
            print(f"   ✅ SUCESSO: {resposta.json().get('mensagem', 'Ordem executada')}")
        elif resposta.status_code == 403:
            print(f"   🛑 VETO FISCAL: {resposta.json().get('erro')}")
        else:
            print(f"   ⚠️ ERRO: {resposta.text}")
    except Exception as e:
        print(f"   ❌ Erro de conexão: {e}")
    time.sleep(1) # Pausa dramática de 1 segundo entre as ordens

print("🚀 INICIANDO SIMULAÇÃO DE ESTRESSE CONTÁBIL...\n")

# ---------------------------------------------------------
# CENÁRIO 1: ENCHENDO A CARTEIRA (Compras base)
# ---------------------------------------------------------
print("--- CENÁRIO 1: COMPRAS DE CUSTÓDIA ---")
enviar_ordem("COMPRA", "PETR4", 500, 35.00)  # Gasta R$ 17.500
enviar_ordem("COMPRA", "VALE3", 200, 60.00)  # Gasta R$ 12.000
enviar_ordem("COMPRA", "AAPL34", 100, 40.00) # Gasta R$ 4.000 (BDR)

# ---------------------------------------------------------
# CENÁRIO 2: O DAY TRADE IMPLACÁVEL (Taxa de 20%)
# ---------------------------------------------------------
print("\n--- CENÁRIO 2: FORÇANDO DAY TRADE ---")
enviar_ordem("COMPRA", "MGLU3", 1000, 4.00)  # Compra por R$ 4.000
enviar_ordem("VENDA", "MGLU3", 1000, 4.50)   # Vende por R$ 4.500 no mesmo dia (Lucro R$ 500 -> Vai gerar IR de R$ 100)

# ---------------------------------------------------------
# CENÁRIO 3: SWING TRADE ISENTO (Menos de R$ 20.000)
# ---------------------------------------------------------
print("\n--- CENÁRIO 3: TESTANDO ISENÇÃO DA RECEITA ---")
# Comprou PETR4 a R$ 35, vende agora a R$ 40
enviar_ordem("VENDA", "PETR4", 200, 40.00)   # Volume da venda: R$ 8.000 (Lucro R$ 1.000 ISENTO!)

# ---------------------------------------------------------
# CENÁRIO 4: VENDA SEM DIREITO A ISENÇÃO (BDR)
# ---------------------------------------------------------
print("\n--- CENÁRIO 4: BDR NÃO TEM ISENÇÃO ---")
# Comprou AAPL34 a R$ 40, vende a R$ 45
enviar_ordem("VENDA", "AAPL34", 100, 45.00)  # Lucro R$ 500 (Vai gerar IR de R$ 75)

# ---------------------------------------------------------
# CENÁRIO 5: O TESTE DO GUARDIÃO FISCAL 🛡️
# ---------------------------------------------------------
print("\n--- CENÁRIO 5: TENTATIVA DE ESTOURAR OS 20K ---")
# O cliente já vendeu R$ 8.000 de PETR4 neste mês.
# Se vendermos essas 200 VALE3 a R$ 75.00, o volume será R$ 15.000.
# 8.000 + 15.000 = R$ 23.000 (Estoura a isenção e vai taxar os lucros anteriores).
# O Guardião deve interceptar e dar HOLD FISCAL!
enviar_ordem("VENDA", "VALE3", 200, 75.00)

print("\n🏁 Simulação finalizada! Abra o React e exporte o PDF.")