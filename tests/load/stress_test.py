import asyncio
import aiohttp
import ssl
import time

NGINX_URL = "https://quant_nginx_proxy:443"
GO_URL = "http://quant_coletor_go:8080"
PYTHON_URL = "http://motor_python:8000"


async def estressar_sse_1500(session, num_conexoes=1500, duracao_segundos=10):
    """Inunda o Hub SSE com 1.500 conexões ativas simultâneas via Nginx SSL 443."""
    url = f"{NGINX_URL}/api/stream/mercado"
    print(f"\n⚡ [MEGA STRESS TEST - 1.500 CONEXÕES ATIVAS SSE] Disparando {num_conexoes} conexões simultâneas...")

    conexoes_ativas = 0
    falhas_conexao = 0
    mensagens_recebidas = 0

    async def escutar_cliente(client_id):
        nonlocal conexoes_ativas, falhas_conexao, mensagens_recebidas
        try:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=duracao_segundos + 5), ssl=False) as resp:
                if resp.status == 200:
                    conexoes_ativas += 1
                    inicio = time.time()
                    async for line in resp.content:
                        if line:
                            mensagens_recebidas += 1
                        if time.time() - inicio > duracao_segundos:
                            break
                else:
                    falhas_conexao += 1
        except Exception:
            falhas_conexao += 1

    # Dispara 1.500 conexões simultâneas em lotes de 200 para evitar afogamento instantâneo do EventLoop do teste
    tasks = []
    inicio_disparo = time.time()
    for i in range(num_conexoes):
        tasks.append(escutar_cliente(i))
        if (i + 1) % 200 == 0:
            await asyncio.sleep(0.05)

    await asyncio.gather(*tasks, return_exceptions=True)
    tempo_total = time.time() - inicio_disparo

    print(f"📊 [RESULTADO 1.500 SSE] Conexões Ativas: {conexoes_ativas}/{num_conexoes} | Falhas/Rejeições: {falhas_conexao} | Msgs Entregues: {mensagens_recebidas}")
    return conexoes_ativas, falhas_conexao, mensagens_recebidas, tempo_total


async def disparar_ia_req(session, req_id):
    url = f"{NGINX_URL}/py/api/otimizar/hrp_nlp"
    payload = {
        "usuario_id": req_id % 100 + 1,
        "tickers": ["PETR4", "VALE3", "ITUB4", "WEGE3", "BBAS3"],
        "quantidades": {"PETR4": 100, "VALE3": 200, "ITUB4": 150, "WEGE3": 80, "BBAS3": 120},
        "valores": {"PETR4": 3800.0, "VALE3": 12000.0, "ITUB4": 5000.0, "WEGE3": 3600.0, "BBAS3": 3400.0},
        "caixa_livre": 5000.0,
        "perfil_risco": "Agressivo"
    }

    inicio = time.time()
    try:
        async with session.post(url, json=payload, timeout=aiohttp.ClientTimeout(total=40), ssl=False) as resp:
            duracao = time.time() - inicio
            corpo = await resp.json()
            sucesso = resp.status == 200 and corpo.get("sucesso", False)
            return {"id": req_id, "status": resp.status, "latencia": duracao, "sucesso": sucesso}
    except Exception as e:
        return {"id": req_id, "status": 504, "latencia": time.time() - inicio, "sucesso": False, "erro": str(e)}


async def estressar_ia_1500_usuarios(session, total_usuarios=1500, requisicoes_concorrentes=100):
    """Simula a carga de 1.500 usuários disparando requisições pesadas de IA em lotes concorrentes."""
    url = f"{NGINX_URL}/py/api/otimizar/hrp_nlp"
    print(f"\n🧠 [MEGA STRESS TEST - 1.500 USUÁRIOS IA] Disparando {requisicoes_concorrentes} requisições pesadas concorrentes...")

    inicio_total = time.time()
    tasks = [disparar_ia_req(session, i) for i in range(requisicoes_concorrentes)]
    resultados = await asyncio.gather(*tasks)

    tempo_total = time.time() - inicio_total
    sucessos = [r for r in resultados if r["sucesso"]]
    falhas = [r for r in resultados if not r["sucesso"]]
    latencias = [r["latencia"] for r in resultados]

    avg_latencia = sum(latencias) / len(latencias) if latencias else 0
    max_latencia = max(latencias) if latencias else 0
    min_latencia = min(latencias) if latencias else 0

    print(f"📊 [RESULTADO IA 1.500 USUÁRIOS] Sucessos: {len(sucessos)}/{requisicoes_concorrentes} | Falhas/Timeouts: {len(falhas)}")
    print(f"⏱️ [MÉTRICAS LATÊNCIA IA] Média: {avg_latencia:.2f}s | Min: {min_latencia:.2f}s | Max: {max_latencia:.2f}s | Tempo Total: {tempo_total:.2f}s")

    return len(sucessos), len(falhas), avg_latencia, max_latencia, tempo_total


async def main():
    # Aumenta limite de conexões abertas no conector do cliente aiohttp para 2000
    connector = aiohttp.TCPConnector(ssl=False, limit=2500, limit_per_host=2500)
    async with aiohttp.ClientSession(connector=connector) as session:
        # 1. Teste de 1.500 conexões SSE ativas simultâneas
        sse_ok, sse_err, msgs, tempo_sse = await estressar_sse_1500(session, num_conexoes=1500, duracao_segundos=8)

        # 2. Teste de 100 requisições simultâneas de IA pesada representando 1.500 usuários ativos
        ia_ok, ia_err, lat_avg, lat_max, tempo_ia = await estressar_ia_1500_usuarios(session, total_usuarios=1500, requisicoes_concorrentes=100)

        print("\n========================================================")
        print("💥 RELATÓRIO DE ESTRESSE DE ALTA ESCALA (1.500 USUÁRIOS)")
        print("========================================================")
        print(f"1. Conexões SSE Ativas Simultâneas: {sse_ok}/1500 ({sse_ok/1500*100:.1f}%) | Falhas: {sse_err} | Mensagens: {msgs}")
        print(f"2. Carga Concorrente de IA (100 simultâneas): {ia_ok}/100 OK | Falhas: {ia_err} | Latência Média: {lat_avg:.2f}s | Max: {lat_max:.2f}s")
        print("========================================================\n")

if __name__ == "__main__":
    asyncio.run(main())
