import logging
from datetime import datetime, time
import pytz
from typing import Tuple

def aplicar_vies_temporal_intradiario(ticker: str, z_score: float, sinal_atual: str, kelly_base: float = 1.0) -> Tuple[str, float]:
    """
    Aplica viés temporal (Time-of-Day Anomaly) Multi-Jurisdição.
    Identifica se o ativo é da B3 (Brasil) ou de Wall Street (EUA) e aplica
    as janelas de ineficiência no fuso horário nativo correto, imune ao Horário de Verão.
    """
    novo_sinal = sinal_atual
    multiplicador_kelly = kelly_base

    # =====================================================================
    # 🌍 1. DETECÇÃO DE JURISDIÇÃO E FUSO HORÁRIO NATIVO
    # =====================================================================
    # Heurística: Símbolos americanos só têm letras (ex: AAPL, QQQ). B3 tem números (PETR4) ou sufixo .SA
    is_us_stock = not any(char.isdigit() for char in ticker) and not ticker.endswith('.SA')
    
    if is_us_stock:
        # 🇺🇸 MERCADO AMERICANO (NYSE/NASDAQ) - Fuso: Eastern Time (ET)
        fuso_mercado = pytz.timezone('America/New_York')
        agora = datetime.now(fuso_mercado).time()
        
        # Horários Nativos (ET): Abertura 09:30 | Fechamento 16:00
        janela_euforia_manha = time(9, 30) <= agora <= time(10, 0)
        janela_mergulho_tarde = time(15, 0) <= agora <= time(15, 55)
        mercado_nome = "Wall Street"
    else:
        # 🇧🇷 MERCADO BRASILEIRO (B3) - Fuso: Brasília (BRT)
        fuso_mercado = pytz.timezone('America/Sao_Paulo')
        agora = datetime.now(fuso_mercado).time()
        
        # Horários Nativos (BRT): Abertura 10:00 | Fechamento 17:00
        janela_euforia_manha = time(10, 0) <= agora <= time(10, 30)
        janela_mergulho_tarde = time(15, 45) <= agora <= time(16, 50)
        mercado_nome = "B3"

    # =====================================================================
    # 📉 2. TESE DA TARDE (Mergulho Institucional / Market-On-Close)
    # =====================================================================
    if janela_mergulho_tarde and (-1.5 <= z_score <= -0.2):
        if sinal_atual in ["NEUTRO", "ALERTA DE VENDA"]:
            logging.info(f"⏰ [VIÉS TEMPORAL] {mercado_nome} - Janela de Exaustão. {ticker} (Z: {z_score:.2f}). Forçando COMPRA FORTE.")
            novo_sinal = "COMPRA FORTE"
        
        # Oportunidade assimétrica: Aumentamos a mão em 50%
        multiplicador_kelly *= 1.50

    # =====================================================================
    # 📈 3. TESE DA MANHÃ (Amateur Hour / Euforia do Varejo)
    # =====================================================================
    elif janela_euforia_manha and (z_score >= 0.2):
        if sinal_atual == "COMPRA FORTE":
            logging.info(f"⏰ [VIÉS TEMPORAL] {mercado_nome} - Euforia Matinal. Cortando compra de {ticker} (Risco de FOMO).")
            novo_sinal = "NEUTRO"
            multiplicador_kelly = 0.0  
            
        elif sinal_atual == "NEUTRO" and z_score >= 1.0:
            logging.info(f"⏰ [VIÉS TEMPORAL] {mercado_nome} - Pico Matinal de {ticker}. Forçando TAKE PROFIT.")
            novo_sinal = "ALERTA DE VENDA"
            # Aumenta a convicção da venda para realizar o lucro rápido
            multiplicador_kelly *= 1.20 

    return novo_sinal, multiplicador_kelly