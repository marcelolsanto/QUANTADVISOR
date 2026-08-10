import pytest
import numpy as np
from models.pairs_trading import PairsTradingAnalyzer, analisar_pairs_trading


def test_pairs_trading_neutro():
    np.random.seed(42)
    x = np.linspace(100, 150, 50) + np.random.normal(0, 0.5, 50)
    y = 1.5 * x + 10 + np.random.normal(0, 0.5, 50)

    res = analisar_pairs_trading(y, x, ticker_a="PETR4", ticker_b="PETR3")

    assert "beta_hedge_ratio" in res
    assert "z_score_atual" in res
    assert res["ticker_a"] == "PETR4"
    assert res["ticker_b"] == "PETR3"
    assert abs(res["beta_hedge_ratio"] - 1.5) < 0.2
    assert res["sinal"] in ["NEUTRO", "SHORT_SPREAD", "LONG_SPREAD"]


def test_pairs_trading_sinal_short():
    np.random.seed(42)
    x = np.linspace(100, 150, 50)
    y = 2.0 * x + 5.0
    # Força uma distorção z-score alta no último elemento (+10.0 acima da média)
    y[-1] += 25.0

    analyzer = PairsTradingAnalyzer(threshold_z=2.0)
    res = analyzer.analisar(y, x, ticker_a="VALE3", ticker_b="GGBR4")

    assert res["sinal"] == "SHORT_SPREAD"
    assert res["acao_ticker_a"] == "VENDA"
    assert res["acao_ticker_b"] == "COMPRA"
    assert res["z_score_atual"] > 2.0


def test_pairs_trading_sinal_long():
    np.random.seed(42)
    x = np.linspace(100, 150, 50)
    y = 2.0 * x + 5.0
    # Força uma distorção z-score muito negativa no último elemento (-25.0 abaixo da média)
    y[-1] -= 25.0

    analyzer = PairsTradingAnalyzer(threshold_z=2.0)
    res = analyzer.analisar(y, x, ticker_a="ITUB4", ticker_b="BBDC4")

    assert res["sinal"] == "LONG_SPREAD"
    assert res["acao_ticker_a"] == "COMPRA"
    assert res["acao_ticker_b"] == "VENDA"
    assert res["z_score_atual"] < -2.0
