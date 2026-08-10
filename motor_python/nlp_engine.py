import logging
import os
import warnings
from transformers import pipeline

warnings.filterwarnings("ignore")
os.environ["TOKENIZERS_PARALLELISM"] = "false"

# Inicializa o modelo Transformer em memória global (Singleton)
logging.info("🧠 [NLP] Carregando modelo FinBERT na memória (Isso pode demorar na 1ª vez)...")
try:
    # Tenta usar GPU (CUDA) se disponível, senão vai para CPU
    analisador_finbert = pipeline("text-classification", model="ProsusAI/finbert")
except Exception as e:
    logging.error(f"Erro ao carregar FinBERT: {e}")
    analisador_finbert = None

def analisar_sentimento_finbert(textos: list) -> float:
    """
    Recebe uma lista de notícias, classifica cada uma e retorna um score médio.
    +1.0 = Máxima Euforia (Bullish) | -1.0 = Máximo Pânico (Bearish) | 0.0 = Neutro
    """
    if not analisador_finbert or not textos:
        return 0.0

    try:
        # Limita para não estourar a memória (batch de no máximo 10 notícias por ativo)
        textos_curtos = [t[:250] for t in textos[:10]]
        
        resultados = analisador_finbert(textos_curtos)
        score_total = 0.0
        
        for res in resultados:
            # O FinBERT retorna labels: 'positive', 'negative', 'neutral'
            if res['label'] == 'positive':
                score_total += res['score'] # Soma a probabilidade (ex: +0.95)
            elif res['label'] == 'negative':
                score_total -= res['score'] # Subtrai a probabilidade (ex: -0.80)
                
        # Calcula a média ponderada do sentimento do ativo
        score_medio = score_total / len(textos_curtos)
        return float(score_medio)
        
    except Exception as e:
        logging.error(f"Erro na inferência NLP: {e}")
        return 0.0