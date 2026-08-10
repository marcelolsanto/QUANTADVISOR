import os
import time
import requests

# Boas práticas: Pegar do ambiente (com fallback para desenvolvimento)
USUARIO_ID = int(os.getenv("USUARIO_ID", 1))
BASE_URL = os.getenv("API_BASE_URL", "http://coletor_go:8080/api")

# 🤖 Crachá VIP do Robô com Secret Forte
HEADERS_BOT = {"X-Internal-Bot": os.getenv("INTERNAL_BOT_SECRET", "quantadvisor_internal_master_777_!@")}

def limpar_carrinho_seguro(id_item, ticker):
    """Garante que a ordem saia da fila. Se falhar, avisa para evitar duplicidade."""
    try:
        resp = requests.post(f"{BASE_URL}/carrinho/limpar", json={"ids": [id_item]}, headers=HEADERS_BOT, timeout=10)
        if resp.status_code not in [200, 201]:
            print(f"  ⚠️ ALERTA: Ordem de {ticker} executada, mas falha ao limpar do carrinho (Status {resp.status_code})!")
    except Exception as e:
        print(f"  🔥 ERRO CRÍTICO: Não foi possível limpar {ticker} do carrinho: {e}")

def executar_pregao():
    print("🌅 [ROBÔ EXECUTOR] O Pregão abriu! Iniciando roteamento de ordens...")

    try:
        resp = requests.get(f"{BASE_URL}/carrinho?usuario_id={USUARIO_ID}", headers=HEADERS_BOT, timeout=10)
        carrinho = resp.json() if resp.status_code == 200 else []
    except Exception as e:
        print(f"❌ Erro de conexão com o orquestrador ao buscar carrinho: {e}")
        return

    if not carrinho:
        print("📭 Nenhuma tarefa pendente no carrinho hoje. Fim do expediente.")
        return

    vendas = [item for item in carrinho if item['tipo_ordem'] == 'VENDA']
    compras = [item for item in carrinho if item['tipo_ordem'] == 'COMPRA']

    # =======================================================
    # FASE 1: VENDAS (Gerando Liquidez)
    # =======================================================
    if vendas:
        print(f"\n📉 FASE 1: DESOVANDO ATIVOS (Executando {len(vendas)} Vendas)...")
        for venda in vendas:
            payload = {"usuario_id": USUARIO_ID, "ticker": venda['ticker'], "tipo_ordem": "VENDA", "quantidade": venda['quantidade'], "preco": venda['preco']}
            try:
                resp_ordem = requests.post(f"{BASE_URL}/ordem", json=payload, headers=HEADERS_BOT, timeout=10)
                
                if resp_ordem.status_code in [200, 201]:
                    print(f"  ✅ VENDIDO: {venda['quantidade']}x {venda['ticker']} a R$ {venda['preco']:.2f}")
                    limpar_carrinho_seguro(venda['id'], venda['ticker'])
                else:
                    erro_msg = resp_ordem.json().get('erro', 'Desconhecido') if resp_ordem.text else 'Timeout/Erro de Gateway'
                    print(f"  ⚠️ FALHA AO VENDER {venda['ticker']}: {erro_msg}")
            except Exception as e:
                print(f"  ❌ ERRO DE REDE AO VENDER {venda['ticker']}: {e}")
            
            time.sleep(0.5)

        # ⏳ O TEMPO DE ASSENTAMENTO (Settlement Delay)
        # Dá tempo para o motor da corretora/Go processar as vendas no book e atualizar o saldo real
        print("  ⏳ Aguardando 3 segundos para liquidação das vendas no book...")
        time.sleep(3.0)

    # =======================================================
    # INTERLÚDIO: CHECANDO CAIXA
    # =======================================================
    print("\n🏦 CHECANDO LIQUIDEZ (Caixa Livre Atualizado)...")
    try:
        resp_cart = requests.get(f"{BASE_URL}/carteira?usuario_id={USUARIO_ID}", headers=HEADERS_BOT, timeout=10)
        if resp_cart.status_code == 200:
            caixa_livre = resp_cart.json().get('saldo_brl', resp_cart.json().get('saldo_disponivel', 0.0))
        else:
            print(f"  ⚠️ Falha ao ler saldo (Status {resp_cart.status_code}). Abortando compras.")
            caixa_livre = 0.0
    except Exception as e:
        print(f"  ❌ Erro de rede ao buscar saldo: {e}. Abortando compras por segurança.")
        caixa_livre = 0.0
    
    print(f"💵 Poder de fogo disponível para compras: R$ {caixa_livre:.2f}")

    # =======================================================
    # FASE 2: COMPRAS
    # =======================================================
    if compras and caixa_livre > 0:
        print(f"\n📈 FASE 2: AQUISIÇÃO DE ATIVOS (Processando {len(compras)} Compras)...")
        for compra in compras:
            custo_estimado = compra['quantidade'] * compra['preco']
            
            # Tolerância de 1 centavo para evitar recusas por float math invisível
            if custo_estimado <= (caixa_livre + 0.01): 
                payload = {"usuario_id": USUARIO_ID, "ticker": compra['ticker'], "tipo_ordem": "COMPRA", "quantidade": compra['quantidade'], "preco": compra['preco']}
                
                try:
                    resp_ordem = requests.post(f"{BASE_URL}/ordem", json=payload, headers=HEADERS_BOT, timeout=10)
                    
                    if resp_ordem.status_code in [200, 201]:
                        print(f"  ✅ COMPRADO: {compra['quantidade']}x {compra['ticker']} | Custo: R$ {custo_estimado:.2f}")
                        caixa_livre -= custo_estimado 
                        limpar_carrinho_seguro(compra['id'], compra['ticker'])
                    else:
                        erro_msg = resp_ordem.json().get('erro', 'Desconhecido') if resp_ordem.text else 'Timeout/Erro de Gateway'
                        print(f"  ⚠️ FALHA AO COMPRAR {compra['ticker']}: {erro_msg}")
                except Exception as e:
                    print(f"  ❌ ERRO DE REDE AO COMPRAR {compra['ticker']}: {e}")
            else:
                print(f"  ❌ SALDO INSUFICIENTE para {compra['ticker']}. (Custo: R$ {custo_estimado:.2f} | Caixa atual: R$ {caixa_livre:.2f})")
            
            time.sleep(0.5)

    print("\n🏁 [ROBÔ EXECUTOR] Rotina diária finalizada!")

if __name__ == "__main__":
    executar_pregao()