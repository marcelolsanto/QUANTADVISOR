import pandas as pd
import logging

def extrair_serie_padronizada(ticker: str, fonte: str, dados_crus) -> pd.DataFrame:
    """
    Camada de Normalização (Adapter):
    Digere Yahoo, Brapi e Alpha Vantage, limpa lacunas e dízimas vazias (.dropna())
    e padroniza uma matriz temporal com Preço e Volume para HFT.
    """
    fechamentos = []
    volumes = []
    
    try:
        # 1. Tratamento do formato Yahoo Finance (JSON estruturado tradicional)
        if fonte == "YAHOO":
            if not dados_crus.get('chart', {}).get('result'):
                return pd.DataFrame()
            result = dados_crus['chart']['result'][0]
            try:
                fechamentos = result['indicators']['adjclose'][0]['adjclose']
                volumes = result['indicators']['quote'][0]['volume']
            except KeyError:
                fechamentos = result['indicators']['quote'][0]['close']
                volumes = result['indicators']['quote'][0]['volume']

        # 2. Tratamento do formato BRAPI (JSON plano vindo do lote)
        elif fonte == "BRAPI":
            if 'historicalDataPrice' in dados_crus:
                for pregao in dados_crus['historicalDataPrice']:
                    fechamentos.append(float(pregao['close']))
                    volumes.append(float(pregao.get('volume', 1.0)))
            elif 'regularMarketPrice' in dados_crus:
                fechamentos = [float(dados_crus['regularMarketPrice'])]
                volumes = [float(dados_crus.get('regularMarketVolume', 1.0))]

        # 3. Tratamento do formato Alpha Vantage (JSON indexado por strings textuais)
        elif fonte == "ALPHA_VANTAGE":
            if "Time Series (Daily)" in dados_crus:
                serie_diaria = dados_crus["Time Series (Daily)"]
                for data_pregao in sorted(serie_diaria.keys()):
                    fechamentos.append(float(serie_diaria[data_pregao]["4. close"]))
                    volumes.append(float(serie_diaria[data_pregao].get("5. volume", 1.0)))

        if not fechamentos:
            return pd.DataFrame()

        # Proteção anti-quebra caso a API omita o volume em algum tick
        if not volumes or len(volumes) != len(fechamentos):
            volumes = [1.0] * len(fechamentos)

        # Montagem Vetorial Bidimensional
        df = pd.DataFrame({'Close': fechamentos, 'Volume': volumes})
        df = df.dropna()

        if len(df) < 50:
            logging.warning(f"⚠️ [NORMALIZADOR] {ticker} ignorado: Histórico insuficiente ({len(df)} dias).")
            return pd.DataFrame()

        return df

    except Exception as e:
        logging.error(f"❌ [ERRO NORMALIZADOR] Falha em {ticker} via {fonte}: {str(e)}")
        return pd.DataFrame()