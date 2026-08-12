import asyncio
import aiohttp
import time
import sys

# Parâmetros do teste
CONCURRENT_USERS = 1500
API_URL = "http://100.95.28.45:8080/api/auditoria"
TIMEOUT = 10 # Segundos de timeout

async def fetch(session, user_id):
    start_time = time.time()
    try:
        async with session.get(API_URL, timeout=TIMEOUT) as response:
            await response.text()
            duration = time.time() - start_time
            return (response.status, duration, None)
    except Exception as e:
        duration = time.time() - start_time
        return (None, duration, str(e))

async def run_load_test():
    print(f"🚀 Iniciando Stress Test: Simulando {CONCURRENT_USERS} aparelhos Android rodando o Polling...")
    
    # Criando conector com limite de requisições simultâneas
    connector = aiohttp.TCPConnector(limit=CONCURRENT_USERS)
    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = []
        for i in range(CONCURRENT_USERS):
            tasks.append(fetch(session, i))
        
        start_test = time.time()
        results = await asyncio.gather(*tasks)
        total_time = time.time() - start_test
        
        # Analisando Resultados
        success = 0
        errors = 0
        status_codes = {}
        error_types = {}
        durations = []
        
        for status, duration, error in results:
            durations.append(duration)
            if status == 200:
                success += 1
            else:
                errors += 1
                if status:
                    status_codes[status] = status_codes.get(status, 0) + 1
                if error:
                    error_types[error] = error_types.get(error, 0) + 1
                    
        avg_latency = sum(durations) / len(durations) if durations else 0
        max_latency = max(durations) if durations else 0
        
        print("\n📊 RESULTADOS DO STRESS TEST")
        print("====================================")
        print(f"Total de Requições: {len(results)}")
        print(f"Tempo Total do Teste: {total_time:.2f} segundos")
        print(f"Requisições bem sucedidas (HTTP 200): {success}")
        print(f"Falhas / Timeouts: {errors}")
        print(f"Latência Média: {avg_latency*1000:.2f} ms")
        print(f"Pico de Latência: {max_latency*1000:.2f} ms")
        
        if errors > 0:
            print("\n🚨 DETALHES DOS ERROS:")
            for code, count in status_codes.items():
                print(f"  HTTP {code}: {count} ocorrências")
            for err, count in error_types.items():
                print(f"  Erro: {err} ({count} ocorrências)")

if __name__ == "__main__":
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())
    asyncio.run(run_load_test())
