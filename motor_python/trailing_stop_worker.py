import os
import time
import json
import requests
import redis
import logging

# Configuração de Log para acompanharmos o robô no terminal
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')

# Conexões com a sua Arquitetura
BASE_URL = os.getenv("API_BASE_URL", "http://coletor_go:8080/api")
HEADERS_BOT = {"X-Internal-Bot": os.getenv("INTERNAL_BOT_SECRET", "quantadvisor_internal_master_777_!@")} # O Crachá VIP Forte para pular o JWT do Go

# Conexão com o Banco Em-Memória (Mesmo pool que as IAs usam)
rdb = redis.Redis(host='quant_redis', port=6379, db=0, decode_responses=True)

def monitorar_carteiras():
    logging.info("🛡️ [TRAILING STOP] Escudo ativado! Vigiando a custódia de todos os usuários...")
    
    while True:
        try:
            # 1. Puxa todos os usuários do sistema do Golang
            resp_usuarios = requests.get(f"{BASE_URL}/usuarios", headers=HEADERS_BOT, timeout=10)
            if resp_usuarios.status_code != 200:
                time.sleep(10)
                continue
            
            usuarios = resp_usuarios.json()
            
            for user in usuarios:
                uid = user.get('id') or user.get('usuario_id')
                if not uid:
                    continue
                
                # 2. Puxa a Carteira Consolidada (O Go já faz o Join com o Redis aqui!)
                resp_carteira = requests.get(f"{BASE_URL}/carteira?usuario_id={uid}", headers=HEADERS_BOT, timeout=10)
                if resp_carteira.status_code != 200:
                    continue
                    
                carteira = resp_carteira.json()
                posicoes = carteira.get('posicoes', [])
                
                # Opcional: Você pode buscar as configs do motor do usuário no banco aqui
                gatilho_lucro = 0.005  # 0.5% (Ativa a proteção)
                recuo_trailing = 0.003 # 0.3% (Vende se cair isso do topo)
                stop_fixo = 0.015      # 1.5% (Vende se o ativo desabar na abertura)
                
                for pos in posicoes:
                    ticker = pos['ticker']
                    qtd = pos['quantidade']
                    preco_compra = pos['preco_medio']
                    preco_atual = pos.get('preco_atual')
                    
                    if qtd <= 0 or not preco_atual:
                        continue
                        
                    # 3. Gerencia a Memória da Operação no Redis
                    chave_memoria = f"trailing:{uid}:{ticker}"
                    estado_str = rdb.get(chave_memoria)
                    
                    if estado_str:
                        estado = json.loads(estado_str)
                    else:
                        estado = {"max_price": preco_compra, "ativo": False}
                        
                    # Atualiza a Máxima Histórica
                    if preco_atual > estado['max_price']:
                        estado['max_price'] = preco_atual
                        
                    # Verifica se o Lucro já é suficiente para ligar o Stop Móvel
                    if not estado['ativo'] and preco_atual >= preco_compra * (1 + gatilho_lucro):
                        estado['ativo'] = True
                        logging.info(f"🟢 [GATILHO LIGADO] {ticker} (User {uid}) subiu para R$ {preco_atual:.2f}. Protegendo lucros!")
                        
                    # 4. Avalia as Regras de Execução (Venda)
                    vender = False
                    motivo = ""
                    
                    if estado['ativo']:
                        # Se já estava ganhando, a linha de corte acompanha a máxima
                        preco_corte = estado['max_price'] * (1 - recuo_trailing)
                        if preco_atual <= preco_corte:
                            vender = True
                            motivo = f"Trailing Stop acionado! Recuou de R$ {estado['max_price']:.2f} para R$ {preco_atual:.2f}"
                    else:
                        # Se nunca deu lucro, o limite é o Stop de Emergência fixo
                        preco_corte = preco_compra * (1 - stop_fixo)
                        if preco_atual <= preco_corte:
                            vender = True
                            motivo = f"Stop Loss Estrutural! Caiu para R$ {preco_atual:.2f}"
                            
                    # 5. Roteamento de Ordem Atômica para o Golang
                    if vender:
                        logging.warning(f"🚨 [VENDA DEFENSIVA] {ticker} (User {uid}) -> {motivo}")
                        payload_venda = {
                            "usuario_id": uid,
                            "ticker": ticker,
                            "tipo_ordem": "VENDA",
                            "quantidade": qtd,
                            "preco": preco_atual
                        }
                        
                        resp_ordem = requests.post(f"{BASE_URL}/ordem", json=payload_venda, headers=HEADERS_BOT, timeout=10)
                        
                        if resp_ordem.status_code in [200, 201]:
                            logging.info(f"✅ Execução de {ticker} concluída com sucesso! Patrimônio salvo.")
                            rdb.delete(chave_memoria) # Apaga a memória, pois a posição foi fechada
                        else:
                            logging.error(f"❌ Falha no roteamento da Venda de {ticker}: {resp_ordem.text}")
                    else:
                        # Se não vendeu, salva a máxima e o estado de volta no Redis
                        rdb.set(chave_memoria, json.dumps(estado))
                        
        except Exception as e:
            logging.error(f"❌ Erro global no Loop de Trailing Stop: {e}")
            
        # O Robô descansa 15 segundos antes de varrer o mercado novamente (Evita sobrecarregar a CPU)
        time.sleep(15)

if __name__ == "__main__":
    monitorar_carteiras()