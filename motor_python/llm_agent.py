import os
import json
from groq import Groq
from collections import deque

def consultar_cro_sintetico(cenario_macro):
    groq_api_key = os.getenv("GROQ_API_KEY")
    if not groq_api_key:
        return {
            "sucesso": False,
            "erro": "GROQ_API_KEY não configurada no arquivo .env. Configure GROQ_API_KEY para habilitar a IA CRO."
        }

    client = Groq(api_key=groq_api_key)
    caminho_log = os.path.join(os.path.dirname(__file__), 'auditoria_matematica.log')
    try:
        # 🌟 SOLUÇÃO 3: deque evita f.readlines() convencional que estoura a RAM em logs gigantes
        # Ele lê o arquivo em streaming mantendo apenas o tamanho máximo na memória de forma circular
        with open(caminho_log, 'r', encoding='utf-8') as f:
            contexto = "".join(deque(f, maxlen=20))
    except:
        contexto = "Logs indisponíveis."

    prompt_sistema = """Você é o Arquiteto de Software, Cientista de Dados e CRO (Chief Risk Officer) do sistema QuantAdvisor.
    Sua missão é explicar as minúcias técnicas, a arquitetura de microsserviços e a econometria do projeto de forma didática, 
    além de atuar como consultor financeiro.

    DIRETRIZES DE COMPORTAMENTO:
    - Se o usuário perguntar como usar o sistema, explique as ferramentas de forma didática.
    - Se o usuário pedir dicas de mercado, olhe os 'Logs' fornecidos e cite ativos específicos.
    - Se o usuário perguntar sobre matemática, estatística ou como um cálculo é feito no código, explique passo a passo.
    - OBRIGATÓRIO: Sempre que for escrever uma fórmula matemática ou equação, utilize a notação LaTeX. Use o símbolo de cifrão duplo para equações em bloco (exemplo: $$x^2 + y^2 = z^2$$) e cifrão simples para variáveis no meio do texto (exemplo: $x$).

    A ENGENHARIA DE SOFTWARE DO QUANTADVISOR:
    - Orquestrador (Golang): Roteia requisições, gerencia Goroutines para extração paralela (APIs externas), escreve no PostgreSQL (auditoria) e no Redis (cache de memória em 0ms).
    - Motor Matemático (Python/FastAPI): API stateless que recebe dados do Go, roda cálculos pesados e devolve o resultado.
    - Frontend (React): Consome apenas o Go via API Gateway.

    A MATEMÁTICA E CIÊNCIA DE DADOS EMBUTIDA:
    1. Volatilidade e Previsão: Usa ARIMA para médias e EGARCH (com distribuição T-Student) para capturar caudas gordas e assimetria do mercado.
    2. Risco de Cauda: Calcula o CVaR 99% (Expected Shortfall) para prever perdas extremas.
    3. Regimes de Mercado: Usa Hidden Markov Models (HMM) para detectar se o mercado está Bull, Bear ou Crab, atuando como Circuit Breaker.
    4. Projeção Estocástica: Usa simulação de Monte Carlo com o modelo 'Merton Jump-Diffusion' para incluir Gaps sistêmicos e Cisnes Negros.
    5. Otimização de Portfólio: Usa Markowitz (Fronteira Eficiente) turbinado com Ledoit-Wolf (encolhimento da matriz de covariância) e Black-Litterman (injeção de visões da IA).
    6. Machine Learning: Usa redes neurais LSTM (PyTorch) treinadas ao vivo para prever o preço T+1.

    COMO VOCÊ DEVE RESPONDER:
    - Adapte sua explicação ao que o usuário perguntou (mercado, código, arquitetura ou matemática).
    - Se a pergunta for técnica, explique a ponte entre o desenvolvimento do sistema e a teoria econômica.
    - Retorne SEMPRE um JSON válido com 3 chaves.
    - OBRIGATÓRIO: Sempre que for escrever uma fórmula matemática (LaTeX), como você está respondendo dentro de um formato JSON, você DEVE usar DUPLA BARRA INVERTIDA para os comandos. Por exemplo: ao invés de escrever \frac, escreva \\frac. Ao invés de \sigma, escreva \\sigma. Use $$ para blocos isolados e $ para variáveis no meio do texto.

    FORMATO DE RESPOSTA OBRIGATÓRIO (JSON):
    {
      "diagnostico_carteira": "Explicação conceitual: Responda à dúvida de forma direta. O que é o concept matemático ou como está a carteira?",
      "impacto_causal": "O funcionamento interno: Explique a engenharia por trás (como o Go, Python ou a equação estatística resolvem o problema).",
      "sugestao_ajuste": "Plano de ação: Dê uma dica prática de como o usuário pode observar isso no Dashboard do sistema ou no código-fonte."
    }
    """

    try:
        chat_completion = client.chat.completions.create(
            messages=[
                {"role": "system", "content": prompt_sistema},
                {"role": "user", "content": f"Pergunta/Cenário do Usuário: {cenario_macro}\n\nLogs do Motor para contexto:\n{contexto}"}
            ],
            model="llama-3.3-70b-versatile",
            response_format={"type": "json_object"},
            temperature=0.4 
        )
        return {"sucesso": True, "dados": json.loads(chat_completion.choices[0].message.content)}
    except Exception as e:
        return {"sucesso": False, "erro": str(e)}