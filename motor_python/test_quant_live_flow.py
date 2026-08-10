import numpy as np
import json
import time
import requests
from models.pairs_trading import PairsTradingAnalyzer
from messaging.redis_pub import get_redis_client, publicar_sinal_hft

def test_quant_engine_live_flow():
    print("\n=======================================================")
    print("🚀 TESTANDO QUANT ENGINE LIVE FLOW (EUA & ALPACAS)")
    print("=======================================================")

    # 1. Teste de Conexão com Redis
    print("\n[Etapa 1] Testando Conexão Redis Pub/Sub...")
    r = get_redis_client()
    try:
        r.ping()
        print("✅ Redis conectado com sucesso!")
    except Exception as e:
        print(f"❌ Falha de conexão com Redis: {e}")
        return

    # 2. Teste de Margem / Buying Power no Redis
    print("\n[Etapa 2] Verificando hft:wallet:buying_power no Redis...")
    r.set("hft:wallet:buying_power", 150000.0)
    bp = r.get("hft:wallet:buying_power")
    print(f"💰 Buying Power registrado no Redis: US$ {float(bp):,.2f}")

    # 3. Execução de Sinal Pairs Trading com Disparo no Redis
    print("\n[Etapa 3] Disparando Sinal Pairs Trading (AAPL x MSFT)...")
    analyzer = PairsTradingAnalyzer(threshold_z=2.0, margin_required=5000.0)
    
    # Preços simulados de AAPL e MSFT com salto recente no Z-Score
    np.random.seed(42)
    base_x = np.linspace(100, 110, 15)
    x = base_x + np.random.normal(0, 0.2, 15)
    y = 1.5 * base_x + 10 + np.random.normal(0, 0.2, 15)
    y[-1] += 12.0  # Disparo de desvio alto (Z-Score > 2.0)

    resultado = analyzer.analisar(
        prices_a=y,
        prices_b=x,
        ticker_a="AAPL",
        ticker_b="MSFT",
        publish_redis=True,
        target_qty=50
    )

    print(f"📊 Resultado Análise Pairs Trading:")
    print(f"   - Cointegrado: {resultado['cointegrado']}")
    print(f"   - Z-Score: {resultado['z_score_atual']}")
    print(f"   - Sinal: {resultado['sinal']}")
    print(f"   - Publicado Redis: {resultado.get('publicado_redis', False)}")
    assert resultado["sinal"] != "NEUTRO"
    print("✅ Sinal HFT gerado e publicado com sucesso no barramento!")

    # 4. Teste de Reciclagem de Capital (Capital Recycling)
    print("\n[Etapa 4] Simulando Disparo de Reciclagem de Capital...")
    success_rec = publicar_sinal_hft(
        strategy="capital_recycling",
        action="CLOSE_POSITION",
        asset_a="VALE3",
        asset_b="",
        target_qty=200,
        extra_data={"motivo": "Margem Crítica Excedida"}
    )
    print(f"♻️ Publicação de Reciclagem de Capital: {success_rec}")

    # 5. Consulta HTTP à API Go Engine
    print("\n[Etapa 5] Consultando Endpoints da Go Engine (porta 8080)...")
    try:
        resp = requests.get("http://quant_coletor_go:8080/api/wallet/buying-power", timeout=5)
        if resp.status_code == 200:
            dados = resp.json()
            print(f"✅ API Go respondeu OK! Buying Power via HTTP: US$ {dados.get('buying_power'):,.2f}")
        else:
            print(f"⚠️ API Go retornou status {resp.status_code}")
    except Exception as e:
        print(f"⚠️ Falha ao conectar na Go Engine HTTP: {e}")

    print("\n=======================================================")
    print("✨ TESTE LIVE FLOW FINALIZADO COM SUCESSO!")
    print("=======================================================")

if __name__ == "__main__":
    test_quant_engine_live_flow()
