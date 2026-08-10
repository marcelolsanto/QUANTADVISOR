import os
import sys
import json
import pandas as pd
import numpy as np
import warnings
import yfinance as yf
import vectorbt as vbt

warnings.filterwarnings('ignore')

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PASTA_RAW = os.path.join(BASE_DIR, 'coletor_go', 'raw_data')


class QuantBacktester:
    def __init__(self, capital_inicial=100000.0):
        self.capital_inicial = capital_inicial

        # --- PARÂMETROS REAIS DE FRICÇÃO DA B3 (AÇÕES SWING TRADE) ---
        # Liquidação (0.020%) + Emolumentos (0.003%) = 0.023%
        self.taxa_b3_perna = 0.00023
        self.spread_estimado = 0.0005  # 0.05% de spread médio estimado no book de ofertas
        self.slippage_estimator = 0.0002  # 0.02% de perda por latência de rede/API
        self.imposto_swing_trade = 0.15  # 15% de alíquota sobre o lucro líquido global (Swing Trade)

    def carregar_dados(self, ticker):
        # 1. Inteligência Geográfica
        tem_numero = any(char.isdigit() for char in ticker)
        ticker_yf = f"{ticker}.SA" if tem_numero and not ticker.endswith('.SA') else ticker
        
        # 2. Download
        df = yf.download(ticker_yf, period="1y", progress=False)
        
        # 3. Blindagem contra MultiIndex (Achata as colunas se o YF inventar moda)
        if isinstance(df.columns, pd.MultiIndex):
            df.columns = df.columns.get_level_values(0)

        # Se o Yahoo falhou de verdade e devolveu vazio, avisa no log
        if df.empty:
            print(f"DEBUG BACKTEST: O Yahoo retornou uma tabela vazia para {ticker_yf}.")
            return None
            
        # 4. Blindagem de Timezone (Garante que os joins/merges vão funcionar)
        if df.index.tz is not None:
            df.index = df.index.tz_localize(None)
            
        # 5. Tratamento de Buracos de BDRs (Impede o dropna de destruir a tabela)
        df = df.ffill().dropna()
        
        # 6. Trava de segurança de dados mínimos
        if len(df) < 100:
            print(f"DEBUG BACKTEST: {ticker_yf} tem histórico muito curto ({len(df)} dias).")
            return None

        # 👉 Cria a coluna 'Preco' que a sua estratégia exige
        if 'Close' in df.columns:
            df['Preco'] = df['Close']
        elif 'Adj Close' in df.columns:
            df['Preco'] = df['Adj Close']
            
        return df

    def executar_estrategia(self, df):
        df['SMA_50'] = df['Preco'].rolling(window=50).mean()
        df['STD_50'] = df['Preco'].rolling(window=50).std()

        # Elimina o Look-ahead bias com shift(1)
        df['Z_Score'] = (df['Preco'] - df['SMA_50'].shift(1)) / df['STD_50'].shift(1)
        df = df.dropna().copy()

        # 1. GERAÇÃO DE SINAIS VETORIZADOS (Instantâneo)
        entradas = df['Z_Score'] < -2.0
        saidas = df['Z_Score'] >= 0

        # 2. MOTOR VECTORBT (Compilado em C via Numba, roda em sub-milissegundos)
        portfolio = vbt.Portfolio.from_signals(
            close=df['Preco'],
            entries=entradas,
            exits=saidas,
            init_cash=self.capital_inicial,
            fees=self.taxa_b3_perna,
            # SOMA O SPREAD E O SLIPPAGE PARA APLICAR A FRICÇÃO REAL NO PREÇO
            slippage=self.slippage_estimator + self.spread_estimado
        )

        # Extração de resultados para compatibilidade com a sua API atual
        df['Patrimonio'] = portfolio.value()

        # Converte as operações do vectorbt para a sua lista de trades brutos
        trades = []
        trades_vbt = portfolio.trades.records_readable
        
        for _, trade in trades_vbt.iterrows():
            # Salva o lucro bruto. O Kelly Criterion precisa da matemática bruta do trade.
            # O imposto será descontado de forma global no calcular_metricas.
            trades.append({'lucro_perc': trade['Return']})

        return df, trades

    def calcular_metricas(self, df, trades):
        if not trades:
            return {
                "sucesso": True,
                "win_rate": 0.0,
                "max_drawdown": 0.0,
                "retorno_liquido_real": 0.0,
                "total_trades_realizados": 0,
                "kelly_recomendado_perc": 0.0,
                "viabilidade_estrategia": "INCONCLUSIVA - O ativo não atingiu o gatilho (Z < -2.0) no último ano."
            }

        total_trades = len(trades)
        trades_vencedores = [t for t in trades if t['lucro_perc'] > 0]
        trades_perdedores = [t for t in trades if t['lucro_perc'] < 0]

        # 1. Taxas de Acerto e Erro
        win_rate_decimal = len(trades_vencedores) / total_trades
        win_rate = win_rate_decimal * 100
        loss_rate_decimal = 1.0 - win_rate_decimal

        # 2. Otimização de Posição (Critério de Kelly)
        lucro_medio = np.mean(
            [t['lucro_perc'] for t in trades_vencedores]) if trades_vencedores else 0.0001
        prejuizo_medio = abs(np.mean(
            [t['lucro_perc'] for t in trades_perdedores])) if trades_perdedores else 0.0001

        payoff = lucro_medio / prejuizo_medio

        # Fórmula de Kelly: f = p - (q / b)
        if payoff > 0 and prejuizo_medio > 0.0001:
            kelly_fraction = win_rate_decimal - (loss_rate_decimal / payoff)
        else:
            kelly_fraction = 0.0

        # "Half-Kelly" para gestão de risco segura (Limitado a 40% do capital máximo por trade)
        kelly_perc = max(0.0, min(kelly_fraction * 0.5, 0.40)) * 100

        # 3. Métricas de Performance Tradicionais e Contabilidade Tributária
        lucro_acumulado_bruto = (df['Patrimonio'].iloc[-1] / self.capital_inicial) - 1
        
        # Só paga imposto se o fechamento global da carteira der ganho de capital
        if lucro_acumulado_bruto > 0:
            lucro_acumulado_liquido = lucro_acumulado_bruto * (1 - self.imposto_swing_trade) * 100
        else:
            lucro_acumulado_liquido = lucro_acumulado_bruto * 100

        picos = df['Patrimonio'].cummax()
        drawdowns = (df['Patrimonio'] - picos) / picos
        max_drawdown = drawdowns.min() * 100

        # 4. Avaliação Visual para o React
        if lucro_acumulado_liquido > 0:
            compensabilidade = f"APROVADA - Alfa gerado. Tamanho ideal do lote (Kelly): {kelly_perc:.1f}% do saldo."
        else:
            compensabilidade = "REPROVADA - A fricção de taxas/spread ou a regra causaram prejuízo."

        return {
            "sucesso": True,
            "win_rate": round(win_rate, 2),
            "max_drawdown": round(max_drawdown, 2),
            "retorno_liquido_real": round(lucro_acumulado_liquido, 2),
            "total_trades_realizados": total_trades,
            "kelly_recomendado_perc": round(kelly_perc, 2),
            "viabilidade_estrategia": compensabilidade
        }


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"sucesso": False, "erro": "Ticker não informado"}))
        sys.exit(1)

    ticker_alvo = sys.argv[1]
    motor = QuantBacktester()
    df_dados = motor.carregar_dados(ticker_alvo)

    if df_dados is not None and not df_dados.empty:
        df_resultado, operacoes = motor.executar_estrategia(df_dados)
        resultado_json = motor.calcular_metricas(df_resultado, operacoes)
        print(json.dumps(resultado_json))
    else:
        print(json.dumps(
            {"sucesso": False, "erro": "Dados históricos indisponíveis"}))