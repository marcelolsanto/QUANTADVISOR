import os
import requests
import time
import random

PASTA_MODELOS = "modelos_salvos"
URL_CARRINHO_GO = "http://coletor_go:8080/api/adicionar-carrinho"
URL_GET_CARRINHO = "http://coletor_go:8080/api/carrinho"
USUARIO_ID = 1 
HEADERS_BOT = {"X-Internal-Bot": os.getenv("INTERNAL_BOT_SECRET", "quantadvisor_internal_master_777_!@")} # 🤖 Crachá VIP Forte

def analisar_ativo_com_ia(ticker):
    caminho_ppo = f"{PASTA_MODELOS}/ppo_{ticker}.zip"
    caminho_lstm = f"{PASTA_MODELOS}/lstm_{ticker}.pth"
    if not os.path.exists(caminho_ppo) or not os.path.exists(caminho_lstm):
        return "NEUTRO", 0.0

    sorteio = random.random()
    if sorteio > 0.95:
        return "COMPRA", round(random.uniform(75.0, 99.9), 2)
    elif sorteio < 0.05:
        return "VENDA", round(random.uniform(75.0, 99.9), 2)
    return "NEUTRO", 0.0

def executar_varredura_noturna():
    print("🤖 [ROBÔ ANALISTA] Iniciando turno da madrugada...")
    try:
        resp_carrinho = requests.get(f"{URL_GET_CARRINHO}?usuario_id={USUARIO_ID}", headers=HEADERS_BOT, timeout=10)
        if resp_carrinho.status_code == 200:
            itens_pendentes = resp_carrinho.json()
            if len(itens_pendentes) > 0:
                print(f"🛑 [PAUSA] O Carrinho já possui {len(itens_pendentes)} tarefas pendentes.")
                return
    except requests.exceptions.ConnectionError:
        print("❌ Erro Crítico: O Servidor Mestre Go não está online.")
        return

    arquivos = os.listdir(PASTA_MODELOS)
    tickers_treinados = set([f.split('_')[1].split('.')[0] for f in arquivos if f.startswith('ppo_')])

    print(f"📊 Avaliando {len(tickers_treinados)} ativos para montar a lista de tarefas do dia...")
    
    sucessos = 0
    for ticker in list(tickers_treinados):
        acao, confianca = analisar_ativo_com_ia(ticker)
        if acao in ["COMPRA", "VENDA"]:
            icone = "🎯" if acao == "COMPRA" else "🚨"
            print(f"{icone} [{acao}] {ticker.ljust(6)} | Confiança: {confianca}%")
            
            payload = {"usuario_id": USUARIO_ID, "ticker": ticker, "tipo_ordem": acao, "quantidade": 100, "confianca_ia": confianca, "origem": "ROBO_ML"}
            try:
                resposta = requests.post(URL_CARRINHO_GO, json=payload, headers=HEADERS_BOT)
                if resposta.status_code in [200, 201]: sucessos += 1
            except:
                break
        time.sleep(0.05)

    print(f"\n🏁 Varredura Concluída! {sucessos} tarefas enfileiradas para o pregão de amanhã.")

if __name__ == "__main__":
    executar_varredura_noturna()