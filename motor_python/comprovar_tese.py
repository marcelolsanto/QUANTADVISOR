import yfinance as yf
import pandas as pd
import matplotlib.pyplot as plt

def comprovar_tese_intradiaria(ticker="BOVA11.SA"):
    print(f"📡 Baixando dados de 15 min para {ticker}...")
    df = yf.download(ticker, period="60d", interval="15m")
    
    # Calcula o retorno percentual de cada candle
    df['Retorno_15m'] = df['Close'].pct_change()
    df['Hora_Minuto'] = df.index.strftime('%H:%M')
    
    # Agrupa para ver a média
    sazonalidade = df.groupby('Hora_Minuto')['Retorno_15m'].mean() * 100
    
    print("\n📊 RESULTADO NUMÉRICO (Retorno Médio % por Horário):")
    print(sazonalidade)
    
    # Gera e salva o gráfico como imagem
    plt.figure(figsize=(12, 6))
    sazonalidade.plot(kind='bar', color=['green' if x > 0 else 'red' for x in sazonalidade])
    plt.title(f"Retorno Médio Intradiario - {ticker} (Prova da Tese)")
    plt.xlabel("Horário do Pregão")
    plt.ylabel("Retorno Médio (%)")
    plt.axhline(0, color='black', linewidth=1)
    
    plt.savefig('tese_sazonalidade.png')
    print("\n📸 Gráfico salvo com sucesso! Procure pelo arquivo 'tese_sazonalidade.png' na pasta.")

comprovar_tese_intradiaria("BOVA11.SA")